"""Execute generated Room schema and actual DAO queries against SQLite."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[2]
ROOM = ROOT / 'app/src/main/java/com/aqua/aqualight/data/aquarium/health/room'
SCHEMA = ROOT / 'app/schemas/com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase/3.json'


def dao_queries(filename="WaterAnalysisDao.java"):
    queries = {}
    source = (ROOM / filename).read_text()
    for match in re.finditer(r'@Query\((.*?)\)\s+[\w<>]+\s+(\w+)\(', source, re.S):
        queries[match[2]] = ''.join(json.loads(token) for token in re.findall(r'"(?:[^"\\]|\\.)*"', match[1]))
    return queries


class WaterAnalysisSqliteContractTest(unittest.TestCase):
    def setUp(self):
        self.database = sqlite3.connect(':memory:')
        self.database.execute('PRAGMA foreign_keys = ON')
        self.database.row_factory = sqlite3.Row
        schema = json.loads(SCHEMA.read_text())['database']
        for entity in schema['entities']:
            table = entity['tableName']
            self.database.execute(entity['createSql'].replace('${TABLE_NAME}', table))
            for index in entity.get('indices', []):
                self.database.execute(index['createSql'].replace('${TABLE_NAME}', table))
        self.queries = dao_queries()
        self.deletions = dao_queries("WaterDeletionDao.java")
        self.imports = dao_queries("WaterImportDao.java")

    def tearDown(self):
        self.database.close()

    def insert(self, identity, owner='owner', tank=2, observed=100, created=100, request=None):
        self.database.execute(
            'INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)',
            (owner, identity, tank, observed, created, request, b'exact raw bytes'),
        )

    def query(self, method, **parameters):
        return self.database.execute(self.queries[method], parameters)

    def insert_import(self, identity, owner='owner', transaction='restore'):
        self.database.execute('INSERT INTO water_analysis_import VALUES (?, ?, ?, ?, ?, ?)',
                              (owner, 'source-owner', identity, identity, transaction, 'evidence-hash'))

    def test_ten_thousand_tied_rows_use_bounded_keysets_without_gaps(self):
        self.database.executemany(
            'INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)',
            [('owner', identity, 2, 100, 100, None, b'raw') for identity in range(1, 10_001)],
        )
        self.insert(1, owner='foreign')
        self.insert(10_001, tank=3)
        page = self.query('firstPage', ownerUid='owner', tankId=2).fetchall()
        expected = 10_000
        while page:
            self.assertLessEqual(len(page), 50)
            for row in page:
                self.assertEqual(expected, row['analysisId'])
                expected -= 1
            last = page[-1]
            page = self.query('pageAfter', ownerUid='owner', tankId=2,
                              observedAtMillis=last['observedAtMillis'],
                              createdAtMillis=last['createdAtMillis'], analysisId=last['analysisId']).fetchall()
        self.assertEqual(0, expected)

    def test_keyset_query_uses_covering_order_index_without_temp_sort(self):
        plan = self.database.execute('EXPLAIN QUERY PLAN ' + self.queries['pageAfter'],
                                     dict(ownerUid='owner', tankId=2, observedAtMillis=100,
                                          createdAtMillis=100, analysisId=100)).fetchall()
        descriptions = [row['detail'] for row in plan]
        self.assertTrue(any('USING INDEX index_water_analysis_ownerUid_tankId' in d for d in descriptions))
        self.assertFalse(any('TEMP B-TREE' in d for d in descriptions))

    def test_backdating_ties_exact_delete_and_latest_are_owner_tank_scoped(self):
        self.insert(1)
        self.insert(2, created=101)
        self.insert(3, observed=99, created=102)
        self.insert(4, owner='foreign', observed=103)
        self.insert(5, tank=3, observed=103)
        scope = dict(ownerUid='owner', tankId=2)
        self.assertEqual([2, 1, 3], [r['analysisId'] for r in self.query('firstPage', **scope)])
        self.assertIsNone(self.query('record', **scope, analysisId=4).fetchone())
        self.assertIsNone(self.query('record', **scope, analysisId=5).fetchone())
        self.assertEqual(0, self.query('delete', ownerUid='foreign', tankId=2, analysisId=2).rowcount)
        self.assertEqual(0, self.query('delete', ownerUid='owner', tankId=3, analysisId=2).rowcount)
        self.assertEqual(1, self.query('delete', **scope, analysisId=2).rowcount)
        self.assertEqual(0, self.query('delete', **scope, analysisId=2).rowcount)
        self.assertEqual(1, self.query('latest', **scope).fetchone()['analysisId'])

    def test_event_and_request_uniqueness_preserve_independent_legacy_null_requests(self):
        self.insert(1, request='request')
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert(1)
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert(2, request='request')
        self.insert(1, owner='foreign', request='request')
        self.insert(2)
        self.insert(3)
        self.assertEqual(3, self.query('countForOwner', ownerUid='owner').fetchone()[0])

    def test_failed_checkpoint_rolls_back_inserted_batch(self):
        self.database.execute("CREATE TRIGGER fail_checkpoint BEFORE INSERT ON water_analysis_migration "
                              "BEGIN SELECT RAISE(ABORT, 'checkpoint failure'); END")
        with self.assertRaises(sqlite3.IntegrityError), self.database:
            self.insert(1)
            self.database.execute('INSERT INTO water_analysis_migration VALUES (?, ?, ?, ?, ?, ?, ?)',
                                  ('owner', 'source', 'records', 1, 1, 1, 1))
        self.assertEqual(0, self.query('countForOwner', ownerUid='owner').fetchone()[0])
        self.assertIsNone(self.query('migration', ownerUid='owner').fetchone())

    def test_request_tombstone_survives_delete_and_prevents_identity_reuse(self):
        self.insert(91, request='stable-request')
        self.database.execute('INSERT INTO water_analysis_request VALUES (?, ?, ?, ?)',
                              ('owner', 'stable-request', 91, 'payload-hash'))
        self.query('delete', ownerUid='owner', tankId=2, analysisId=91)
        request = self.query('request', ownerUid='owner', requestId='stable-request').fetchone()
        self.assertEqual(91, request['analysisId'])
        self.assertEqual(91, self.query('lastAllocatedId', ownerUid='owner').fetchone()[0])
        self.assertIsNone(self.query('request', ownerUid='foreign', requestId='stable-request').fetchone())
        with self.assertRaises(sqlite3.IntegrityError):
            self.database.execute('INSERT INTO water_analysis_request VALUES (?, ?, ?, ?)',
                                  ('owner', 'stable-request', 92, 'changed'))

    def test_migration_identity_floor_survives_deletion_of_legacy_rows_without_requests(self):
        self.database.execute('INSERT INTO water_analysis_migration VALUES (?, ?, ?, ?, ?, ?, ?)',
                              ('owner', 'source', 'records', 1, 1, 400, 3))
        self.assertEqual(400, self.query('lastAllocatedId', ownerUid='owner').fetchone()[0])
        self.assertEqual(0, self.query('lastAllocatedId', ownerUid='foreign').fetchone()[0])

    def test_remapped_same_owner_source_identity_cannot_collide_with_future_native_events(self):
        self.insert(1)
        self.database.execute('INSERT INTO water_analysis_import VALUES (?, ?, ?, ?, ?, ?)',
                              ('owner', 'owner', 10_000, 1, 'restore', 'hash'))
        self.assertEqual(10_000, self.query('lastAllocatedId', ownerUid='owner').fetchone()[0])
        self.assertEqual(0, self.query('lastAllocatedId', ownerUid='foreign').fetchone()[0])

    def test_count_and_has_older_are_owner_tank_scoped_with_strict_full_cursor(self):
        self.insert(1)
        self.insert(2)
        self.insert(3, owner='foreign')
        self.insert(4, tank=3)
        self.assertEqual(2, self.query('countForTank', ownerUid='owner', tankId=2).fetchone()[0])
        scope = dict(ownerUid='owner', tankId=2, observedAtMillis=100, createdAtMillis=100)
        self.assertEqual(1, self.query('hasOlder', **scope, analysisId=2).fetchone()[0])
        self.assertEqual(0, self.query('hasOlder', **scope, analysisId=1).fetchone()[0])

    def test_request_insert_failure_cannot_commit_a_raw_only_event(self):
        self.database.execute("CREATE TRIGGER fail_request BEFORE INSERT ON water_analysis_request "
                              "BEGIN SELECT RAISE(ABORT, 'request failure'); END")
        with self.assertRaises(sqlite3.IntegrityError), self.database:
            self.insert(1, request='request')
            self.database.execute('INSERT INTO water_analysis_request VALUES (?, ?, ?, ?)',
                                  ('owner', 'request', 1, 'hash'))
        self.assertEqual(0, self.query('countForOwner', ownerUid='owner').fetchone()[0])

    def test_ten_thousand_deletion_snapshots_page_without_touching_other_tanks(self):
        self.database.executemany('INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)',
                                  [('owner', i, 2, 100, 100, None, b'exact') for i in range(1, 10_001)])
        self.insert(10_001, tank=3)
        self.insert(1, owner='foreign')
        scope = dict(ownerUid='owner', tankId=2)
        self.database.execute(self.deletions['capture'], scope)
        self.assertEqual(10_000, self.database.execute(self.deletions['count'], scope).fetchone()[0])
        cursor, count = 0, 0
        while True:
            page = self.database.execute(self.deletions['page'], dict(**scope, afterId=cursor)).fetchall()
            if not page:
                break
            self.assertLessEqual(len(page), 50)
            self.assertEqual(list(range(cursor + 1, cursor + len(page) + 1)), [r['analysisId'] for r in page])
            count += len(page)
            cursor = page[-1]['analysisId']
        self.assertEqual(10_000, count)
        self.database.execute(self.deletions['removeEvents'], scope)
        self.assertEqual(1, self.query('countForOwner', ownerUid='owner').fetchone()[0])
        self.assertEqual(1, self.query('countForOwner', ownerUid='foreign').fetchone()[0])

    def test_stage_manifest_failure_preserves_live_history_and_rolls_back_snapshot_rows(self):
        self.insert(1)
        self.database.commit()
        self.database.execute("CREATE TRIGGER fail_stage BEFORE INSERT ON water_analysis_delete_manifest "
                              "BEGIN SELECT RAISE(ABORT, 'staging failure'); END")
        scope = dict(ownerUid='owner', tankId=2)
        with self.assertRaises(sqlite3.IntegrityError), self.database:
            self.database.execute(self.deletions['capture'], scope)
            self.database.execute('INSERT INTO water_analysis_delete_manifest VALUES (?, ?, ?, ?, ?, ?)',
                                  ('owner', 2, 'transaction', 1, 'checksum', 1))
        self.assertEqual(1, self.query('countForOwner', ownerUid='owner').fetchone()[0])
        self.assertEqual(0, self.database.execute(self.deletions['count'], scope).fetchone()[0])

    def test_version_one_upgrade_executes_actual_migration_sql_and_keeps_raw_bytes(self):
        old = sqlite3.connect(':memory:')
        try:
            v1 = json.loads(SCHEMA.with_name('1.json').read_text())['database']
            for entity in v1['entities']:
                table = entity['tableName']
                old.execute(entity['createSql'].replace('${TABLE_NAME}', table))
                for index in entity.get('indices', []):
                    old.execute(index['createSql'].replace('${TABLE_NAME}', table))
            old.execute('INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)',
                        ('owner', 1, 2, 100, 100, None, b'unchanged raw event'))
            source = (ROOM / 'WaterAnalysisDatabase.java').read_text()
            for statement in re.findall(r'database\.execSQL\((.*?)\);', source, re.S):
                sql = ''.join(json.loads(token) for token in re.findall(r'"(?:[^"\\]|\\.)*"', statement))
                old.execute(sql)
            self.assertEqual(b'unchanged raw event', old.execute('SELECT rawProto FROM water_analysis').fetchone()[0])
            latest = json.loads(SCHEMA.read_text())['database']
            for entity in latest['entities']:
                columns = old.execute('PRAGMA table_info(' + entity['tableName'] + ')').fetchall()
                self.assertEqual([(f['columnName'], f['affinity'], int(f.get('notNull', False))) for f in entity['fields']],
                                 [(r[1], r[2], r[3]) for r in columns])
        finally:
            old.close()

    def test_owner_cleanup_removes_all_owner_state_and_retains_foreign_rows(self):
        cleanup = dao_queries('WaterOwnerCleanupDao.java')
        for owner in ('owner', 'foreign'):
            self.insert(1, owner=owner, request='request')
            self.insert_import(1, owner=owner)
            self.database.execute('INSERT INTO water_analysis_request VALUES (?, ?, ?, ?)',
                                  (owner, 'request', 1, 'hash'))
            self.database.execute('INSERT INTO water_analysis_migration VALUES (?, ?, ?, ?, ?, ?, ?)',
                                  (owner, 'source', 'records', 1, 1, 1, 3))
            self.database.execute(self.deletions['capture'], dict(ownerUid=owner, tankId=2))
            self.database.execute('INSERT INTO water_analysis_delete_manifest VALUES (?, ?, ?, ?, ?, ?)',
                                  (owner, 2, 'transaction', 1, 'checksum', 1))
        self.database.commit()
        with self.database:
            for sql in cleanup.values():
                self.database.execute(sql, dict(ownerUid='owner'))
        for table in ('water_analysis', 'water_analysis_request', 'water_analysis_migration',
                      'water_analysis_delete_stage', 'water_analysis_delete_manifest', 'water_analysis_import'):
            self.assertEqual(['foreign'], [r[0] for r in self.database.execute('SELECT ownerUid FROM ' + table)])

    def test_owner_cleanup_failure_rolls_back_earlier_table_deletes(self):
        self.insert(1)
        self.insert_import(1)
        self.database.execute('INSERT INTO water_analysis_request VALUES (?, ?, ?, ?)', ('owner', 'r', 1, 'hash'))
        self.database.commit()
        self.database.execute("CREATE TRIGGER fail_owner_cleanup BEFORE DELETE ON water_analysis_request "
                              "BEGIN SELECT RAISE(ABORT, 'cleanup failure'); END")
        cleanup = dao_queries('WaterOwnerCleanupDao.java')
        with self.assertRaises(sqlite3.IntegrityError), self.database:
            for sql in cleanup.values():
                self.database.execute(sql, dict(ownerUid='owner'))
        self.assertEqual(1, self.query('countForOwner', ownerUid='owner').fetchone()[0])
        self.assertEqual(1, self.query('request', ownerUid='owner', requestId='r').fetchone()['analysisId'])
        self.assertEqual(1, self.database.execute('SELECT COUNT(*) FROM water_analysis_import').fetchone()[0])

    def test_ten_thousand_import_rows_use_indexed_bounded_transaction_pages(self):
        self.database.executemany('INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)',
                                  [('owner', i, 2, 100, 100, None, b'exact') for i in range(1, 10_001)])
        self.database.executemany('INSERT INTO water_analysis_import VALUES (?, ?, ?, ?, ?, ?)',
                                  [('owner', 'source-owner', i, i, 'restore', 'hash') for i in range(1, 10_001)])
        sql = self.imports['transactionPage']
        params = dict(ownerUid='owner', transactionId='restore', afterId=0)
        plan = [row['detail'] for row in self.database.execute('EXPLAIN QUERY PLAN ' + sql, params)]
        self.assertTrue(any('index_water_analysis_import_ownerUid_restoreTransactionId_analysisId' in r for r in plan))
        self.assertFalse(any('TEMP B-TREE' in r for r in plan))
        count = 0
        while page := self.database.execute(sql, params).fetchall():
            self.assertLessEqual(len(page), 50)
            self.assertEqual(list(range(count + 1, count + len(page) + 1)), [r['analysisId'] for r in page])
            count += len(page)
            params['afterId'] = page[-1]['analysisId']
        self.assertEqual(10_000, count)

    def test_import_rollback_cascades_exact_owner_transaction_but_preserves_request_tombstones(self):
        for owner, identity, transaction in [('owner', 1, 'a'), ('owner', 2, 'b'), ('foreign', 1, 'a')]:
            self.insert(identity, owner=owner, request=str(identity))
            self.insert_import(identity, owner=owner, transaction=transaction)
            self.database.execute('INSERT INTO water_analysis_request VALUES (?, ?, ?, ?)',
                                  (owner, str(identity), identity, 'hash'))
        self.database.execute(self.imports['rollback'], dict(ownerUid='owner', transactionId='a'))
        self.assertEqual(1, self.query('countForOwner', ownerUid='owner').fetchone()[0])
        self.assertEqual(1, self.query('countForOwner', ownerUid='foreign').fetchone()[0])
        self.assertIsNotNone(self.query('request', ownerUid='owner', requestId='1').fetchone())
        self.assertEqual([('foreign', 1), ('owner', 2)], [tuple(r) for r in self.database.execute(
            'SELECT ownerUid, analysisId FROM water_analysis_import ORDER BY ownerUid')])

    def test_import_mapping_requires_its_exact_owner_event_and_unique_source(self):
        self.insert(1)
        self.insert_import(1)
        self.insert(2)
        with self.assertRaises(sqlite3.IntegrityError):
            self.database.execute('INSERT INTO water_analysis_import VALUES (?, ?, ?, ?, ?, ?)',
                                  ('owner', 'source-owner', 1, 2, 'restore', 'hash'))
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert_import(1, owner='foreign')
        self.query('delete', ownerUid='owner', tankId=2, analysisId=1)
        self.assertEqual(0, self.database.execute('SELECT COUNT(*) FROM water_analysis_import').fetchone()[0])


if __name__ == '__main__':
    unittest.main()

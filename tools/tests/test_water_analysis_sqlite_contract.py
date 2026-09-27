"""Execute generated Room schema and actual DAO queries against SQLite."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[2]
ROOM = ROOT / 'app/src/main/java/com/aqua/aqualight/data/aquarium/health/room'
SCHEMA = ROOT / 'app/schemas/com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase/1.json'


def dao_queries():
    queries = {}
    source = (ROOM / 'WaterAnalysisDao.java').read_text()
    for match in re.finditer(r'@Query\((.*?)\)\s+[\w<>]+\s+(\w+)\(', source, re.S):
        queries[match[2]] = ''.join(json.loads(token) for token in re.findall(r'"(?:[^"\\]|\\.)*"', match[1]))
    return queries


class WaterAnalysisSqliteContractTest(unittest.TestCase):
    def setUp(self):
        self.database = sqlite3.connect(':memory:')
        self.database.row_factory = sqlite3.Row
        schema = json.loads(SCHEMA.read_text())['database']
        for entity in schema['entities']:
            table = entity['tableName']
            self.database.execute(entity['createSql'].replace('${TABLE_NAME}', table))
            for index in entity.get('indices', []):
                self.database.execute(index['createSql'].replace('${TABLE_NAME}', table))
        self.queries = dao_queries()

    def tearDown(self):
        self.database.close()

    def insert(self, identity, owner='owner', tank=2, observed=100, created=100, request=None):
        self.database.execute(
            'INSERT INTO water_analysis VALUES (?, ?, ?, ?, ?, ?, ?)',
            (owner, identity, tank, observed, created, request, b'exact raw bytes'),
        )

    def query(self, method, **parameters):
        return self.database.execute(self.queries[method], parameters)

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


if __name__ == '__main__':
    unittest.main()

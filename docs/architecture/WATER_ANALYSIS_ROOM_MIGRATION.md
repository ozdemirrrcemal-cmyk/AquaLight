# Water Analysis indexed migration staging

## Status and authority

This implementation adds Room schema v1 and a verified migration staging path.
The existing Proto store remains the application's live read/write authority.
`VERIFIED` means staging has passed verification, **not** that a live cutover has
occurred. No code here deletes, replaces or truncates the source Proto file.
Composition must switch only after the owner/session/tank gate, cancellation,
archive, rollback and recovery requirements are implemented and accepted.

## Schema and queries

`WaterAnalysisDatabase` is one process singleton, uses the application context,
and has no destructive-migration fallback or main-thread I/O opt-in.

| Table | Identity and content |
| --- | --- |
| `water_analysis` | Composite `(ownerUid, analysisId)` primary key; unique `(ownerUid, requestId)`. Legacy blank requests use SQL NULL. Owner, tank, observed/created times are indexed columns; each event retains its exact individual Proto payload including unknown wire fields. |
| `water_analysis_migration` | Owner primary key; original-file SHA-256, owner-record SHA-256/count, copied count, last copied analysis ID and COPYING/VERIFIED state. |

The history index is `(ownerUid ASC, tankId ASC, observedAtMillis DESC,
createdAtMillis DESC, analysisId DESC)`. First and subsequent pages have a hard
50-row query limit; subsequent pages use the strict lexicographic time/time/ID
cursor. Detail, latest and delete include both owner and tank. No full history
query, automatic retention, overwrite or replacement insert is exposed for rows.
Migration verification reads only 50 destination rows at a time in ID order.
The one-time legacy Proto input is still parsed in memory; this is not a claim
that legacy source reading has constant memory usage.

Java Room declarations use javac annotation processing, so this change does not
introduce kapt/KSP Gradle plugins. Schema JSON must be produced by Room's processor and committed;
it must never be hand-written to imitate compiler output.

## Replay and verification

1. Strictly read and validate the complete source with `WaterAnalysisLegacyReader`.
   Fingerprint the original input bytes, then select only the requested owner.
2. Sort immutable raw records by analysis ID and compute a length-framed SHA-256
   with a versioned domain prefix and owner. Reordering source rows changes the
   file fingerprint but not the ordered record checksum.
3. Refuse a different source, unknown journal state, inconsistent checkpoint/count,
   or pre-existing unjournaled destination rows. Never discard either side.
4. Insert at most 50 rows and advance the durable checkpoint in one Room transaction.
   Conflicts abort; replay continues strictly after the last committed ID.
5. In a single transaction, verify expected count, exact raw record bytes, owner,
   strict ID ordering and correspondence between payload and indexed columns.
   Only then save VERIFIED. Empty-owner migration verifies zero rows without
   adopting another owner's data.
6. Keep the source and staging. Live cutover and post-cutover retention/rollback
   belong to the remaining integration work, not this marker.

A failed checkpoint insert rolls back its event batch. Verification failure leaves
the copying journal and source intact. Database I/O/constraint exceptions propagate
rather than being converted to empty history or success.

## Evidence and remaining gates

- `WaterAnalysisMigrationSourceTest`: 10,000 records in bounded batches, replay,
  owner isolation, exact field/unknown-wire-field preservation, missing/extra/
  duplicate/reordered/changed-row rejection, source identity and safe cursors.
- `WaterAnalysisRoomMigrationInstrumentedTest`: file database close/reopen replay,
  checkpoint-trigger failure rollback, changed source, premature verification,
  changed raw payload/indexed columns and empty-owner migration.
- `WaterAnalysisRoomPagingInstrumentedTest`: 10,000 equal-time rows, scoped
  detail/delete/latest, backdating, request uniqueness and indexed query plan.

Local validation on 27 September 2026:

- Room 2.7.0's actual javac processor generated schema v1 and compiled the Java
  entities, DAO, database and generated implementations against Android API 36.
- Kotlin 2.1.0 compiled the migration service and all ten Android test methods
  against the real Room and Android test dependencies. This is compile evidence,
  not device execution.
- The 16 reader/serializer/migration-source JUnit tests passed.
- `test_water_analysis_sqlite_contract.py` executed the generated schema and
  actual DAO SQL: all five tests passed, including 10,000-row keysets, index
  selection, identity constraints, scoped operations and checkpoint rollback.

The source tests pass locally. Instrumentation tests are acceptance requirements
until executed successfully on API 27 and API 36. The 10,000-row test proves query
behavior when run; it is not a substitute for the low-memory-device latency and
memory budget required by the production contract.

Gradle 8.11.1 successfully regenerated dependency locks and SHA-256 verification
metadata with `:app:dependencies --write-locks --write-verification-metadata sha256`.
The dependency integrity guard passed: 551 modules / 194 configurations; no
existing coordinate or trusted checksum was removed and no existing artifact
acquired a different checksum. The full tool suite passed 313 tests. Existing
architecture guards and Detekt 1.23.8's unchanged baseline policy also passed.
This is not a claim that a full Android APK build or emulator run occurred locally.

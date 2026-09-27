# Water Analysis indexed migration staging

Current checkpoint, 28 September 2026: Room schema v3 is the live authority after
verified owner activation. M.2/M.3/M.4 have accepted CI/device evidence; see the
production checklist and `WATER_ANALYSIS_ACCEPTANCE_EVIDENCE.md`. The sections
below retain the earlier staging checkpoint and its then-open gates. Their
Proto-authority statements do not describe the current runtime.

## Status and authority

This implementation adds Room schema v1 and a verified migration staging path.
The existing Proto store remains the application's live read/write authority.
`VERIFIED` means staging has passed verification, **not** that a live cutover has
occurred. No code here deletes, replaces or truncates the source Proto file.
Composition must switch only after the owner/session/tank gate, cancellation,
archive, rollback and recovery requirements are implemented and accepted.

## Shared mutation gate before live cutover

`OwnerTankMutationGate.shared` now orders the live Proto analysis create and exact
delete paths with `OwnerTankDataCleaner` and `TankCareIntegrityRecovery`. The gate
is acquired before store transactions and held across tank validation, analysis
commit, deletion snapshots, tank commit, dependent cleanup and compensating
rollback. Multi-tank deletions acquire ascending tank IDs; different owners and
tanks have independent locks. Reservations include waiters, are released on
cancellation/failure, and are removed when no operation uses the key. Child
coroutines cannot inherit permission to bypass an occupied gate.

Recovery rereads both pending journal state and authoritative tank existence
after acquiring the gate. A recovery request queued behind live deletion cannot
restore care tasks using a tank snapshot taken before deletion. The low-level
`deleteAnalysesForTank` callback is cleanup-only and deliberately uses the caller's
gate; acquiring the non-reentrant gate again would deadlock.

`OwnerTankMutationGateTest` covers ordering, independent keys, opposite multi-tank
input order, partial-acquisition cancellation, holder cancellation and failure
cleanup. `OwnerTankDataCleanerTest` verifies the journal waits for a writer and
the gate remains held through water cleanup and non-cancellable care rollback.
The multi-tank Android suite also covers queued analysis creation after tank
deletion and recovery waiting behind an in-flight tank deletion.

This closes the live analysis/cleaner ordering gap, but does not activate Room.
Actual Room commits still need session-generation integration; owner-wide cleanup,
archive restore and bounded durable analysis snapshots remain separate acceptance
requirements. The new Android concurrency tests require device/CI execution.

Local gate validation on 27 September 2026 passed with Gradle 8.11.1:
`:app:testDebugUnitTest` filtered to `OwnerTankMutationGateTest` (7 tests) and
`OwnerTankDataCleanerTest` (12 tests), plus `:app:compileDebugAndroidTestKotlin`.
All 19 selected tests passed with zero failures/errors/skips; Android test sources
compiled successfully. Architecture guards and the 313-test tool suite passed.
Detekt 1.23.8 passed against the unchanged baseline with zero blockers and zero
new debt (776 existing advisories). No suppression or baseline entry was added.

For the preceding Room staging commit `e046a82b`, Android CI `36321146886`,
Debug APK `36321146873` and dependency integrity `36321146842` succeeded. API 27
and API 36 emulator acceptance remained pending when this gate change was made.

## Session-bound live writes and cancellation

`UserDataScope` pins a UID across suspensions; that UID alone cannot distinguish
logout or a new session for the same owner. Production composition now binds an
immutable `OwnerSessionWriteLease` to the committed graph's exact owner and
generation. The release-smoke fixture uses the same barrier/lease implementation
with its isolated committed smoke session.

`OwnerSessionCoordinator.open` and `close` share `OwnerSessionMutationBarrier`
with live analysis create/exact-delete operations. The order is session barrier,
owner/tank gate, then DataStore. An admitted write keeps its session until its
durable result; a queued writer validates after acquiring the barrier. Owner
switch, logout, pending activation and same-owner reentry invalidate an old
lease. The DataStore transform revalidates that lease as well. This conservative
barrier serializes live Proto analysis mutations; concurrent Room writer
throughput remains part of the live-cutover performance work.

The DataStore actor can continue disk I/O after cancellation of the caller waiting
for its acknowledgement. `updateDataAwaitingCommit` checks cancellation before
admission, awaits the actual `updateData` result in `NonCancellable`, then
propagates caller cancellation. Session/tank locks therefore cannot be released
while an admitted disk write is still running. A cancelled request may already
be committed and must be reconciled using its original request ID; this is not a
claim that the remaining SavedStateHandle/UI retry work is complete.

Application reads now carry the lease's explicit owner into the store and check
the generation at subscription and each emission. They cannot switch to another
owner through ambient Firebase/coroutine identity. Expiry is a typed cancellation,
so obsolete LiveData collectors stop without converting old-session access into
empty history or an uncaught ordinary exception. This does not replace root-graph
replacement or the remaining route/draft/event lifecycle acceptance tests.

Startup orphan repair, deletion recovery and explicit owner cleanup remain
maintenance paths. They do not recursively acquire a live-write lease while
startup already holds the transition barrier. Owner-wide cleanup, archive restore
and the Room cutover still need their broader acceptance work.

Evidence: `OwnerSessionMutationBarrierTest` covers exact binding, pending/closed
sessions, both write/transition orders, same-owner reentry, failure, cancellation
and child-coroutine exclusion. `DataStoreCommitTest` uses the real file DataStore
and a paused serializer to reproduce acknowledgement cancellation, verify durable
completion before session close, inject write failure and reject pre-admission
cancellation. `WaterAnalysisSessionInstrumentedTest` adds three Android scenarios
for stale create/delete/read, a queued stale writer, and collector/owner isolation.
The last suite requires device execution; compilation alone is not acceptance.

Local Gradle verification on 27 September 2026 passed all 40 selected tests:
8 session-barrier, 6 existing session-state, 4 real DataStore commit, 3 existing
commercial-store concurrency, 7 tank-gate and 12 tank-cleaner tests. Both
`:app:compileDebugAndroidTestKotlin` and `:app:compileReleaseSmokeKotlin` succeeded.
The 313-test tool suite and architecture, composition, application-boundary and
session-startup guards passed. For the preceding `bb0e403a` commit, Android CI
`36322495211`, Debug APK `36322495151` and dependency integrity `36322495126`
succeeded; current session changes require their own CI/device results.

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

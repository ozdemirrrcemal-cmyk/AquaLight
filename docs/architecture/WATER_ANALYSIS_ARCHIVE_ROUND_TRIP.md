# Water Analysis archive and restore evidence

Current checkpoint, 28 September 2026: live water archive reads/restores use Room
after owner activation. Archive v4 adds health history/photos and follow-up
remapping while retaining v1/v2/v3 readers. API 27/API 36 on `78a8311a` passed
the Room/health archive instrumentation tests; that workflow subsequently failed
at minified health navigation. Final archive/preference/release gates remain open.
The dated Proto-bridge sections below are historical; named current evidence is
in `WATER_ANALYSIS_ACCEPTANCE_EVIDENCE.md` and the production checklist.

Implementation date: 2026-09-27. This is the live Proto bridge; it does not claim the Room event-store cutover or device acceptance.

## Explicit format and completeness

Backup and portable export move to schema 3. A new ZIP has an explicit `waterHistory` declaration and `history/water-v1.bin`, including an empty 12-byte stream when the snapshot contains no analyses. Missing declarations/entries, count/size/hash mismatch, duplicate analysis/request IDs, mixed owners, unknown tank references, malformed records, truncation and trailing bytes fail validation before live restore starts. v1/v2 have zero analyses; their envelope cannot smuggle the new history declaration or ZIP entry. The v1 identity reader preserves the documented missing livestock identity compatibility.

The history stream has an eight-byte magic, an exact record count, and length-framed original Proto events. Each event is at most 2 MiB; at most 100,000 events and the existing 64 MiB total uncompressed/compressed archive limits apply. Exceeding a limit fails the export; no records are silently truncated. The reader retains one event payload at a time plus bounded identity sets. Existing live Proto storage still holds its full list in memory; this bridge does not satisfy the indexed Room memory requirement.

## Identity and frozen evidence

The importing account owns every restored event. Tank IDs use the actual archive-to-local tank map; new event and request IDs avoid local collisions. The original observed/created timestamps, raw measurements, unrounded canonical values, frozen context JSON/checksum, historical entity labels, rule outputs and catalog revisions remain unchanged. The archived plant/livestock local IDs are retained by the existing tank restore implementation; findings continue to refer to those historical snapshot identities, including entities that no longer exist in the live tank.

An additive `StoredWaterImportOrigin` contains the first source owner/event/tank/request identities, exact source-event hash, and current restore transaction UUID. This metadata is committed with the event. Re-export/import retains the first source and frozen context rather than nesting snapshots or re-evaluating history. Restored events decode the context against its original tank identity, while record routing uses the current mapped tank. Source provenance never grants ownership or redirects a write to the original account. Repeat restore matches the first source owner/event identity and requires byte-identical evidence and the same mapped tank; conflicting evidence fails, rather than silently duplicating or overwriting.

## Durable recovery

Restore journal v3 writes a UUID before any tank/history mutation. v1/v2 journals remain readable; an old journal with no UUID retains its v2 encoding if rewritten. Production backup/export and restore hold the committed owner/session write lease with an immutable owner scope; history import then takes the ordered owner/tank gates and rechecks tank existence/deletion blocking before the admitted DataStore write. The complete imported event set commits atomically in the live Proto store. Cancellation waits for durable acknowledgement before gates/session are released.

If a later assignment/commit step fails, recovery removes only this owner and this restore UUID's events before rolling back newly created tanks. Previous history, deduplicated events and other owners are retained. A history rollback failure preserves the restore journal and tanks for retry. Reopening the disk store retains the UUID, closing the write-before-journal gap without putting growing history payloads in the small journal. Committed recovery clears the journal and retains the event provenance.

Portable JSON streams `waterAnalyses` with explicit stable field names for raw/source values, optional temperature, canonical measurements, context, findings/conflicts/recommendations and import lineage. An unassessed old event remains unassessed. Preview and completion show actual analysis counts in TR/EN. Failure feedback no longer claims rollback succeeded when recovery itself failed.

## Whole-coordinator ordering

Backup/export, restore and restore recovery, tank deletion and deletion recovery now share an owner archive gate. Lock order is session lease → owner archive gate → ordered tank gates → durable stores. The owner gate stays held through restore completion or rollback, preventing a concurrent tank deletion from snapshotting uncommitted imported history and later resurrecting it. Separate owners can progress independently; a cancelled waiter releases its reservation. Internal rollback calls the already-held-gate recovery entry and never reacquires the non-reentrant owner gate.

An unresolved ACTIVE restore journal blocks new tank deletion, deletion recovery, backup/export and manual analysis writes/deletes. A COMMITTED journal does not block them. Backup/export and a new restore also reject unresolved tank-deletion journals; startup recovers the archive before the dependent deletion recovery. Journal checks involving disk run on IO. The gate is process-local, while the persisted journals enforce the pending-work restriction after reopening.

## Named verification

- `WaterHistoryArchiveTest`: 10,000 exact events, zero history, checksums/counts/trailing/truncated data, duplicate IDs, missing tanks, mixed owners, partial-output cleanup.
- `WaterAnalysisArchiveImportTest`: changed owner/tank/request/event IDs, byte-stable frozen assessment, re-export through another account, tampered provenance rejection, idempotent repeat import, conflicting evidence rejection.
- `WaterHistoryRestoreRecoveryTest`: actual ZIP decode and real DataStore persistence; repeated restore; failure after history commit; disk close/reopen and owner/transaction-specific rollback; failed rollback retains the journal until retry succeeds.
- `WaterHistoryPortableWriterTest`: readable raw zero, absent temperature/assessment, original observation/creation time and source basis/method.
- `UserDataBackupCodecTest`: old v1/v2 zero-history behavior and rejection of missing or smuggled history, plus existing media/size/path validation.
- `OwnerArchiveMutationGateTest`: child coroutine waits for the same owner, another owner progresses, cancelled waiter reservations are pruned.
- `OwnerTankDataCleanerTest`: deletion waits for the full archive coordinator; ACTIVE restore journal rejects deletion and COMMITTED permits it.
- `WaterHistoryRestoreRecoveryTest` also verifies the gate remains held through failed-restore rollback and a pending deletion rejects restore before journal/tank creation.
- `WaterAnalysisSessionInstrumentedTest`: pending real restore journal rejects new save/exact delete and preserves prior history; COMMITTED allows deletion (compiled locally, execution requires Android).

## Remaining acceptance

Room live authority, indexed history UI, method preference persistence/export, migration-stage export policy, tank-duplicate device regression, minified runtime restore/concurrency, and final API 27/API 36 physical/locale/accessibility evidence remain separate gates. No M/W checklist row is closed solely by this document.

Local verification: the final source passed 1,935 debug JVM tests (zero failures/errors/skips), debug Android-test compilation and releaseSmoke Kotlin compilation. The three new journal Android tests are compiled, not executed locally. The 325 Python tests and seven relevant architecture/composition/session/feedback guards passed. Detekt 1.23.8 against the unchanged advisory baseline: zero blockers, zero new debt, 775 existing advisories. R8 releaseSmoke minification and existing-baseline debug lint passed on the preceding archive candidate; final session-scope and strict integer-declaration amendments received fresh unit/compile/Detekt verification. Exact-head baseline-free CI and device/minified-runtime results remain separate evidence.

Coordinator follow-up: 1,941 JVM tests passed with zero failures/errors/skips; debug Android-test and releaseSmoke Kotlin compilation passed. The existing Detekt baseline remains unchanged: zero blockers and zero new debt. This run includes the separately reported Water Analysis Fragment factory correction. The earlier archive commit `d4ef0f0a` also received successful Android CI, dependency-integrity, Firebase and debug-APK workflow results; that success is not substituted for follow-up runtime evidence.

Startup ordering correction: the first coordinator-gate commit (`1a0f00b2`) still invoked archive recovery from `SessionBoundServiceManager.start`, after `OwnerSessionOpenFlow.repairOwnerData`. An ACTIVE archive therefore reached the new deletion-recovery guard before it could recover. Recovery now runs inside `repairOwnerData`, immediately after binding the assignment repository and before assignment/deletion/orphan repairs. Services start only after these repairs succeed. `owner_recovery_order_guard.py`, invoked by the existing session/startup guard, rejects the old late-services placement, late recovery inside repair, and a missing assignment binding; three mutation tests cover those regressions. This corrects the startup-order claim above for the follow-up source rather than claiming the earlier commit had the correct order.

The existing `UserDataArchiveArchitectureTest` now also requires that exact dependency order and rejects a return to service-phase recovery. After the correction, all 1,941 JVM tests, Android-test/releaseSmoke compilation, 332 Python tests and the unchanged-baseline Detekt gate passed (zero blockers/new debt). Physical/process-death execution is still distinct evidence.

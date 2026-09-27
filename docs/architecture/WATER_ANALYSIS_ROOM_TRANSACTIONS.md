# Water Analysis Room transactions and bounded deletion staging

Implementation date: 27 September 2026. Production still uses the existing Proto
facade. These Room components are implemented and locally verified; composition
cutover, cross-store coordinator binding and archive integration remain open.
They are not a claim of Water Quality release acceptance.

## Application read/delete boundary continuation

The application boundary now offers a tank-targeted `latestAnalysis` result;
both the tank entry card and the health dashboard collect one event instead of
an entire projected history. Detail and exact delete require both `tankId` and
`analysisId` through ViewModel, mutation controller, operations and store. A
valid event ID from another tank cannot read or remove that event. Read validation
and historical projection execute on the IO dispatcher. Central navigation and
the existing layouts are unchanged.

The legacy Proto file is still decoded as a whole by DataStore, and the history
screen still collects its full list. This step does not claim indexed storage,
bounded history paging or Room cutover completion. The new operations match the
already staged Room latest/detail/delete primitives for the later cutover.

Local verification: 1,942 JVM tests passed with no failures/errors/skips;
Android-test and releaseSmoke Kotlin compilation passed; 332 Python tests and
navigation/composition/UI dependency/Water Analysis guards passed. The mutation
test checks that both route identities survive until durable acknowledgement.
Two added Android scenarios cover wrong-tank read/delete and latest ordering
after backdating, equal observation times, other-tank writes and deletion. Those
scenarios are compiled, not locally executed; M.5 and E acceptance remain open.

## Schema and authority

### Bounded UI continuation

History now requests 50-record pages through `WaterAnalysisOperations`, displaying
the store total separately from the current page size. Newer/older controls reuse
the existing button styles and record cards. A route saves only one ordering cursor
and direction, so walking all history does not retain records or cursor stacks.
An emptied page returns to the first existing page; deleted cursor rows remain
valid ordering boundaries. The RecyclerView delays scroll-state restoration until
records arrive. Navigation destinations and directions are unchanged.

Room adds the reverse indexed query, returning the nearest 50 newer rows; both
directions use one read transaction for page contents, total and availability.
`WaterAnalysisProtoPagesTest` covers both directions, ordering, wrong tank and
deleted pages. An added actual-Room instrumentation test covers round-trip page
navigation and deletion recovery; the Python SQL contract verifies reverse query
ordering and the index plan on 10,000 rows. Initial local verification passed
1,946 JVM tests, 333 Python tests and Android/releaseSmoke compilation. Device
execution of this continuation is separate from the successful `9f80eb2d` run.

Production remains on Proto at this checkpoint: bounded UI output does not remove
DataStore's whole-file decode. The live Room authority switch is the next gate.

Room's processor generates schema v2. `MIGRATION_1_2` adds request tombstones and
deletion staging without rewriting v1 event payloads. No destructive fallback is
configured. The Android schema-upgrade test constructs the real exported v1
schema, opens it through Room's actual migration and checks retained raw bytes.

`WaterAnalysisRoomActivation` copies bounded batches through the existing
migration journal and verifies original source/owner counts and checksums.
Activation seeds request identities and writes ACTIVE in one transaction. A
failure rolls both back, retaining VERIFIED staging for retry. ACTIVE is distinct
from VERIFIED; later reads and writes refuse a store that has not activated.
Reopening an already active source never recopies deleted historical events.
The original Proto file is retained. Composition must not invoke activation until
legacy writes are stopped and deletion/archive acceptance is ready.

## Atomic event and durable retry

`WaterAnalysisRoomCommit` writes the complete raw/canonical/context/assessment
Proto event and its request fingerprint in one SQLite transaction. The versioned
fingerprint includes input time, tank, temperature/source and all raw
measurement/source/basis/unit fields in deterministic parameter order. It excludes
request identity, catalog state and commit time. Null and measured zero differ;
signed zero and input ordering do not manufacture different payloads.

The request row survives an explicit event deletion. Identical retries return the
original live event; changed input and previously deleted events produce distinct
application errors. The highest request or migrated identity remains reserved,
even after its event is deleted. A delayed save cannot resurrect deleted history.

`WaterAnalysisRoomWriter` orders session, tank and Room gates. It validates the
owner generation and tank before capture, skips recapture for an acknowledged
retry and rechecks authority inside the transaction. An admitted transaction runs
to completion before releasing the external gates, then propagates cancellation.
This does not replace the existing owner-wide cleanup or restore journal.

## Bounded reads and rollback snapshots

`WaterAnalysisRoomQueries` fetches exact owner/tank detail, one latest record, or
50 records after a strict time/time/ID cursor. Count, page and presence of an older
row come from one read transaction; the last full page does not invent an empty
next page. Disk access is dispatched off the caller thread. Conflated observers
are removed even if cancellation occurs during registration.

A tank deletion stage stores raw event bytes in dedicated Room rows, with a
transaction identity, exact count, length-framed SHA-256 and durable
PREPARED/REMOVED state. No growing event payload enters SharedPreferences.
Capture plus its manifest are atomic. Destruction checks the stage and live rows
before deleting events and advancing the marker in one transaction. Restore
validates first, refuses conflicting identities, preserves exact bytes and may
safely repeat. Reads and restoration use 50-row batches. Stage cleanup requires
the same transaction and follows durable resolution of the external tank journal.
Coordinator/recovery binding is the next implementation step; these primitives
alone do not close M.6/W6.

## Archive backend and schema v3 follow-up

Schema v3 adds `water_analysis_import`: owner + original source owner/event identity,
unique local event mapping, original evidence hash and restore transaction UUID.
Its event foreign key cascades on deletion. The owner/restore-UUID/event-ID index
supports 50-row rollback queries without a temporary sort. New allocation also
reserves remapped same-owner source IDs, preventing a later native event from
claiming an already imported original identity.

`MIGRATION_2_3` adds the table/indexes and visits retained raw events one at a time
to backfill existing import provenance, without rewriting payloads. Invalid raw
identity or provenance fails the schema transaction. Activation seeds import and
request identities together with ACTIVE; it also accepts identical mappings
already backfilled by schema migration. Deletion-stage compensation recreates
the mapping with the exact restored event; owner cleanup cascades only that
owner's mappings and preserves the other owners.

`WaterAnalysisRoomArchiveStore` implements the shared history-archive boundary.
Export streams 50-row pages in one consistent Room transaction into the existing
checked length-framed archive. Restore uses indexed source-identity lookup and
atomically commits events, request fingerprints and import mappings. An error
late in the archive rolls back the whole attempt. Deduplication requires the
same mapped tank and byte-identical original evidence. Journal rollback checks
the stored index against each event and removes only the exact owner/transaction,
retaining request tombstones. Admitted writes keep tank gates until durable
completion even on caller cancellation; the outer archive coordinator owns the
session and owner gate.

This is a staged backend, not live Room cutover. Production still composes the
Proto backend through the same interface. The owner migration barrier, activation
binding, Room deletion facade and indexed UI integration remain open.

Named follow-up checks: `WaterRoomArchiveInstrumentedTest` covers multi-page
round-trip, exact evidence, repeat restore, injected index-insert failure, late
conflict atomicity, reopened owner/transaction rollback, request tombstones,
tank-delete compensation and owner cleanup. `WaterRoomSchemaUpgradeInstrumentedTest`
adds real v2→v3 provenance backfill to v1 upgrade coverage. The activation failure
test checks both request and import-index rollback. Python executes the generated
v3 schema and actual DAO SQL, including 10,000 indexed transaction rows, foreign
keys, uniqueness and cleanup rollback. Android execution remains a separate gate.

Local v3 follow-up verification: 1,941 JVM tests passed with zero failures/errors/skips;
debug Android-test and releaseSmoke Kotlin compilation passed. Room's javac
processor generated the committed v3 schema and DAO implementations. All 329
Python tests passed, including 18 actual SQLite contract tests. Navigation,
composition, architecture, UI dependency and Water Analysis guards passed.
Detekt 1.23.8 against the unchanged baseline: zero blockers/new debt and 775
existing advisories. The five new Android scenarios are compiled, not executed
locally; live cutover and device acceptance remain open.

## Verification of the preceding v2 transaction implementation

- Full debug JUnit: 1,911 tests, zero failures/errors/skips. Includes three request
  fingerprint tests and two deletion digest tests.
- Debug Android test sources and releaseSmoke Kotlin compiled. Six new activation,
  commit/paging/schema scenarios and five deletion-stage scenarios are compiled;
  their execution still requires API 27/API 36.
- Python suite: 323 tests passed. Its 12 SQLite contract tests execute the exported
  schema, actual DAO SQL and actual v1→v2 migration SQL; cover 10,000-row keysets and
  staging, exact scope, request tombstones, identity floors and injected transaction
  failures. Host SQLite evidence does not replace Room/device execution.
- Debug lint passed after fixing the earlier quantity wording and obsolete
  resources. The final added staging/schema test changes were compiled separately.
- Detekt and commit-time results are recorded with the commit. Baselines,
  suppressions and architecture guards are not relaxed.

Remaining: live operation/route binding, deletion/restore/owner-cleanup integration,
archive import dedup/remapping, device execution, storage-full/device memory and
latency budgets, and complete E/W9 acceptance. A/P/L gates remain open.

Commit-time local Detekt 1.23.8 passed against the unchanged baseline: zero
blockers, zero new advisory debt, 776 existing advisories. `git diff --check` passed.

The subsequent live-Proto deletion/owner-cleanup binding is described in
`WATER_ANALYSIS_DELETION_RECOVERY.md`. Event-history cutover and archive integration
are still distinct unfinished gates.

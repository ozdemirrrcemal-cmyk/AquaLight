# Water Analysis Room transactions and bounded deletion staging

Implementation date: 27 September 2026. Production still uses the existing Proto
facade. These Room components are implemented and locally verified; composition
cutover, cross-store coordinator binding and archive integration remain open.
They are not a claim of Water Quality release acceptance.

## Schema and authority

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

## Verification

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

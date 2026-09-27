# Water Analysis deletion and owner-cleanup integration

Implementation date: 27 September 2026. This connects durable Room rollback staging
to the currently live Proto store. It does not activate Room event history or close
archive/restore and device acceptance.

The tank deletion coordinator now captures both care tasks and exact water-event
bytes before any destructive store operation. Water bytes live in bounded-query
Room staging rows; the care journal v2 stores only the stage transaction UUID.
The v1 reader remains supported and does not invent a missing water snapshot.
Malformed/unknown versions and noncanonical references fail explicitly.

Care tasks and water events are removed before the tank commit. A failed or
cancelled tank write restores both histories under the held tank gate before
aborting the journal and clearing staging. Failed restoration keeps the journal
and snapshot for recovery. A staging failure starts no destructive operation.
The prior behavior that reported an already-deleted tank when analysis removal
failed has therefore changed: it now reports deletion failure and preserves the
tank and its history. Tests assert that stronger rollback behavior.

Recovery uses actual tank existence: a surviving tank gets its referenced water
snapshot back, and a deleted tank finishes cleanup without resurrecting history.
A required missing/corrupt stage cannot become empty success. Reads validate the
complete stage before appending any restored rows; conflicting live identity
aborts restoration. Resolved stage residues after a crash between journal
resolution and staging cleanup are removed under the tank gate, after rechecking
that no journal transaction remains.

Explicit owner cleanup removes that owner's Proto source first, then clears its
Room events, requests, migration checkpoint and staging in one Room transaction.
Other owners' rows are untouched. The two-store owner-cleanup operation awaits
admitted commits and reports failures for the existing account cleanup retry
mechanism; it is not presented as a single cross-database transaction.

The live legacy destination still rewrites its full Proto. Staging itself is
paged, but this adapter does not close indexed-history or low-memory acceptance.
Room live activation and archive restore remain the next integration gates.

Named evidence: `OwnerTankDataCleanerTest` covers staging failure before mutation,
water restore before journal resolution, retained snapshots after rollback failure,
and the existing cancellation/gate cases. `TankCareIntegrityFormatTest` covers v1,
v2 and malformed references. `OwnerTankDataCleanerMultiTankInstrumentedTest` adds
actual Proto/Room restoration after a failed tank write and interrupted deletion
with a surviving versus deleted tank. These Android cases require execution;
compilation is not device acceptance. The SQLite contract suite now contains 14
tests, including exact owner-wide cleanup and injected cleanup rollback.

Local Gradle verification passed 1,917 debug JUnit tests with zero failures,
errors or skips; debug Android tests and releaseSmoke Kotlin compiled. The full
Python suite passed 325 tests. Device execution and minified runtime acceptance
remain unclaimed.

Commit-time local Detekt 1.23.8 passed against the unchanged baseline: zero
blockers, zero new advisory debt, 775 existing advisories. No suppression or
baseline entry was added. `git diff --check` passed.

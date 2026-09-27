# Water Analysis storage and deletion integrity audit

Reviewed on 27 September 2026 against `feat/water-analysis-ui-flow` at
`3b8920d` and the production branch. This is a gap inventory, not an archive
or recovery guarantee.

| Boundary | Current behavior | Required acceptance |
| --- | --- | --- |
| Save | `DefaultWaterAnalysisOperations` checks current owner and input policy; `WaterAnalysisDataStoreManager` checks tank ownership before the Proto update and active owner inside it. Proto v3 uses request UUID to reuse a committed event for an identical retry. | Commit raw/canonical/context/assessment atomically with indexed `(owner, requestId)` uniqueness. Prove concurrent tank removal and retry behavior with fault injection. |
| Read/corruption | `WaterAnalysesSerializer` validates v1/v2/v3, migrates v1/v2 to v3 in memory without relabelling ambiguous ammonia, and throws on malformed/unsupported content. The old empty-store corruption handler is absent. | Preserve a recoverable original, distinguish I/O from unsupported schema and corruption, and verify migration counts/checksums before Room cutover. |
| History/detail/delete | Owner filter precedes snapshots; owner and tank route are checked by detail UI. DataStore reads and rewrites the full list for a single delete; history keeps all records in memory. | Indexed row reads, keyset pages and transactionally scoped deletes with large-record performance tests. |
| Tank deletion | `OwnerTankDataCleaner` journals care-task snapshots and tank deletion. Analysis cleanup now runs before the journal completes; if it fails, the journal stays pending and owner-session recovery repeats the idempotent cleanup. The analysis write checks the tank-deletion gate inside its Proto update. | Verify crash and cancellation at every durable boundary, including parallel add/delete, then define historical recovery/export before closing M.6. |
| Login/session repair | `TankCareIntegrityRecovery` replays journaled analysis cleanup for deleted tanks before completing the journal. `OwnerSessionCoordinator` also calls `repairOrphanedTankAnalyses`, which removes records whose tank no longer exists without a matching journal. | Never silently call orphan pruning an archive/restore guarantee; preserve recovery evidence and define an explicit orphan disposition. |
| Account removal | `UserDataCleaner` attempts water-analysis owner cleanup before tank cleanup and accumulates step failures. | Verify interrupted and repeated deletion, owner isolation, and failure reporting with final postcondition checks. |
| Backup/restore | Android manifest sets `allowBackup=false`; backup/data extraction rules exclude `datastore/`. No explicit water-analysis export/restore path is present. | Add a versioned, owner-bound, validated export/import that round-trips all raw/provenance/context/assessment data without fabricated historical analysis. |

The current in-memory Proto approach is an interim working record path; it
does not close M.2–M.7. Do not delete the Proto file during a migration until
all records have been counted, checksummed and verified in the new indexed
store, with a defined rollback route.

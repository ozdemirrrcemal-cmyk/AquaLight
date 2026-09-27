# Water Analysis: verified implementation rows

Reviewed 28 September 2026 against `9b731d26`. This maps individual historical
W requirements to executed evidence. It does not close the broader U/S/C/M/E
release gates or the dependent Algae, Plant and Livestock acceptance gates.

## Executed checkpoints

- `c6224e4f`: Android CI `36344376557` and API 27/API 36 run `36344376560`
  passed, including 172 instrumentation tests per API and minified navigation.
- `78a8311a`: Android CI `36351790925` passed JVM tests, baseline-free Lint,
  existing-baseline Detekt and all ten critical package coverage thresholds.
  CodeQL `36351790870` passed. Emulator run `36351790882` passed all 184
  instrumentation tests on each API, then failed during the later minified
  health-form smoke. Its overall workflow is **failed**, not accepted.
- `9b731d26`: fixes the missing smoke image processor and requires thirteen
  screens across six profiles. Android CI `36354005818` and emulator run
  `36354005814` passed: 184 instrumentation tests and 78 validated screenshots
  on each API, including minified health navigation. CodeQL `36354005819`, APK,
  dependency and Firebase workflows also passed. The routes currently
  exercise empty/missing-record states; populated records are the next check.

## Individually accepted historical requirements

| Row | Production boundary and named evidence | Scope of closure |
| --- | --- | --- |
| W5.1 | `WaterAnalysisRoomRuntime`, `WaterAnalysisDatabase`; `WaterAnalysisCutoverInstrumentedTest.activeAuthorityDoesNotReadLegacyOrResurrectDeletedRows`. | Dedicated Room authority after verified owner cutover; original legacy input retained. |
| W5.2 | `WaterAnalysisDatabase.getInstance`, `WaterRoomSchemaUpgradeInstrumentedTest`, `WaterAnalysisRoomMigrationInstrumentedTest`, `WaterAnalysisRoomPagingInstrumentedTest.pageQueryUsesTimeIndexWithoutTemporarySort`; `WaterAnalysisRoomWriter` holds session → owner → tank gates. | Singleton database, additive Room migrations and owner/tank/time/identity indexes. Existing Proto data is not rewritten to become Room schema. |
| W5.5 | `WaterAnalysisSessionInstrumentedTest.savedEvaluationIsAtomicAndRetryDoesNotReadChangedContext`, `evaluationFailureDoesNotLeaveARawOnlyRecord`; `WaterAnalysisRoomCommitInstrumentedTest.eventAndRequestInsertAreOneTransactionAndRetryIsExact`. | Raw input, frozen evaluation and request identity commit atomically; no raw-only success after evaluation failure. Scientific completeness remains separate. |
| W5.6 | `WaterAnalysisSessionInstrumentedTest.mismatchedTankCannotReadOrDeleteAnExistingAnalysis`, `oldCollectorIsCancelledAndExplicitOwnerReadsNeverFollowAmbientAccount`; bounded query tests in `WaterAnalysisRoomCommitInstrumentedTest`. | Application page/latest/detail/create/delete operations enforce owner and route identity; page reads replace the old full-list observation API. |
| W5.7 | `WaterAnalysisRoomPagingInstrumentedTest.backdatingAndCreatedTimeTiesUseStableOrderAndExactDeletion`, `tenThousandTiedRowsPageWithoutGapsAcrossOwnersAndTanks`; `WaterAnalysisSessionInstrumentedTest.latestUsesObservationBeforeCommitOrderAndKeepsOtherTanksSeparate`. | Observed time, created time and ID define stable ordering; backdated writes cannot replace a newer observation. |
| W5.8 | `WaterAnalysisLatestMeasurementsTest.missingMetricInLatestEventDoesNotBorrowOlderValue`, `latestEventIsIndependentOfListOrderAndUsesCommitTimeBeforeId`; production dashboard/entry collect `latestAnalysis`. | One latest event supplies displayed measurements; absent parameters are not filled from older events. |
| W5.9 | `WaterAnalysisRoomCommitInstrumentedTest.eventAndRequestInsertAreOneTransactionAndRetryIsExact`, `activationRequiresExactVerificationAndIsDurableWithoutReimportAfterDeletion`; `WaterAnalysisMutationControllerTest` retry case. | Owner/request uniqueness, exact retry, changed-payload rejection and durable deletion tombstones. |
| W5.10 | `WaterAnalysisRoomPagingInstrumentedTest.backdatingAndCreatedTimeTiesUseStableOrderAndExactDeletion`; `WaterAnalysisSessionInstrumentedTest.mismatchedTankCannotReadOrDeleteAnExistingAnalysis`, `latestUsesObservationBeforeCommitOrderAndKeepsOtherTanksSeparate`. | Repeated exact deletion is harmless; latest selects the next record, and deleting the sole record returns null/empty content. Wrong-tank deletion cannot remove it. |
| W6.3 | `TankCareIntegrityFormatTest` legacy/current/malformed entry cases; `WaterAnalysisCutoverInstrumentedTest` pending-journal admission cases; `WaterArchiveJournalInstrumentedTest`. | The care journal keeps bounded v2 transaction references, reads v1 without inventing a snapshot, rejects unknown/malformed entries and blocks cutover while recovery is pending. The archive journal preserves its separate transaction identity. |
| W6.4 | `WaterDeletionStageInstrumentedTest.restartAfterRemovalRestoresExactBytesOnceAndRetainsOtherTanks`, `fullDiskAtManifestWriteLeavesNoStageAndDoesNotBeginDestruction`, `checksumCorruptionCannotDeleteLiveRowsOrRestorePartialHistory`, `removalAndMarkerFailureRollBackTogetherAndChangedTransactionCannotCleanStage`, `rollbackRefusesConflictingLiveIdentityWithoutOverwritingEitherSide`. | Bounded durable Room staging, checksums, atomic marker/removal and reopen/retry recovery. The disk-full case is injected at the write boundary; it is not a physical-device capacity benchmark. |
| W7.3 | `WaterAnalysisNavigationSmoke.verify` on the successful `c6224e4f` minified runs; application session-isolation tests above. | Five real routes obtain distinct central-factory ViewModels; back navigation retains the correct instance and stale duplicate navigation is rejected. |
| W7.6 | `WaterAnalysisMutationControllerTest` pending-save/delete, exact retry, cancellation and retained-delete-identity cases; atomic Room commit tests above. | A pending mutation blocks duplicate commands; success follows durable acknowledgement; cancellation is propagated. |

## Remaining acceptance limits

W5.3/W5.4 retain their broader semantic/presence/provenance acceptance. W5.12
still needs complete live Room failure-category coverage; an error must never
be treated as empty history. W5.13/M.5 require measured latency and memory on a
representative low-memory device: the existing 10,000-row ordering/index tests
are functional evidence only. M.6/M.7 still include persisted method preferences,
all recovery boundaries and complete archive/minified acceptance. UI navigation
evidence alone does not prove process-death draft restoration or TalkBack usability.

These twelve closures must not be added to the formal gate total: the historical
W rows overlap the already accepted M rows and other release requirements.

## Additional verification awaiting device execution

`HealthObservationSmokeFixture` writes all three kinds through the production
owner-bound operations and verifies exact retries and reads. The minified
navigation smoke waits for populated history, ready forms and saved detail,
then follows each record into a new form and verifies the frozen parent/subject
identity and back-stack ViewModel. This expands the previous missing-record route
check; it has not yet passed device CI.

Manual review of `9b731d26` API 36 screenshots found dark default checkbox text
on the navy surface in the light profile and missing dropdown insets. The
continuation binds checkbox text/tint to Aqua semantic colors, adds a shared
outlined dropdown style, and checks rendered choice text/control contrast in
each health visual profile. The preceding automated pass did not detect that
visual issue and must not be described as complete accessibility acceptance.

`WaterTankDuplicateHistoryInstrumentedTest` adds a real-store regression for
W6.13/M.7: duplicating a tank must leave both Water and Plant Health history on
the original, even though the copied tank retains the local plant ID. The test
also verifies that the original saved evidence is unchanged. W6.13 stays open
until this added scenario executes on API 27/API 36.

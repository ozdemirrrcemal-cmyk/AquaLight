# Water Analysis: verified implementation rows

Reviewed 28 September 2026 against `7169891e`. This maps individual historical
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
  dependency and Firebase workflows also passed. At that checkpoint the routes
  exercised empty/missing-record states; the later checkpoint below adds saved records.

- `7169891e`: all applicable CI workflows passed: Android `36356386170`,
  CodeQL `36356386173`, emulator `36356386129`, APK, dependency and Firebase.
  API 27 and API 36 each passed **185 tests and 78 screenshots**. Populated
  health list/detail, ready forms, exact retries and follow-up parent/subject
  identity passed in the minified build, with rendered choice contrast checks.

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
| W6.13 | `WaterTankDuplicateHistoryInstrumentedTest.duplicateKeepsBothHistoriesOnlyOnOriginalTankEvenWhenPlantIdentityIsCopied`, API 27/API 36 run `36356386129`. | Real production tank duplication retains local plant ID but copies neither Water nor Plant Health history; original saved evidence remains identical. |
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

These thirteen closures must not be added to the formal gate total: the historical
W rows overlap the already accepted M rows and other release requirements.

## Populated health and duplication verification

`HealthObservationSmokeFixture` writes all three kinds through production
owner-bound operations and verifies exact retries and reads. On `7169891e`,
both API jobs passed populated history, ready forms, saved detail, follow-up
parent/subject identity and back-stack ViewModel checks. This supersedes the
previous empty/missing-record route-only evidence.

Manual review of `9b731d26` screenshots found low-contrast default checkbox text
in the light profile and missing dropdown insets. `7169891e` uses Aqua semantic
text/tint colors and a shared outlined dropdown style. Rendered text/control
contrast checks passed in all six profiles on both APIs. API 36 light Plant form,
saved Plant detail and large-font-light Livestock form were visually inspected
on 28 September. Full TalkBack/scroll/interaction acceptance remains separate.

`WaterTankDuplicateHistoryInstrumentedTest` passed on both APIs. API 36 JUnit XML
records 185 tests, zero failures/errors/skips, including the named duplicate
case. W6.13 is accepted; the broader M.7 preference/concurrency gate stays open.

## Timezone continuation (device execution pending)

The existing component-replacement tests only covered a stable device timezone.
Water/Health controllers saved a draft timezone, but shared picker construction
and rendered date/time used the current device zone. The continuation explicitly
passes the draft zone to both, and persists the picker zone in fragment arguments
for recreation. Existing callers keep the device-zone default and existing
payload/bounds arguments. No navigation or approved layout is changed.

`ObservationTimeZoneInstrumentedTest` adds date/time picker recreation after a
device-zone change and TR/EN 12/24-hour formatting checks. The JVM formatter
regression checks an instant that falls on different calendar days in Tokyo and
Los Angeles. These new tests must pass before this fix receives CI acceptance.
The water input still lacks the original sample-offset field, and broad
process-death/DST acceptance remains open under E.2/E.3/W8.3.

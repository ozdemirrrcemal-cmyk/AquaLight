# Water Analysis navigation regression — 2026-09-27

## Cause and correction

The reported device stack ends in `NoSuchMethodException: WaterAnalysisViewModel.<init>[]`, reached from `TankDetailTankFragment.observeHealthEntry`. Commit `c56808f4` changed five Water Analysis consumers from `activityViewModels()` to fragment-owned `viewModels()` while adding constructor `SavedStateHandle` and immutable tank/event route identities. The old activity scope obtained the injected factory from `BaseActivity`. Plain fragment `viewModels()` used the Fragment's default factory, which could not construct the dependency-injected ViewModel. This was an integration regression, not a missing navigation destination.

The scope change implements production contract §29.1 (K14): drafts, request IDs and selected event IDs must not bleed between tank routes. Commit `0a25a9fa` keeps that scope and explicitly resolves `requireAppContainer().defaultViewModelFactory` in all five fragments, using the existing `DeviceLightQuickSetupFragment` construction pattern. The delegate still uses the Fragment's default creation extras, preserving route arguments and saved state. The existing `AquaViewModelFactory → OwnerViewModelFactory` chain constructs dependencies and calls `createSavedStateHandle()`; no data store, repository, database or alternative factory is constructed in UI code.

## Central architecture comparison

Compared production branch base `3b8920d1349e8c4b657fb9e352c3953a9f566294` with corrected commit `0a25a9fa6e88b984770a18f6508252b3f052b9be`:

| Area | Result |
| --- | --- |
| All `app/src/main/res/navigation` graphs | No diff, including `nav_aquarium.xml` and existing `analysisId` argument |
| `ui/navigation` (`RootNavigator`, `AppRouteNavigator`, destination contracts) | No diff |
| `ui/main` shell and main navigation | No diff |
| `ui/tabs/aquarium/navigation` helpers and tab contracts | No diff |
| `TankDetailFragment`, pager adapter and tab coordinator | No diff |
| Water feature forward actions | Existing generated Directions and `navigateSafelyFrom` |
| Back navigation and write completion | Existing NavController; confirmed mutation navigates only from its own destination |
| Confirmation and feedback | Central confirmation dialog, FragmentResult and BaseActivity Snackbar |

`TankDetailTankFragment` remains a pager child, not a new production graph destination. Its existing `newInstance(tankId)` bundle is the manual-child exception documented by `navigation_guard.py`. The four health graph destinations continue to read their arguments using Safe Args.

## Verification and limits

Passed locally: navigation guard (including shared device-root, device-family and UI/data isolation), composition root, architecture, aquarium application boundary, UI dependency construction, process-safe feedback, session/startup and Water Analysis guards. `WaterAnalysisViewModel` is now in the existing injected-fragment guard, so a return to a plain `viewModels()` delegate fails CI.

The corrected working tree, including the separately pending archive-coordinator changes, passed 1,941 JVM tests with zero failures/errors/skips, debug Android-test compilation and releaseSmoke Kotlin compilation. Python tests: 325 passed. Detekt against the unchanged baseline: zero blockers and zero new debt.

`WaterAnalysisNavigationSmoke` is called before the existing release-smoke pass marker. It opens all five real fragments, checks distinct route ViewModels and stable instances on back-stack view recreation. The follow-up expands it to inflate the production `nav_aquarium.xml`, use generated Directions and `navigateSafelyFrom` for health/add/history/detail, and reject repeated stale-source navigation. Only the pager child is mounted as an extra test-host destination; production XML and entry routes are unchanged.

This environment has no connected Android device/emulator. The smoke code is compiled locally; API 27/API 36 execution and full parent-pager/physical-device acceptance require observed CI/device results. Static checks and JVM success do not establish those runtime results, and no checklist acceptance row is closed by this report.

### Observed CI outcome on `1a0f00b2`

Android CI `36338668121` and installable APK `36338668183` passed. Emulator run
`36338668127` completed 158 instrumentation tests on each of API 27 and API 36:
157 passed and one failed on each API. `WaterRoomSchemaUpgradeInstrumentedTest`
failed while constructing its historical database fixture because the generated
Room schema omits `indices` for entities with no indices. The fixture reader now
handles that omission; its migration and exact-payload assertions remain intact.
The instrumentation failure prevented the subsequent minified navigation smoke
from running, so it provides no five-screen navigation acceptance evidence.

CodeQL run `36338668208` stopped before analysis because release-smoke lint
reported `UseRequireInsteadOfGet` on the nested navigation-host expression. The
smoke now obtains the primary navigation fragment with an explicit missing-fragment
error. No lint suppression or baseline change was introduced. Both corrections
require a new successful CI run; the previous failures are not counted as passes.

The corrected source passed local `:app:compileDebugAndroidTestKotlin` and
`:app:lintReleaseSmoke` (including its Detekt policy task): zero new lint issues,
775 existing Detekt advisories and zero new debt. All 332 Python tests and the
navigation/composition/UI-dependency/Water Analysis/session guards passed. A new
comparison through `c6951c4c` and these test-only fixes still finds no production
navigation or tank pager changes. Android instrumentation and minified route
execution still require the new CI result.

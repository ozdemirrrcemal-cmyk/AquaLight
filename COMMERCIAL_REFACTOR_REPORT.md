# AquaLight Commercial Refactor Report

This package applies a broad application-level production refactor on top of the previous catalog refactor.

## Completed refactor scope

### 1. Layering and package boundaries

The project previously had persistence/background/data classes depending on UI packages. This has been corrected for the guarded layers:

- `app/src/main/java/com/aqua/aqualight/data/**`
- `app/src/main/java/com/aqua/aqualight/app/**`
- `app/src/main/java/com/aqua/aqualight/base/**`

These layers now pass `tools/architecture_guard.py`, which blocks imports from `com.aqua.aqualight.ui.*`.

### 2. Aquarium domain/data relocation

Moved UI-independent aquarium models out of UI packages:

- `TankDraft`
- `SavedAquariumTank`
- `SavedAquariumPlant`
- `SavedAquariumMaterial`
- `SavedAquariumLivestock`
- `LivestockCategories`

New packages:

- `com.aqua.aqualight.data.aquarium.model`
- `com.aqua.aqualight.data.aquarium.catalog.livestock`

### 3. Maintenance / care domain relocation

Moved care task persistence models and notification/background classes out of UI packages:

- `CareTask`
- `CareTaskSource`
- `CareTaskStatus`
- `CareTaskType`
- `CareTaskReminderScheduler`
- `CareTaskReminderReceiver`
- `CareTaskBootReceiver`
- `CareTaskTypeCatalog`
- `CareTaskTypeDefinition`

New packages:

- `com.aqua.aqualight.data.care.model`
- `com.aqua.aqualight.data.care.catalog`
- `com.aqua.aqualight.data.care.reminder`

The manifest receiver declarations were updated accordingly.

### 4. SmartCare relocation

Moved SmartCare business/background logic out of UI packages:

- `SmartCareDailyWorker`
- `SmartCareTaskGenerator`
- `SmartCareProfileBuilder`
- `SmartCareRuleCatalog`
- `SmartCareRuleModels`
- `SmartFertilizerDoseCalculator`
- fertilizer dose catalog/models

New package:

- `com.aqua.aqualight.data.care.smartcare`

### 5. Light program domain relocation

Moved light program and light curve domain/runtime models out of UI packages:

- `LightCurvePoint`
- `LightCurveChannelValues`
- `LightCurveTransitionMode`
- `LightCurveGraphState`
- `TodayLightPlanGraphState`
- `LightCurveInterpolator`
- `LightProgramDraft`
- `SavedLightProgram`
- `SavedLightPreset`
- `RepeatMode`
- `MoonlightSettings`
- `CloudSimulationSettings`
- `LightProgramTimeline*`
- `LightProgramDraftValidator`
- `LightProgramScheduleConflictValidator`

New packages:

- `com.aqua.aqualight.data.devices.light.curve.model`
- `com.aqua.aqualight.data.devices.light.curve.interpolator`
- `com.aqua.aqualight.data.devices.light.programs.model`
- `com.aqua.aqualight.data.devices.light.programs.timeline`
- `com.aqua.aqualight.data.devices.light.programs.validation`
- `com.aqua.aqualight.data.devices.light.presets.model`

UI-only state and event classes remain in UI packages.

### 6. Device catalog naming cleanup

Renamed UI-oriented data-layer naming:

- `AquaDeviceUiController` -> `AquaDeviceControllerType`
- `uiController` -> `controllerType`
- `DevicesDataStoreManager.DeviceInfoUi` -> `DevicesDataStoreManager.DeviceInfo`
- `UserPreferencesManager.UsageAnalyticsUi` -> `UserPreferencesManager.UsageAnalytics`
- `CareTaskTypeUi` -> `CareTaskTypeDefinition`

### 7. Production build hardening

Updated release build configuration:

- `minifyEnabled true`
- `shrinkResources true`
- release ProGuard keep rules for WorkManager worker and manifest receivers
- release crash line-number attributes preserved
- lint now uses `abortOnError true`
- `android.nonTransitiveRClass=true`
- `android.nonFinalResIds=false`

### 8. CI architecture enforcement

Added:

- `tools/architecture_guard.py`

Updated workflows:

- `.github/workflows/android.yml`
- `.github/workflows/android_release.yml`

The architecture guard runs in CI before Gradle build steps.

## Validation performed in this environment

Completed:

- XML parse validation for all `res/**/*.xml`
- architecture guard validation
- static search for old moved-package references
- static search confirming guarded data/app/base layers do not import UI packages

Not completed in this environment:

- Full Gradle build, because Gradle wrapper attempted to download `gradle-8.11.1-all.zip` from `services.gradle.org`, and this environment has no internet access.

Run locally in Android Studio or CI:

```bash
./gradlew clean :app:compileDebugKotlin :app:assembleDebug lintDebug testDebugUnitTest
./gradlew assembleRelease lintRelease testReleaseUnitTest
python3 tools/architecture_guard.py
```

## Production notes

This refactor makes the project materially closer to a commercial-grade Android architecture by enforcing core layer separation, moving background/domain/persistence models out of UI packages, and enabling release shrinking. The remaining highest-value next step is Gradle build verification plus screen-level regression testing across aquarium creation, maintenance reminders, device setup, light program editing, SmartCare generation and release signing.

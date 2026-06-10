# AquaLight Navigation Contract & Safe Args Refactor

## Scope
This refactor standardizes navigation contracts across the existing app without implementing unfinished Dosing/Timer/Cooling feature screens.

## Implemented

### Safe Args infrastructure
- Added `androidx.navigation.safeargs.kotlin` plugin to `settings.gradle` and `app/build.gradle`.
- Converted raw `R.id.action_*` Kotlin navigation calls to generated `Directions` classes.
- Added `tools/navigation_guard.py` to prevent new raw action-id navigation references.
- Updated GitHub workflows to run both architecture and navigation guards.

### Navigation argument contract cleanup
- Added missing `deviceDosingFragment` arguments:
  - `deviceId`
  - `deviceIp`
  - `deviceTitle`
- Added missing `deviceSetupFragment` arguments:
  - `setupSsid`
  - `displayName`
  - `familyName`
  - `deviceType`
- Added missing `tankSettingsFragment` argument:
  - `startTab`
- Added missing `reAuthenticateFragment` argument:
  - `arg_action`
- Added a global Safe Args action for notification-driven task detail navigation:
  - `action_global_taskDetailFragment`

### Device controller routing contract
- Added `AquaDeviceControllerRoute`.
- Added exhaustive `AquaDeviceControllerType.toControllerRoute()` mapping.
- Removed the broad `else` fallback from `DeviceRouterFragment` controller routing.
- Routed advanced/matrix light controller types to the existing light controller shell.
- Kept `FULL_CONTROLLER` and `UNSUPPORTED` explicitly routed to unsupported until feature screens exist.
- Added unit test coverage for controller route mapping.

### Build-error cleanup carried forward
- Replaced remaining `DeviceInfoUi` references in debug/test sources with `DeviceInfo`.

## Deliberately not implemented
The following are feature work, not navigation-contract work:
- Real Dosing controller UI
- Real Timer controller UI
- Real Cooling controller UI

The current Dosing/Timer/Cooling fragments remain navigation-ready shells.

## Local verification performed in this environment
- XML parse check passed for all `res/**/*.xml` files.
- `tools/architecture_guard.py` passed.
- `tools/navigation_guard.py` passed.
- Static scan shows no Kotlin `R.id.action_*` navigation references remain.

## Not fully verifiable in this environment
A complete Gradle compile could not be executed because the Gradle wrapper attempts to download `gradle-8.11.1-all.zip` and this environment has no internet access.

Run locally/CI:

```bash
./gradlew clean :app:compileDebugKotlin :app:compileReleaseKotlin
./gradlew :app:compileReleaseUnitTestKotlin
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew lintDebug lintRelease testDebugUnitTest testReleaseUnitTest
python3 tools/architecture_guard.py
python3 tools/navigation_guard.py
```

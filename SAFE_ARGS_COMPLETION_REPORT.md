# Safe Args Completion Report

This package completes the Safe Args transition for Navigation Component graph destinations.

## Completed

- Every `nav_app.xml` fragment destination that declares `<argument>` now has a `by navArgs()` delegate.
- Navigation argument reads were removed from nav graph destination fragments:
  - no `requireArguments().getLong/getString/getInt/getBoolean` remains in nav destinations
  - no `arguments?.getLong/getString/getInt/getBoolean` remains in nav destinations
  - no `Args.fromBundle(requireArguments())` remains in nav destinations
- `tools/navigation_guard.py` was upgraded to enforce both sides of the contract:
  1. no raw `R.id.action_*` navigation references in Kotlin
  2. no manual argument reads inside argument-based nav graph destinations
- Existing bottom sheet, FragmentResult, and manual child-fragment bundles are intentionally not converted because they are not Navigation Component destination arguments.

## Intentionally Allowed Non-Safe-Args Bundles

These remain by design:

- `ui/common/bottomsheet/*` arguments
- `FragmentResult` bundles
- child-fragment tab/wizard container arguments such as TankDetail tab pages and TankSettings tab pages
- root graph resets such as `R.id.nav_app` and `R.id.authContainerFragment`, because those are destination resets, not argument-bearing actions

## Verified Static Checks

- `python3 tools/architecture_guard.py`
- `python3 tools/navigation_guard.py`
- XML parse check for `app/src/main/res/**/*.xml`

Gradle compile could not be run in this environment because the wrapper attempts to download `gradle-8.11.1-all.zip` from `services.gradle.org`, which is not reachable here.

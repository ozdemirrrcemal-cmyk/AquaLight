# Build Fix Report

## Fix applied

The release build error in `CreateTankViewModel.kt` was caused by the missing import for the moved data model:

```kotlin
import com.aqua.aqualight.data.aquarium.model.TankDraft
```

Because `TankDraft` was unresolved, Kotlin could not infer the type of `tankDraft`. That cascaded into the later errors in `TankPhotoFragment.kt` such as unresolved `plantName`, incorrect `category` type, and `removeAt` inference failures.

## Validation performed in this environment

- `tools/architecture_guard.py` passes.
- Static search confirms no old moved aquarium/catalog package imports remain.
- Full Gradle compilation could not be executed in this environment because the Gradle wrapper requires downloading `gradle-8.11.1-all.zip` and internet access is blocked here.

## Required validation in Android Studio / CI

Run:

```bash
./gradlew clean :app:compileReleaseKotlin :app:assembleDebug lintDebug testDebugUnitTest
python3 tools/architecture_guard.py
```

# Firebase environment contract

## Isolation model

Firebase release-status environments are separate Android product flavors. A build variant is
created only when its Firebase environment and Android build type are an approved pair.

| Environment | Enabled Android variant | Application ID | Firebase project | Backend behavior |
|---|---|---|---|---|
| Development | `developmentDebug` | `com.aqua.aqualight.dev` | `demo-aqualight-development` | Auth and Firestore use the local Emulator Suite at `10.0.2.2` |
| Staging | `stagingReleaseSmoke` | `com.aqua.aqualight.staging` | `demo-aqualight-staging` | Safe non-live placeholder until a dedicated staging project is provisioned |
| Production | `productionRelease` | `com.aqua.aqualight` | `aqualight-58aa2` | Existing live Firebase project; reachable only from the production variant |

The `demo-` project IDs have no live Firebase resources. A development or staging operation fails
instead of falling through to production when its intended emulator or future staging service is
unavailable.

Non-production launcher labels include `Dev`/`Geliştirme` or `Staging`, so builds that can coexist
on one device cannot be mistaken for the production application.

The following combinations are deliberately not created:

- development release or release-smoke;
- staging debug or production release;
- production debug or release-smoke.

`app/google-services.json` is forbidden because the Google Services plugin would apply it as a
fallback to every variant. Each configuration instead lives in
`app/src/<environment>/google-services.json`, and Gradle verifies the project ID, package ID, API
key and web OAuth-client presence before any Android build.

## Development

Start the isolated Auth and Firestore emulators from the repository root:

```bash
npx --yes firebase-tools@15.24.0 emulators:start \
  --only auth,firestore \
  --project development
```

Then build or test the development application:

```bash
./gradlew assembleDevelopmentDebug
./gradlew testDevelopmentDebugUnitTest
./gradlew connectedDevelopmentDebugAndroidTest
```

Email/password authentication can use the Auth emulator. The committed development OAuth client is
deliberately non-live; it cannot authenticate against AquaLight production Google Sign-In.

## Staging

CI and emulator smoke tests use:

```bash
./gradlew \
  lintStagingReleaseSmoke \
  testStagingReleaseSmokeUnitTest \
  assembleStagingReleaseSmoke
```

The current staging Firebase identity is a fail-closed demo placeholder. To activate a live staging
backend, create a separate Firebase project, register only
`com.aqua.aqualight.staging`, enable the same Auth providers and deploy the committed Firestore
rules. Replace only `app/src/staging/google-services.json`, then update the staging project ID in
`.firebaserc`, `app/build.gradle` and `tools/firebase_environment_guard.py` in one reviewed change.
Never reuse the production project or its user data for staging.

## Production

The existing production application ID, Firebase project and user data remain unchanged. Only the
controlled tag workflow may build:

```bash
./gradlew \
  lintProductionRelease \
  testProductionReleaseUnitTest \
  assembleProductionRelease
```

Production Firestore deployment must use the explicit alias:

```bash
firebase deploy --project production --only firestore
```

Android Firebase configuration values identify an app and project; they are not release-signing
credentials. Keystores, passwords, service-account keys and deploy tokens remain outside source
control and are not part of any `google-services.json`.

## Enforcement

- `FirebaseEnvironmentInstaller` checks the selected Firebase project and application ID before
  constructing repositories.
- Development routes Auth and Firestore to the emulator before either client can be used.
- `verifyFirebaseEnvironmentSeparation` runs before Android builds.
- `tools/firebase_environment_guard.py` rejects shared project/app/API/OAuth identities, legacy
  aggregate CI tasks and hard-coded production OAuth resources.
- CI names every environment-specific Gradle task explicitly; it does not use ambiguous
  `assembleDebug`, `assembleRelease` or `connectedDebugAndroidTest` tasks.

# AquaLight Firebase environment contract

This document defines the only supported Firebase configuration path for AquaLight Android builds.
Firebase configuration is execution-time material and must never be committed to the repository.

## Environment identities

| Environment | Android build type | Application ID | Required configuration input |
|---|---|---|---|
| Debug | `debug` | `com.aqua.aqualight.debug` | `AQL_FIREBASE_DEBUG_CONFIG_BASE64` |
| Staging | `staging` | `com.aqua.aqualight.staging` | `AQL_FIREBASE_STAGING_CONFIG_BASE64` |
| Release smoke | `releaseSmoke` | `com.aqua.aqualight.smoke` | `AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64` |
| Production | `release` | `com.aqua.aqualight` | `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64` |

Each input must contain the base64 encoding of the exact `google-services.json` downloaded for that
Android application from its Firebase project. The four configurations must use distinct Firebase
project IDs, project numbers and mobile SDK app IDs.

## Fail-closed rules

- No repository, root-module, `src/main`, placeholder or legacy Firebase configuration is accepted.
- Missing or invalid base64 input stops the affected Gradle task.
- Invalid JSON, an unsupported configuration version or malformed project identity stops the task.
- A configuration must contain exactly one Android client for the expected application ID.
- The matching client must contain exactly one API key and exactly one Web OAuth client.
- Reusing an Android application ID, Firebase project ID, project number or mobile SDK app ID across
  environments is rejected.
- Generated files are restricted to the four build-type directories and remain ignored by Git.

## GitHub Actions protection

Non-production pull-request jobs use only the debug, staging and release-smoke repository secrets.
They never reference the production Firebase input or the `production-release` environment.

The Stage 14 protected Firebase job, the production release-variant validation job and the release
workflow reference the `production-release` GitHub environment only on an allowed branch push or
controlled release tag. Production configuration therefore cannot reach a runner until that
environment's protection and deployment rules allow the job to start. The environment must restrict
access to `commercial/14-final-validation` while Stage 14 is active and to controlled `v*.*.*` tags.

The following input names are used by the controlled chain:

- `AQL_FIREBASE_DEBUG_CONFIG_BASE64`
- `AQL_FIREBASE_STAGING_CONFIG_BASE64`
- `AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64`
- `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64`

The three non-production values are repository secrets. The production value is exclusively a
`production-release` environment secret. The four values must never be printed, persisted as an
artifact or copied into a tracked file.

## Required validation

Pull requests must pass the non-production Firebase tasks without access to production material:

```text
:app:verifyFirebaseRuntimePolicy
:app:processDebugGoogleServices
:app:processStagingGoogleServices
:app:processReleaseSmokeGoogleServices
```

The protected Stage 14 branch push must pass the complete contract on the same commit:

```text
:app:verifyFirebaseConfigurationContract
:app:verifyFirebaseEnvironmentIsolation
:app:verifyFirebaseRuntimePolicy
:app:processDebugGoogleServices
:app:processStagingGoogleServices
:app:processReleaseSmokeGoogleServices
:app:processReleaseGoogleServices
```

Successful protected validation publishes only redacted identity evidence containing package names,
Firebase project IDs, project numbers and mobile SDK app IDs. Generated `google-services.json` files
are deleted from every runner after validation.

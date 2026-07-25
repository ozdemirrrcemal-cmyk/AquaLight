# Stage 14 — Firebase Environment Contract

Status: implemented; CI evidence pending  
Branch: `agent/stage-14-final-commercial-validation`

## Commercial rule

AquaLight does not read Firebase configuration from a repository file. No compatibility fallback, legacy lookup or source-controlled `google-services.json` is permitted.

Every Firebase configuration is materialized at build execution time from an environment-scoped base64 value. The only exception is a deterministic, non-routable CI identity used by explicitly non-production variants when GitHub Actions runs without application secrets.

## Environment mapping

| Build type | Application ID | Environment | Configuration source | Production access |
|---|---|---|---|---|
| `debug` | `com.aqua.aqualight.debug` | Debug | `AQL_FIREBASE_DEBUG_CONFIG_BASE64` or deterministic CI identity | Forbidden |
| `staging` | `com.aqua.aqualight.staging` | Staging | `AQL_FIREBASE_STAGING_CONFIG_BASE64` or deterministic CI identity | Forbidden |
| `releaseSmoke` | `com.aqua.aqualight` | CI release smoke | `AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64` or deterministic CI identity | Forbidden |
| `release` | `com.aqua.aqualight` | Production | `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64` only | Required |

The `releaseSmoke` variant is minified and release-like, but it is not a production build and must not receive the production Firebase configuration.

## Required protected release secrets

The `production-release` GitHub Environment must provide all of the following values:

- `AQL_FIREBASE_DEBUG_CONFIG_BASE64`
- `AQL_FIREBASE_STAGING_CONFIG_BASE64`
- `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64`

The protected release build verifies that the decoded Debug, Staging and Production configurations:

1. contain the expected Android package name;
2. contain a non-empty Firebase `project_id`;
3. use three distinct Firebase project IDs;
4. are valid base64-encoded JSON documents.

A missing secret, malformed base64 value, invalid JSON document, wrong package name or repeated project ID stops the release before the AAB is built.

## CI-only configuration

Public pull-request, CodeQL and emulator workflows never build the real production variant.

They use `debug`, `staging` and `releaseSmoke`. When an environment-specific non-production secret is not present and `CI=true`, Gradle creates a deterministic CI configuration with:

- a synthetic CI project ID;
- a synthetic application ID entry matching the selected build type;
- no real AquaLight Firebase project credentials;
- a distinct identity for each non-production environment.

This is a permanent CI isolation mechanism, not a fallback for production. Production configuration generation never accepts a CI identity.

## Source-control prohibition

The following paths are forbidden in Git history:

- `app/google-services.json`
- `app/src/**/google-services.json`

The `.gitignore` policy excludes all `google-services.json` files. The Gradle task `:app:verifyNoLegacyFirebaseConfig` additionally fails when:

- the legacy application-level file exists; or
- Git reports any Firebase configuration file as tracked.

All Firebase preparation tasks depend on this verification.

## Verification tasks

### Non-production isolation

```text
:app:verifyFirebaseNonProductionEnvironmentIsolation
```

Validates Debug, Staging and Release Smoke configuration generation and requires distinct project IDs.

### Production isolation

```text
:app:verifyFirebaseProductionEnvironmentIsolation
```

Requires the real Debug, Staging and Production secrets and verifies project separation. The protected production release script runs this task before `bundleRelease` or `assembleRelease`.

### Runtime dependency policy

```text
:app:verifyFirebaseRuntimePolicy
```

Continues to reject unapproved Firebase runtime modules and now also depends on non-production environment isolation for public CI execution.

## Workflow boundaries

- Android pull-request CI uses `lintReleaseSmoke`, never `lintRelease`.
- CodeQL uses `testReleaseSmokeUnitTest`, `lintReleaseSmoke` and `assembleReleaseSmoke`.
- Emulator integration builds `assembleReleaseSmoke`.
- The controlled tag/release workflow is the only workflow allowed to execute the production release build.
- The production build requires the protected Firebase environment secrets and production signing material in the same protected job.

## Acceptance evidence

This migration is accepted only after a pull-request run demonstrates:

- Android CI passes without repository Firebase files;
- CodeQL compiles and analyzes the minified release-smoke variant;
- emulator preparation builds release smoke with the CI identity;
- `git ls-files` reports no Firebase configuration files;
- the production isolation task fails when any protected secret is absent;
- the production isolation task succeeds with three valid and distinct protected configurations.

# Stage 14 — Firebase Environment Contract

Status: implementation complete; CI evidence pending  
Integration branch: `commercial/14-final-rc-validation`

## Commercial rule

AquaLight does not read Firebase configuration from a repository file. No compatibility fallback, legacy lookup, dual-read path or source-controlled `google-services.json` is permitted.

Every Firebase configuration is materialized at build execution time from an environment-scoped base64 value. The only exception is a deterministic, non-routable CI identity used by explicitly non-production variants when public CI runs without application secrets.

## Environment mapping

| Build type | Application ID | Environment | Configuration source | Production access |
|---|---|---|---|---|
| `debug` | `com.aqua.aqualight.debug` | Debug | `AQL_FIREBASE_DEBUG_CONFIG_BASE64` or deterministic CI identity | Forbidden |
| `staging` | `com.aqua.aqualight.staging` | Staging | `AQL_FIREBASE_STAGING_CONFIG_BASE64` or deterministic CI identity | Forbidden |
| `releaseSmoke` | `com.aqua.aqualight` | CI release smoke | `AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64` or deterministic CI identity | Forbidden |
| `release` | `com.aqua.aqualight` | Production | `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64` only | Required |

The `releaseSmoke` variant is non-debuggable, minified and resource-shrunk. It validates release behavior without receiving the production Firebase identity.

## Protected production requirements

The `production-release` GitHub Environment must provide:

- `AQL_FIREBASE_DEBUG_CONFIG_BASE64`
- `AQL_FIREBASE_STAGING_CONFIG_BASE64`
- `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64`

Before release packaging, the build verifies that the decoded configurations:

1. are valid base64-encoded JSON documents;
2. contain the expected Android package name;
3. contain a non-empty Firebase `project_id`;
4. use three distinct Debug, Staging and Production project IDs.

A missing secret, malformed value, wrong package or repeated project ID stops the release before the AAB is built.

## CI separation

### Public validation

Public pull-request and integration validation use:

- Debug
- Staging
- Release Smoke

Public CI does not receive `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64`. The non-production identities are deterministic synthetic configurations and cannot route to a real AquaLight Firebase project.

### Blocking release validation

The controlled tag/release CodeQL gate continues to build and analyze the real minified Release variant using the protected Production configuration. Its exact release commit, SARIF evidence and critical/high alert blocking behavior remain unchanged.

The final signing job runs only inside `production-release` and validates Debug, Staging and Production project separation before `bundleRelease`.

## Source-control prohibition

The following paths are forbidden:

- `app/google-services.json`
- `app/src/**/google-services.json`

`.gitignore` excludes all `google-services.json` files. Gradle task `:app:verifyNoLegacyFirebaseConfig` additionally fails if a legacy file exists or Git reports any Firebase configuration as tracked.

## Verification tasks

### Non-production isolation

```text
:app:verifyFirebaseNonProductionEnvironmentIsolation
```

Generates and verifies distinct Debug, Staging and Release Smoke identities.

### Production isolation

```text
:app:verifyFirebaseProductionEnvironmentIsolation
```

Requires real Debug, Staging and Production inputs and verifies package and project separation.

### Runtime dependency policy

```text
:app:verifyFirebaseRuntimePolicy
```

Continues to reject unapproved Firebase runtime modules and depends on the non-production isolation gate in public CI.

## Contract evidence

`tools/verify_firebase_environment_contract.sh` proves both sides of the contract without using a real production credential:

- missing protected Production configuration is rejected;
- no Firebase configuration is tracked;
- non-production identity isolation succeeds;
- three valid synthetic contract configurations are accepted;
- all three project IDs are distinct.

The workflow stores logs and `contract-evidence.json` for 90 days.

## Completion condition

This migration is complete only when the same integration commit passes:

- Firebase Environment Contract
- Android CI
- Dependency Integrity
- CodeQL Security Scan
- API 27 instrumentation and release smoke
- API 36 instrumentation and release smoke

Real Firebase project creation, secret installation and final production release activation remain protected infrastructure operations. They do not reintroduce repository fallback or compatibility code.

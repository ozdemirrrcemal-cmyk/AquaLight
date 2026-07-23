# AquaLight commercial release and supply-chain controls

## Purpose

This document is the operational contract for publishing the Android application. Production artifacts are no longer built from an ordinary push to `main`. A stable SemVer tag, protected production environment approval, passing quality gates, and verified signing/Firebase material are all required before GitHub creates or updates a Release.

## Release flow

1. Merge an approved pull request into `main` only after the `Android Commercial CI`, CodeQL, and Firebase rules checks pass.
2. Select the intended commit on `main` and create the stable tag `vMAJOR.MINOR.PATCH`.
3. Push the tag. The `Android Controlled Release` workflow derives:
   - `versionName = MAJOR.MINOR.PATCH`
   - `versionCode = MAJOR × 1,000,000 + MINOR × 1,000 + PATCH`
4. The workflow runs, in order: guard → lint/Detekt → unit tests/coverage → instrumentation/release smoke → signed release build.
5. The final job enters the GitHub `production` environment. Configure required reviewers so approval is explicit and auditable.
6. After approval, CI validates production Firebase configuration, the release keystore checksum and alias, the OTA public key, and then builds both a signed AAB and signed APK.
7. CI generates a CycloneDX JSON SBOM, `SHA256SUMS`, provenance and SBOM attestations, and uploads all files to the matching GitHub Release.

The manual workflow entry is only an idempotent recovery path for an existing stable tag. It does not accept an arbitrary branch or create a release from an untagged commit.

## Version policy

Only exact stable tags matching `vMAJOR.MINOR.PATCH` are accepted. Pre-release suffixes and mutable labels such as `latest` are rejected.

The versionCode formula preserves SemVer ordering while remaining deterministic. `MINOR` and `PATCH` are limited to `0..999`; the final value must remain within Android's `2,100,000,000` limit. Never reuse or move a published tag. If a Play Console upload has occurred, the next release must use a strictly higher SemVer tag.

## Environment isolation

| Android variant | Application ID | Firebase source | Intended use |
|---|---|---|---|
| `debug` | `com.aqua.aqualight.debug` | `app/src/debug/google-services.json` demo project | Local development and unit/instrumentation CI |
| `staging` | `com.aqua.aqualight.staging` | `app/src/staging/google-services.json` demo project until a managed staging project is provisioned | Minified non-production validation |
| `releaseSmoke` | `com.aqua.aqualight` | `app/src/releaseSmoke/google-services.json` isolated demo project | Minified production-shape smoke tests without production credentials |
| `release` | `com.aqua.aqualight` | Materialized at runtime from `FIREBASE_CONFIG_PRODUCTION_BASE64` | Play Store and controlled APK release |

The committed non-production files intentionally use Firebase `demo-` project IDs. They provide deterministic Google Services resources but cannot access production. A real staging Firebase project may replace the staging demo only after its ownership, IAM, API-key restrictions, OAuth clients, Firestore rules, and retention controls have been approved. Do not copy production configuration into a non-production source set.

The historical `app/google-services.json` is removed. Production configuration is written with mode `0600` inside the protected runner and deleted in the final cleanup step.

## Required production environment secrets

Configure the following only on the GitHub `production` environment:

| Secret | Requirement |
|---|---|
| `FIREBASE_CONFIG_PRODUCTION_BASE64` | Base64 of the production `google-services.json`; package must be `com.aqua.aqualight` and project ID must not start with `demo-` |
| `RELEASE_KEYSTORE_BASE64` | Base64 of the Play signing/upload keystore used by this workflow |
| `RELEASE_KEYSTORE_SHA256` | Lower- or upper-case SHA-256 digest of the decoded keystore |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Existing alias validated with `keytool` before Gradle runs |
| `RELEASE_KEY_PASSWORD` | Private-key password |
| `AQL_OTA_MANIFEST_PUBLIC_KEY_PEM` | PEM public key used to verify OTA manifests |
| `AQL_OTA_MANIFEST_KEY_ID` | Must equal `aql-ota-manifest-2026-01` until a separately reviewed key rotation changes the contract |

Production environment policy should require at least one reviewer, prevent self-approval where the organization plan supports it, restrict deployment branches/tags to stable release tags, and limit who may edit secrets.

## Quality gates

Android lint runs without a `checkOnly` restriction. Existing findings are captured in `app/lint-baseline.xml`; new findings fail CI because warnings are treated as errors.

Detekt uses `config/detekt/detekt.yml` and `config/detekt/baseline.xml`. The baseline records existing debt and must not be regenerated merely to make a pull request green. New Kotlin findings fail CI.

JaCoCo publishes HTML and XML reports. Initial package-level line coverage floors are:

- `com.aqua.aqualight.data.auth`: 10%
- `com.aqua.aqualight.i18n`: 40%

These floors are migration baselines, not targets. Raise them when covered code increases; lowering them requires an explicit review rationale in the pull request.

## Dependency trust

All resolvable Gradle configurations use strict dependency locking. `gradle.lockfile` files are committed. Gradle dependency verification uses SHA-256 metadata in `gradle/verification-metadata.xml`.

When intentionally changing Gradle dependencies:

```bash
./gradlew :app:dependencies \
  --write-locks \
  --write-verification-metadata sha256
```

Review the lock and verification diffs alongside the declared dependency change. Unexpected repositories, artifacts, checksums, or broad metadata churn are release blockers.

Dependabot checks Gradle, GitHub Actions, and Firebase test npm dependencies weekly. GitHub Action references remain pinned to full commit SHAs; a human-readable version comment may accompany the SHA. `tools/verify_action_pins.py` prevents mutable refs from entering CI.

## Artifacts and verification

A successful release contains:

- `AquaLight-VERSION.aab`
- `AquaLight-VERSION.apk`
- `AquaLight-VERSION-sbom.cdx.json`
- `SHA256SUMS`
- `production-release-build.log`

Verify checksums after download:

```bash
sha256sum --check SHA256SUMS
```

Verify GitHub artifact attestations with the GitHub CLI and the repository identity:

```bash
gh attestation verify AquaLight-VERSION.aab --repo ozdemirrrcemal-cmyk/AquaLight
gh attestation verify AquaLight-VERSION.apk --repo ozdemirrrcemal-cmyk/AquaLight
```

## Operational controls and rollback

- Never force-update or delete a published release tag.
- Never upload a locally built production artifact when CI can build from the tag.
- If a release fails before publication, correct the code or environment configuration and rerun the existing tag only when no artifact was published. Otherwise create the next patch tag.
- If a signing, Firebase, or OTA key may be compromised, stop releases, rotate the affected credential in its owning platform, update the protected environment, and document the incident before resuming.
- The Play Console remains the authority for rollout percentage, staged rollout pause, and rollback decisions. GitHub Release success does not by itself authorize a Play production rollout.

## External provisioning still required

Repository code cannot create or approve Firebase ownership/IAM, GitHub production-environment reviewers, Play Console signing enrollment, or Play release permissions. Those controls must be completed by an authorized administrator and evidenced outside the repository before the first commercial tag is published.

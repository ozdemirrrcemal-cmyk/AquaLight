# Stage 13 — Commercial Release, CI and Supply-Chain Migration Plan

Status: execution started  
Owner: AquaLight engineering  
Target branch: `main`  
Execution branch: `agent/stage-13-release-ci-supply-chain`  
Plan date: 2026-07-23

## 1. Executive objective

Stage 13 converts AquaLight's Android delivery process from a development-oriented build pipeline into a controlled commercial release system.

The target state is a release chain in which:

- a push to `main` validates the product but never publishes a commercial release;
- an immutable, validated semantic-version tag is the only production release trigger;
- Android version identity is deterministic and monotonically increasing;
- guard, lint, unit-test, instrumentation and release-build gates execute in that order;
- Play Store artifacts are signed, traceable and reproducible enough for commercial audit;
- release assets carry checksums, dependency evidence and provenance;
- Firebase and signing material are isolated by environment and protected by GitHub environments;
- dependency and workflow updates are automated but review-controlled.

This stage is a release-governance change. It must not alter runtime product behavior, account isolation, device provisioning, OTA trust, Firebase privacy policy or the architecture boundaries already recorded in the repository.

## 2. Current-state findings

The migration starts from the following repository facts:

- `app/build.gradle` currently fixes `versionCode` at `1` and `versionName` at `1.0.0`.
- The release workflow currently runs on every push to `main` as well as manual dispatch.
- The release workflow currently builds a signed APK but not a Play Store AAB.
- Release build execution currently precedes release lint and unit tests.
- Android lint is restricted to `UnusedResources` by `checkOnly`.
- No Detekt or ktlint gate is present.
- No JaCoCo coverage report or package-level coverage threshold is present.
- No Gradle dependency lock state or dependency-verification metadata is present.
- No Dependabot or Renovate policy is present.
- Most GitHub Actions are referenced by mutable major-version tags.
- Release checksums, SBOM and GitHub artifact provenance are not produced.
- A production Firebase configuration is tracked at application root; debug, staging and production are not isolated.
- Release signing values are consumed directly from environment variables and the keystore is decoded into the repository working tree.
- Instrumentation validation exists for API 27 and API 35, but is not a required predecessor of the release build in one orchestrated release chain.

## 3. Non-negotiable commercial controls

1. Production release tags use the exact format `vMAJOR.MINOR.PATCH`.
2. `versionName` is the tag without the leading `v`.
3. `versionCode` is deterministic: `MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`.
4. `MINOR` and `PATCH` must be lower than `1000`; the resulting code must be in Android's valid positive integer range.
5. A release tag must represent a version newer than the previous production tag.
6. `main` pushes may build debug/release candidates for validation, but may not publish commercial artifacts.
7. Production artifacts are created only after guard, lint, unit-test and instrumentation gates pass.
8. Production signing material is available only to the production release job and must never be written inside the repository tree.
9. Every third-party GitHub Action reference is pinned to a full commit SHA, with the human-readable version retained in a comment.
10. Every production release publishes at least an AAB, SHA-256 checksums and provenance. APK remains an additional distribution artifact.
11. Existing critical/high architecture, privacy, authentication, provisioning and OTA controls may not be bypassed to make the pipeline green.
12. A failing mandatory gate blocks publication; there is no `continue-on-error` path for release acceptance.

## 4. Target release architecture

```text
Pull request / main push
  └─ guard
      └─ Android lint + Kotlin static analysis
          └─ unit tests + coverage verification
              └─ instrumentation / release-smoke tests
                  └─ non-publishing validation build

Production tag vMAJOR.MINOR.PATCH
  └─ validate tag and derive version metadata
      └─ guard
          └─ Android lint + Kotlin static analysis
              └─ unit tests + coverage verification
                  └─ API 27 / API 35 instrumentation and release-smoke
                      └─ production signing preflight
                          └─ signed AAB + optional APK
                              └─ signature verification
                                  └─ checksums + SBOM + provenance
                                      └─ immutable GitHub Release assets
```

## 5. Work packages and delivery order

### WP-13.1 — Release identity and controlled trigger

Deliverables:

- semantic release-tag parser and validation tests;
- deterministic `versionName` and monotonic `versionCode` derivation;
- Gradle property/environment injection for release identity;
- removal of production release publication from `main` push;
- tag-only production release trigger.

Acceptance:

- `v1.2.3` produces `versionName=1.2.3` and `versionCode=1002003`;
- malformed, non-increasing or out-of-range tags fail before Gradle build execution;
- local/debug builds retain safe defaults when no release metadata is supplied.

### WP-13.2 — Commercial artifact and signing gate

Deliverables:

- signed `bundleRelease` AAB for Play Store;
- separately produced signed release APK;
- release-secret presence, base64, keystore, alias and password preflight;
- keystore written only under runner temporary storage with restrictive permissions;
- AAB/APK signature verification;
- versioned artifact naming and mapping-file retention.

Acceptance:

- missing or invalid signing material fails closed before production build;
- production AAB and APK are non-empty, signed and version-aligned;
- no keystore remains after the job.

### WP-13.3 — Ordered quality gates

Deliverables:

- release orchestration standardized as `guard → lint → unit test → instrumentation → release build`;
- instrumentation workflow made reusable by the production release workflow;
- standard Android lint re-enabled by removing the `UnusedResources`-only restriction;
- report upload retained on failures.

Acceptance:

- release build cannot start when any predecessor job fails;
- API 27 and API 35 instrumentation remain mandatory;
- lint debt is either remediated or explicitly baselined with an owner and removal plan; it is not silently ignored.

### WP-13.4 — Kotlin static analysis and coverage ratchet

Deliverables:

- Detekt or ktlint integration;
- machine-readable and human-readable reports;
- JaCoCo unit-test coverage report;
- package-level minimum thresholds for critical application, authentication, provisioning and owner-isolation code;
- baseline/ratchet strategy for brownfield debt.

Commercial policy:

- the first committed threshold must be derived from a measured baseline, not guessed;
- the threshold may stay flat during remediation but may not decrease without an approved engineering decision record;
- newly changed critical code must not reduce package coverage.

### WP-13.5 — Dependency integrity and update governance

Deliverables:

- Gradle dependency locking for resolvable production/test configurations;
- committed `gradle/verification-metadata.xml` with checksum verification;
- CI verification that lock and metadata files are current;
- Dependabot configuration for Gradle, GitHub Actions and Firebase npm dependencies;
- grouped minor/patch updates and separately reviewable major updates.

Acceptance:

- an unverified or unexpectedly changed dependency fails resolution;
- lockfile drift is visible in pull requests;
- automation never merges dependency changes without normal required checks.

### WP-13.6 — Supply-chain evidence

Deliverables:

- SHA-256 manifest for all release assets;
- CycloneDX or SPDX SBOM covering the shipped Android artifact;
- GitHub build provenance/attestation for AAB and APK;
- immutable release asset publication.

Acceptance:

- checksums can be verified independently after download;
- SBOM and provenance refer to the exact published artifact digests;
- release assets are generated by the tagged commit, not copied from an unrelated workflow run.

### WP-13.7 — Firebase environment isolation

External prerequisite: separate Firebase Android applications/projects and approved configuration files for debug, staging and production.

Deliverables:

- explicit `debug`, `staging` and `production` environment model;
- distinct application identifiers where required;
- environment-specific `google-services.json` materialization from protected secrets or approved source-set files;
- no production Firebase configuration used by debug or staging builds;
- CI guard that rejects missing, duplicated or cross-environment Firebase project IDs/package names.

Acceptance:

- debug/staging activity cannot write to production Auth or Firestore;
- production configuration is available only to production jobs;
- local setup is documented without committing confidential operational material.

### WP-13.8 — Repository governance and production approval

Manual repository administration:

- create a protected GitHub environment named `production`;
- require an authorized reviewer for production deployment;
- restrict production secrets to that environment;
- protect `main` with required status checks and pull-request review;
- protect `v*` tags from deletion/rewriting where the GitHub plan supports rulesets;
- disallow force-push on `main` and release tags.

These controls cannot be completed by source changes alone and require repository-administrator configuration.

## 6. Execution slices

### Slice 1 — Release foundation

Scope:

- migration plan;
- release metadata parser and tests;
- Gradle version injection;
- tag-only release workflow;
- ordered quality/instrumentation/release jobs;
- signed AAB and APK;
- signing preflight;
- SHA-256 checksums;
- provenance;
- GitHub Action SHA pinning;
- Dependabot policy;
- full Android lint activation.

Exit condition: a draft pull request exposes the complete release-foundation diff and CI results without publishing a production release.

### Slice 2 — Quality and dependency integrity

Scope:

- measured lint/static-analysis baseline;
- Detekt or ktlint ratchet;
- JaCoCo reporting and critical-package thresholds;
- Gradle lockfiles and dependency verification metadata;
- lock/verification drift guard;
- SBOM generation.

Exit condition: all quality and dependency gates are blocking, documented and green.

### Slice 3 — Environment and governance closure

Scope:

- debug/staging/production Firebase provisioning;
- source-set or secret-based configuration materialization;
- production GitHub environment and reviewer gate;
- branch/tag rulesets;
- first rehearsal tag and rollback drill.

Exit condition: a release-candidate rehearsal produces verified, non-production-distributed assets and the commercial release runbook is signed off.

## 7. Stage 13 control matrix

| Requirement | Initial state | Target owner | Planned slice | Exit evidence |
|---|---|---|---:|---|
| Automatic `versionCode` | Static `1` | Android/CI | 1 | tag parser test + AAB manifest |
| Tag-derived `versionName` | Static `1.0.0` | Android/CI | 1 | Gradle output + artifact name |
| Controlled release trigger | `main` push publishes | CI | 1 | tag-only workflow |
| Signed Play AAB | Missing | Android/Release | 1 | signed `.aab` |
| Optional APK | Present but ungoverned | Android/Release | 1 | signed versioned `.apk` |
| Standard Android lint | `UnusedResources` only | Android | 1–2 | full lint report |
| Detekt/ktlint | Missing | Android | 2 | static-analysis report |
| Coverage report | Missing | Android | 2 | JaCoCo XML/HTML |
| Critical coverage threshold | Missing | Architecture owners | 2 | blocking verification task |
| Dependency locking | Missing | Build engineering | 2 | committed lock state |
| Verification metadata | Missing | Build engineering | 2 | committed verification metadata |
| Dependabot/Renovate | Missing | Repository owner | 1 | automation policy |
| Action SHA pinning | Mostly mutable tags | CI/Security | 1 | full-SHA workflow references |
| Artifact checksum | Missing | Release | 1 | `SHA256SUMS` |
| SBOM | Missing | Security/Release | 2 | CycloneDX/SPDX file |
| Provenance | Missing | Security/Release | 1 | GitHub attestation |
| Firebase by environment | Single production config | Firebase owner | 3 | three isolated configurations |
| Debug/staging/production | Not separated | Android/Firebase | 3 | validated variant matrix |
| Signing-secret hardening | Basic environment use | Repository admin/CI | 1 & 3 | preflight + protected environment |
| Ordered CI gates | Release build too early | CI | 1 | job dependency graph |

## 8. Risk register

| Risk | Commercial impact | Mitigation |
|---|---|---|
| Full lint activation reveals legacy debt | Release branch initially red | expose debt in draft PR; remediate or create time-bounded baseline, never disable checks |
| Version formula receives malformed tag | Play upload collision/rejection | strict parser, range checks and previous-tag comparison |
| Production keystore secret is malformed | Release failure or wrong signing identity | fail-fast decode, alias lookup and signature verification |
| Reusable instrumentation increases release duration | Slower release lead time | retain concurrency cancellation, cache safely, never skip mandatory API matrix |
| Action SHA becomes stale | Security/compatibility exposure | Dependabot GitHub Actions updates with normal review and CI |
| Dependency verification metadata is incomplete | Build resolution failure | generate from a clean trusted runner, review diff, then enable strict enforcement |
| Debug/staging reuse production Firebase | Production data contamination | separate Firebase apps/projects before enabling environment variants |
| Public workflow publishes unintended tag | Unauthorized release | protected tag ruleset, production environment reviewer and exact tag grammar |

## 9. Rollback and recovery

- Source rollback is performed by reverting the Stage 13 pull request; production tags are never moved or force-updated.
- A failed release run may be re-run for the same immutable tag. A rebuilt artifact must retain the same `versionName`; if `versionCode` or source changes are needed, create a new semantic patch tag.
- A GitHub Release created with incorrect assets is marked as withdrawn and replaced by a new patch release; published Play Store `versionCode` values are never reused.
- Production signing credentials are rotated if logs, artifacts or workflow behavior indicate possible exposure.
- Firebase environment separation is rolled out only after export/backup and access-rule verification; production configuration is not deleted during the migration.

## 10. Definition of done

Stage 13 is complete only when all of the following are true:

- all control-matrix rows have objective repository or platform evidence;
- required checks are green on the final pull request;
- no production release occurs from a `main` push;
- a rehearsal tag passes the complete ordered gate chain;
- the resulting AAB/APK signatures, checksums, SBOM and provenance are independently verified;
- production Firebase and signing secrets are protected by environment approval;
- branch and tag governance is enabled;
- the release runbook and rollback procedure have been exercised once.

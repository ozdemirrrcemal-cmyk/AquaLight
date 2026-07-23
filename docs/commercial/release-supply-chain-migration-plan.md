# AquaLight Commercial Release and Supply-Chain Migration Plan

## Objective

Replace the current direct `main`-push release path with an auditable, tag-driven Android release process that produces signed Play Store artifacts only after all commercial quality and supply-chain gates pass.

## Delivery gates

1. **Version and environment contract**
   - Derive `versionName` from an approved SemVer release tag.
   - Derive a deterministic, monotonically increasing Android `versionCode`.
   - Separate debug, staging, release-smoke and production application environments.
   - Provision Firebase configuration from environment-specific secrets; never track `google-services.json`.

2. **Static quality gate**
   - Run all standard Android lint checks instead of only `UnusedResources`.
   - Treat new lint warnings as failures, with a reviewed baseline for existing debt.
   - Run Detekt with a reviewed baseline and immutable reports.

3. **Test and coverage gate**
   - Run unit tests and generate Kover XML/HTML reports.
   - Enforce measured minimum line coverage for selected business-critical packages/classes.
   - Run API 27 and API 35 instrumentation plus minified release-smoke tests.

4. **Supply-chain gate**
   - Commit Gradle dependency locks and SHA-256 verification metadata.
   - Enable Dependabot for Gradle, GitHub Actions and Firebase npm maintenance.
   - Pin every external GitHub Action to a full commit SHA.
   - Generate CycloneDX SBOM, SHA-256 release checksums and signed GitHub artifact attestations.

5. **Controlled publication gate**
   - Run production release only for validated `vMAJOR.MINOR.PATCH` (or approved prerelease) tags whose commit is contained in `main`.
   - Validate signing secrets and the expected keystore alias before invoking Gradle.
   - Build signed AAB and APK, then publish a GitHub Release only after guard, lint, unit, instrumentation and release-build jobs succeed.

## Required sequence

`guard -> lint + detekt -> unit test + coverage -> instrumentation -> signed release build -> checksum/SBOM/provenance -> GitHub Release`

## Rollout

The migration is introduced on `agent/commercial-release-supply-chain`. A one-time bootstrap workflow generates lint/Detekt baselines, dependency evidence and the initial coverage report from the real repository graph. Those generated files are reviewed and committed, the bootstrap workflow is removed, and the final CI/release workflows are then validated on the pull request.

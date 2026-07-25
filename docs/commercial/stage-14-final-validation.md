# AquaLight — Stage 14 Final Commercial Validation Plan

**Status:** In progress  
**Working branch:** `commercial/14-final-validation`  
**Base branch:** `main`

## Commercial implementation rules

- All Stage 14 work will continue on this single branch.
- No parallel Stage 14 branch will be created.
- No temporary workaround, compatibility fallback, patch-only solution or dual-path implementation is allowed.
- Every code change must be permanent, fail-closed and suitable for commercial release.
- A step is marked complete only after its required build, lint, test and evidence checks pass.
- Physical-device-only checks remain final operational release gates and are not represented as completed by emulator-only evidence.

## Scope excluded from code migration

The following items require physical-device, operational or legal validation and are not implemented as application-code changes in this stage:

- Physical phone reboot validation
- Real camera, BLE and notification permanent-denial scenarios
- Physical Wi-Fi or power-loss testing
- Real TalkBack user validation
- Privacy and Terms legal approval
- Final signed release candidate end-to-end validation on a real device

## Required implementation order

### 1. Establish the single Stage 14 branch and validation contract

- [x] Confirm `main` as the only base branch.
- [x] Create exactly one Stage 14 working branch: `commercial/14-final-validation`.
- [x] Record the scope, exclusions, commercial rules and implementation order in this document.
- [x] Make no application, Gradle, Firebase or CI code changes in this step.

Acceptance criteria:

- The branch exists directly from `main`.
- Only this document differs from `main`.
- No pull request is opened in this step.

### 2. Make Firebase production configuration environment-scoped and fail-closed

- [ ] Remove repository and legacy production configuration fallback.
- [ ] Separate debug, staging, release-smoke and production identities.
- [ ] Require protected production configuration for real release builds.
- [ ] Reject missing, malformed, wrong-package or duplicate Firebase project configurations.
- [ ] Prohibit tracked `google-services.json` files.
- [ ] Validate all affected Gradle tasks and CI workflows before completion.

### 3. Add a machine-readable Stage 14 validation policy

- [ ] Define required API levels, suites, blocker thresholds and artifacts.
- [ ] Add fail-closed policy schema validation.
- [ ] Use the policy as the source of truth for final evidence.

### 4. Standardize guard, unit-test and coverage gates

- [ ] Run architecture and policy guards first.
- [ ] Run dependency integrity before build resolution.
- [ ] Run Detekt and Android Lint.
- [ ] Run all required debug, staging and release unit tests.
- [ ] Generate JaCoCo reports and enforce critical-package thresholds.

### 5. Add a full Android Lint blocker gate

- [ ] Preserve normal baseline regression control.
- [ ] Run final validation without hiding blockers behind the baseline.
- [ ] Require zero Fatal and Error findings for required variants.
- [ ] Publish remaining warnings separately.

### 6. Make CodeQL a blocking release prerequisite

- [ ] Bind CodeQL to the controlled release chain.
- [ ] Wait for result processing.
- [ ] Enforce the configured SARIF severity policy.
- [ ] Prevent release build and signing when the security gate fails.

### 7. Standardize the emulator matrix to API 27 and API 36

- [ ] Keep minimum-supported API 27.
- [ ] Run the current target API 36.
- [ ] Execute required instrumentation and minified release-smoke suites on both.

### 8. Complete clean-install automation

- [ ] Install the minified candidate on a clean emulator.
- [ ] Verify deterministic first start.
- [ ] Verify no private owner, tank, assignment, credential or preference state exists.
- [ ] Reject startup crash or ANR evidence.

### 9. Add application upgrade and over-install validation

- [ ] Build and install a lower-version baseline with the same signing identity.
- [ ] Seed deterministic data.
- [ ] Install the candidate with a higher versionCode.
- [ ] Verify supported data preservation or explicit migration.
- [ ] Verify stale runtime and credential state is not restored.

### 10. Add end-to-end rapid account-switch validation

- [ ] Transition rapidly from Owner A through signed-out state to Owner B.
- [ ] Verify Owner A resources close.
- [ ] Verify Owner B resources open exactly once.
- [ ] Verify delayed Owner A work cannot affect Owner B.
- [ ] Verify no cross-owner data projection.

### 11. Complete process recreation, rotation and force-stop validation

- [ ] Recreate authenticated application state.
- [ ] Rotate representative Tank and Care flows.
- [ ] Force-stop and restart the application.
- [ ] Verify durable state survives and runtime-only state is reconstructed.
- [ ] Verify stale owner state is not revived.

### 12. Promote Tank and Care Task corruption checks into a named release suite

- [ ] Run corruption recovery as an explicit Stage 14 suite.
- [ ] Cover truncated data, invalid values, owner mismatch and orphan tasks where required.
- [ ] Preserve fail-closed recovery and recovery telemetry assertions.
- [ ] Publish independent machine-readable evidence.

### 13. Add WebSocket closure and account-cleanup integration validation

- [ ] Verify current socket closure clears current runtime proof.
- [ ] Verify delayed old-socket events cannot clear a newer session.
- [ ] Verify logout and account deletion close owner-scoped runtime resources.
- [ ] Verify duplicate active runtimes cannot exist.

### 14. Assemble accessibility, blocker and final release-candidate evidence

- [ ] Run Light, Dark, 200% font, LTR and RTL profiles.
- [ ] Add deterministic automated accessibility scanning.
- [ ] Require zero open critical, high and release-blocker issues.
- [ ] Build the production-signed minified AAB and optional APK.
- [ ] Verify mapping, signatures, checksums, SBOM and provenance.
- [ ] Generate final JSON and Markdown evidence summaries.
- [ ] Allow publication only when every automated requirement passes.

## Required pipeline order

`guard → dependency integrity → lint/Detekt → unit test/coverage → CodeQL → instrumentation API 27/36 → clean install → upgrade install → release signing/build → checksum → SBOM/provenance → final evidence → publication`

## Completion rule

Stage 14 is code-complete only when every included requirement passes on the same commit and its evidence is retained. The device-menu architecture and Light, Cooling, Timer and Dosing development sequence may begin only after this stage is complete.

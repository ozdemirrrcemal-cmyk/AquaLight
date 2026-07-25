# Stage 14 — Final Commercial Validation

Status: In progress  
Branch: `agent/stage-14-final-commercial-validation`  
Base branch: `main`  
Started: 2026-07-25

## Objective

Complete the code, automated-test and CI evidence required before the AquaLight commercial release candidate is accepted. Stage 13 release and supply-chain controls are treated as existing infrastructure and are strengthened only where Stage 14 requires a fail-closed final gate.

Device-menu development and firmware feature menus are not part of this stage.

## Scope boundary

### Included

- Gradle, Firebase environment and release configuration
- GitHub Actions quality and release gates
- Unit, instrumentation and release-smoke tests
- Clean-install and upgrade-install automation
- Session, process recreation, corruption and WebSocket cleanup validation
- Accessibility automation that can run deterministically on emulators
- Machine-readable final release evidence

### Manual or physical-device validation excluded from this branch

- Physical phone reboot validation
- Real camera, BLE and notification permanent-denial flows
- Physical Wi-Fi or power-loss testing
- Real TalkBack user validation
- Privacy and Terms legal approval
- Final signed RC end-to-end validation on a real device

These items remain final operational release gates, but they are not implemented as application-code changes in this branch.

## Required implementation order

### 1. Bootstrap the Stage 14 validation contract

- [x] Create the Stage 14 branch from `main`.
- [x] Record scope, exclusions, implementation order and acceptance criteria.
- [ ] Add machine-readable Stage 14 evidence identifiers.

Acceptance: the branch and this contract provide the single source of truth for Stage 14 work.

### 2. Make production Firebase configuration fail closed

- [ ] Remove tracked `app/google-services.json` production fallback.
- [ ] Require `AQL_FIREBASE_PRODUCTION_CONFIG_BASE64` for real release builds.
- [ ] Keep explicitly marked CI-only placeholders for non-production validation variants.
- [ ] Verify distinct debug, staging and production Firebase project IDs.
- [ ] Verify generated Firebase configuration is not committed.

Acceptance: a production release cannot be built from a repository Firebase file or without protected production configuration.

### 3. Add a machine-readable Stage 14 policy

- [ ] Add `config/stage14/final-validation.json`.
- [ ] Define required API levels, test suites, lint severities, security results, release artifacts and blocker limits.
- [ ] Add a policy parser that fails closed on missing or malformed requirements.

Acceptance: final validation requirements are data-driven instead of being distributed across shell scripts.

### 4. Standardize guard, unit-test and coverage gates

- [ ] Run architecture and policy guards first.
- [ ] Run dependency integrity before build/test resolution.
- [ ] Run Detekt and Android Lint.
- [ ] Run all required debug, staging and release unit tests.
- [ ] Generate JaCoCo reports.
- [ ] Enforce critical-package line and branch thresholds.

Acceptance: one named CI gate proves all required unit tests and coverage checks passed.

### 5. Add a full Android Lint blocker gate

- [ ] Preserve normal regression lint with the approved baseline.
- [ ] Add final-validation lint without the baseline.
- [ ] Parse Debug, Staging and Release XML reports.
- [ ] Fail when any Fatal or Error blocker exists.
- [ ] Publish the remaining warning inventory separately.

Acceptance: final validation reports zero Android Lint blockers without hiding them behind the baseline.

### 6. Make CodeQL a blocking release prerequisite

- [ ] Convert CodeQL into a reusable or release-dependent workflow job.
- [ ] Wait for CodeQL result processing.
- [ ] Validate SARIF output and severity policy.
- [ ] Prevent release signing/build when the CodeQL gate fails.
- [ ] Include CodeQL evidence in the Stage 14 package.

Acceptance: a security finding above the configured threshold blocks the release workflow.

### 7. Standardize the emulator matrix to API 27 and API 36

- [ ] Keep minimum-supported API 27 validation.
- [ ] Replace the current latest API 35 release gate with API 36.
- [ ] Run required instrumentation and minified release-smoke suites on both API levels.
- [ ] Keep API 35 only as optional compatibility coverage if retained.

Acceptance: the same release commit passes on minimum API 27 and current target API 36.

### 8. Complete clean-install automation

- [ ] Install the minified release-smoke APK on a clean emulator.
- [ ] Verify the deterministic first-start marker.
- [ ] Verify no previous owner, tank, assignment, credential or preference state exists.
- [ ] Scan startup logs for crashes and ANRs.
- [ ] Preserve uninstall/private-data clearing validation.

Acceptance: a genuinely clean installation launches successfully and contains no restored private state.

### 9. Add application upgrade/over-install validation

- [ ] Build a lower-version baseline APK with the same CI signing identity.
- [ ] Install the baseline and create deterministic local fixtures.
- [ ] Install the candidate with `adb install -r` and a higher versionCode.
- [ ] Verify supported data remains readable or is migrated.
- [ ] Verify stale credentials and runtime-only state are not restored.
- [ ] Support previous-release-tag artifacts after the first commercial release.

Acceptance: the candidate can be installed over the baseline without data loss, crash or stale runtime leakage.

### 10. Add end-to-end rapid account-switch validation

- [ ] Open Owner A session and owner-scoped resources.
- [ ] Transition rapidly through unauthenticated state to Owner B.
- [ ] Verify Owner A graph, stores and runtime close.
- [ ] Verify Owner B graph opens exactly once.
- [ ] Verify delayed Owner A callbacks cannot mutate Owner B state.
- [ ] Verify no Owner A data appears in Owner B projections.

Acceptance: rapid account switching settles exclusively on the newest owner and fails closed during transition.

### 11. Complete process-death, rotation and force-stop validation

- [ ] Recreate the authenticated activity/session.
- [ ] Rotate representative Tank and Care screens.
- [ ] Force-stop and restart the application.
- [ ] Recreate during a pending operation.
- [ ] Verify stale owner state is not revived.
- [ ] Verify runtime-only BLE/WebSocket objects are reconstructed rather than restored.

Acceptance: supported state survives recreation while runtime and stale session state do not.

### 12. Promote Tank and Care Task corruption tests into an explicit release suite

- [ ] Run Tank and Care protobuf corruption recovery tests as a named Stage 14 suite.
- [ ] Include truncated-file, invalid-value, owner-mismatch and orphan-task fixtures where missing.
- [ ] Preserve fail-closed recovery and recovery-telemetry assertions.
- [ ] Preserve tank-delete process-death rollback and stale-writer rejection tests.
- [ ] Publish independent XML and JSON evidence.

Acceptance: corruption handling is explicitly visible in final release evidence rather than only inside the general instrumentation run.

### 13. Add WebSocket closure and account-cleanup integration validation

- [ ] Verify an authenticated socket close clears its current runtime proof.
- [ ] Verify a delayed old-socket close cannot clear a newer session.
- [ ] Verify logout closes active runtime resources.
- [ ] Verify account deletion clears owner-scoped runtime resources.
- [ ] Verify Owner A callbacks cannot affect Owner B.
- [ ] Verify duplicate active runtime sessions cannot exist.

Acceptance: WebSocket lifecycle and owner-session cleanup are validated in one integration chain.

### 14. Assemble accessibility, blocker and final RC evidence

- [ ] Run Light and Dark profiles.
- [ ] Run 200% font-scale profiles.
- [ ] Run LTR and RTL profiles.
- [ ] Add deterministic automated accessibility scanning.
- [ ] Query open `severity:critical`, `severity:high` and `release-blocker` issues and require zero.
- [ ] Build the production-signed minified AAB and optional APK.
- [ ] Verify mapping, signatures, checksums, SBOM and provenance.
- [ ] Generate `stage14-final-evidence.json` and a Markdown summary.
- [ ] Allow final release publication only when every automated Stage 14 requirement passes.

Acceptance: one fail-closed workflow result states whether the code and CI portion of the commercial release candidate is ready.

## Enforced pipeline order

`guard → dependency integrity → lint/Detekt → unit test/coverage → CodeQL → instrumentation API 27/36 → install/upgrade smoke → release signing/build → checksum → SBOM/provenance → final evidence → publication`

## Completion rule

Stage 14 is code-complete only when all included checkboxes pass on the same commit and the generated evidence package is retained. Physical-device and legal checks remain separate final operational gates and must still be completed before commercial publication.

After the full Stage 14 gate is complete, work may proceed to device-menu architecture and the Light, Cooling, Timer and Dosing menu sequence.

# AquaLight — Stage 14 Commercial Validation and Artifact Closure

**Repository status:** implementation complete; PR validation and merge pending

**Working branch:** `commercial/14-final-validation`

**Base branch:** `main`

**Distribution in this stage:** verified GitHub Actions artifacts only

## Purpose

Stage 14 proves that the current application can be built, tested, signed and
handed off as a traceable APK+AAB pair. It does not claim that unfinished device
menus are product-complete and it does not upload anything to Google Play.

The release contract is build once, test that signed candidate, and finalize the
same bytes without rebuilding or re-signing.

## Blocking policy

Only the following block Stage 14:

- repository architecture and security guards;
- dependency integrity, Android Lint, Detekt, unit tests and configured coverage;
- CodeQL critical/high threshold;
- API 27 and API 36 instrumentation, clean-install and upgrade-install evidence;
- a production-signed, minified APK and AAB from the same tagged commit;
- exact signing certificate, mapping and SHA-256 identity;
- physical acceptance of the signed APK’s currently implemented critical path;
- byte-identical final artifact verification.

SBOM, provenance and machine-readable evidence remain in the existing pipeline
because they are already automated. They are not a substitute for physical
testing.

Full TalkBack review, final Privacy Policy/Terms approval, Play Console Data
Safety/App Content declarations, store listing, testing tracks and rollout are
future product-release gates. They become relevant after the device menus, UI and
data flows are complete.

## Completed repository implementation

### Configuration and policy

- [x] Production Firebase configuration is protected, environment-scoped and
  fail-closed.
- [x] Debug, staging, release-smoke and production identities are isolated.
- [x] Tracked or fallback production `google-services.json` files are rejected.
- [x] `config/commercial/stage14-validation-policy.json` is the machine-readable
  source of truth.
- [x] The policy pins minSdk 27, targetSdk 36 and the API 27/36 emulator matrix.
- [x] APK and AAB are both mandatory Stage 14 candidate artifacts.
- [x] Google Play upload is explicitly outside the Stage 14 pipeline.

### Automated validation

- [x] Architecture and product-policy guards run before commercial packaging.
- [x] Dependency integrity is verified.
- [x] Baseline-free blocker Lint and zero-new-debt Detekt are enforced.
- [x] Debug, staging, release-smoke and release unit tests run.
- [x] Critical-package JaCoCo thresholds are enforced.
- [x] CodeQL critical/high findings block the controlled release.
- [x] API 27 and API 36 run instrumentation and minified release-smoke tests.
- [x] Clean install, same-signer upgrade, process recreation, force-stop,
  account switching, permission denial, corruption recovery and runtime cleanup
  generate machine-readable evidence.
- [x] Light, dark, 200% font and RTL profiles generate deterministic evidence.

### Signed candidate and finalization

- [x] The release workflow has separate `candidate` and `finalize` phases.
- [x] The candidate phase builds the production-signed minified APK and AAB once.
- [x] Candidate APK, AAB, mapping, certificate and SHA-256 identities are recorded
  in `CANDIDATE.json`.
- [x] Candidate checksums, SBOM and provenance are generated before handoff.
- [x] The immutable candidate is archived under
  `AquaLight-Candidate-vMAJOR.MINOR.PATCH`.
- [x] Finalization requires the exact successful candidate workflow run ID.
- [x] Protected physical acceptance is bound to the candidate manifest, signing
  certificate and APK/AAB/mapping digests.
- [x] The finalize phase contains no Gradle, build, signing or attestation task.
- [x] Finalization rehashes the downloaded candidate and copies the same bytes into
  the final archive.
- [x] Final JSON/Markdown evidence and `RELEASE.json` use the
  `approved-for-archive` decision.
- [x] The final package is archived under
  `AquaLight-Final-vMAJOR.MINOR.PATCH`.
- [x] No Google Play upload action exists in the workflow.

## Required order

### Repository integration

1. Make this single implementation commit.
2. Require all PR checks on that exact commit to pass.
3. Merge PR #103 into `main`.

After the implementation commit is green, merge is the only remaining repository
action. Do not create the tag from the PR branch.

### Post-merge Stage 14 operational closure

1. Tag the exact merged `main` commit as `v0.14.0`.
2. Run the `candidate` phase; tag push may start it automatically.
3. Download `AquaLight-Candidate-v0.14.0` and retain its workflow run ID.
4. Test the signed APK on a physical phone:
   - clean install and first launch;
   - login, logout and owner isolation;
   - force-stop, relaunch and reboot;
   - permission denial and connectivity interruption;
   - currently implemented critical path end to end.
5. Complete schema-v2 manual acceptance from the candidate manifest and store it
   as the protected `AQL_STAGE14_MANUAL_ACCEPTANCE_BASE64` secret.
6. Dispatch `finalize` with `release_tag=v0.14.0` and the successful candidate
   workflow run ID.
7. Require `AquaLight-Final-v0.14.0` verification to pass.

If the candidate changes for any reason, create a new candidate run and repeat
physical acceptance. Never edit an accepted artifact or replace it with a rebuild.

## Completion rule

Stage 14 is complete only when:

- PR #103 is green and merged;
- the tag points to the merged `main` commit;
- the production-signed APK and AAB candidate succeeds;
- the physical acceptance summary matches that candidate;
- finalization succeeds without rebuilding; and
- the final archive contains the same APK/AAB/mapping digests.

Device-menu implementation begins after this operational closure. Full
accessibility, legal and store-release closure occurs later, against the completed
product rather than the current partial UI.

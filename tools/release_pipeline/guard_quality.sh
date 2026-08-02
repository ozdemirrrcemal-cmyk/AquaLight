#!/usr/bin/env bash
set -Eeuo pipefail

release_tag="${AQL_RELEASE_TAG:-}"
if [[ ! "$release_tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Release tag must use canonical vMAJOR.MINOR.PATCH format: $release_tag" >&2
  exit 1
fi

git fetch --force --tags origin
git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main
release_commit="$(git rev-list -n 1 "$release_tag")"
[[ "$release_commit" =~ ^[0-9a-f]{40}$ ]]
git merge-base --is-ancestor "$release_commit" origin/main
[[ "$(git rev-parse HEAD)" == "$release_commit" ]]

{
  echo "release-tag=$release_tag"
  echo "release-version=${release_tag#v}"
  echo "release-commit=$release_commit"
  echo "include-apk=true"
} >> "$GITHUB_OUTPUT"

mkdir -p release-quality
for name in \
  AQL_FIREBASE_DEBUG_CONFIG_BASE64 \
  AQL_FIREBASE_STAGING_CONFIG_BASE64 \
  AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64 \
  AQL_FIREBASE_PRODUCTION_CONFIG_BASE64; do
  value="${!name:-}"
  if [[ -z "${value//[[:space:]]/}" ]]; then
    echo "Required protected Firebase input is missing: ${name}" >&2
    exit 1
  fi
done

./gradlew help --no-daemon --stacktrace \
  2>&1 | tee release-quality/firebase-gradle-configuration.log

python3 tools/verify_stage14_policy.py \
  --policy config/commercial/stage14-validation-policy.json \
  --app-gradle app/build.gradle \
  --emulator-workflow .github/workflows/android_emulator_tests.yml \
  --release-workflow .github/workflows/android_release.yml \
  --summary release-quality/stage14-policy-validation.json
python3 -m unittest discover \
  -s tools/tests \
  -p 'test_*.py'

for guard in \
  architecture_guard.py \
  session_startup_guard.py \
  composition_root_guard.py \
  ui_dependency_construction_guard.py \
  device_application_boundary_guard.py \
  device_root_application_boundary_guard.py \
  tank_device_assignment_boundary_guard.py \
  aquarium_application_boundary_guard.py \
  care_application_boundary_guard.py \
  provisioning_discovery_boundary_guard.py \
  provisioning_progress_boundary_guard.py \
  provisioning_commit_recovery_guard.py \
  navigation_guard.py \
  ws_protocol_guard.py \
  firmware_interoperability_guard.py \
  ws_v1_commercial_closure_guard.py \
  permission_architecture_guard.py \
  notification_reminder_architecture_guard.py \
  process_safe_feedback_guard.py \
  stage9_feedback_media_guard.py \
  firebase_telemetry_guard.py \
  privacy_legal_guard.py \
  design_system_resource_guard.py \
  localization_accessibility_guard.py; do
  python3 "tools/$guard"
done

python3 tools/verify_dependency_integrity.py \
  --lockfile app/gradle.lockfile \
  --metadata gradle/verification-metadata.xml \
  --summary release-quality/dependency-integrity.json

mkdir -p ~/.android
if [[ ! -s ~/.android/debug.keystore ]]; then
  keytool -genkeypair -noprompt \
    -keystore ~/.android/debug.keystore \
    -storepass android \
    -alias AndroidDebugKey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storetype JKS \
    -dname "CN=Android,O=AquaLight,C=TR"
fi

# Release lint and unit-test variants use an ephemeral CI-only key. Production
# signing material is unavailable until the later environment-protected job.
rm -f release-key.jks
keytool -genkeypair -noprompt \
  -keystore release-key.jks \
  -storepass aqualight-ci \
  -alias aqualight-ci \
  -keypass aqualight-ci \
  -keyalg RSA \
  -keysize 2048 \
  -validity 1 \
  -dname "CN=AquaLight CI Quality,O=AquaLight,C=TR"
export RELEASE_KEYSTORE_PASSWORD=aqualight-ci
export RELEASE_KEY_ALIAS=aqualight-ci
export RELEASE_KEY_PASSWORD=aqualight-ci

./gradlew \
  :app:verifyFirebaseConfigurationContract \
  :app:verifyFirebaseEnvironmentIsolation \
  :app:verifyFirebaseRuntimePolicy \
  :app:processDebugGoogleServices \
  :app:processStagingGoogleServices \
  :app:processReleaseSmokeGoogleServices \
  :app:processReleaseGoogleServices \
  --no-daemon \
  --stacktrace \
  2>&1 | tee release-quality/firebase-configuration.log

test -s app/build/reports/firebase/configuration-contract.json
test -s app/build/reports/firebase/environment-isolation.json
test -s app/src/debug/google-services.json
test -s app/src/staging/google-services.json
test -s app/src/releaseSmoke/google-services.json
test -s app/src/release/google-services.json

mkdir -p release-quality/firebase
cp app/build/reports/firebase/configuration-contract.json \
  release-quality/firebase/configuration-contract.json
cp app/build/reports/firebase/environment-isolation.json \
  release-quality/firebase/environment-isolation.json

./gradlew -PAQL_FINAL_LINT=true \
  :app:verifyDetektPolicy \
  :app:lintDebug \
  :app:lintStaging \
  :app:lintReleaseSmoke \
  :app:lintRelease \
  --continue \
  --no-daemon \
  --stacktrace \
  2>&1 | tee release-quality/lint-detekt.log

for report in \
  app/build/reports/lint-results-debug.xml \
  app/build/reports/lint-results-staging.xml \
  app/build/reports/lint-results-releaseSmoke.xml \
  app/build/reports/lint-results-release.xml \
  app/build/reports/lint-results-detekt.xml \
  app/build/reports/lint-results-detekt-advisory.xml \
  app/build/reports/detekt/detekt.sarif \
  app/build/reports/detekt/detekt-advisory.sarif \
  app/build/reports/stage14/detekt-policy-summary.json; do
  test -s "$report"
done

python3 tools/verify_android_lint.py \
  --report app/build/reports/lint-results-debug.xml \
  --report app/build/reports/lint-results-staging.xml \
  --report app/build/reports/lint-results-releaseSmoke.xml \
  --report app/build/reports/lint-results-release.xml \
  --summary release-quality/android-lint-summary.json

./gradlew \
  :app:createDebugUnitTestCoverageReport \
  :app:testStagingUnitTest \
  :app:testReleaseSmokeUnitTest \
  :app:testReleaseUnitTest \
  --no-daemon \
  --stacktrace \
  2>&1 | tee release-quality/unit-test-coverage.log

for suite in \
  testDebugUnitTest \
  testStagingUnitTest \
  testReleaseSmokeUnitTest \
  testReleaseUnitTest; do
  result_directory="app/build/test-results/${suite}"
  test -d "$result_directory"
  test -n "$(find "$result_directory" -type f -name 'TEST-*.xml' -size +0c -print -quit)"
done

mkdir -p release-quality/stage14-evidence
unit_reports=(
  --report "debug=app/build/test-results/testDebugUnitTest"
  --report "staging=app/build/test-results/testStagingUnitTest"
  --report "release-smoke=app/build/test-results/testReleaseSmokeUnitTest"
  --report "release=app/build/test-results/testReleaseUnitTest"
)
for evidence_set in \
  accessibility-unit \
  permission-permanent-denial-unit \
  process-recreation-unit \
  rapid-account-switch-unit \
  tank-care-corruption-unit \
  websocket-account-cleanup-unit; do
  python3 tools/verify_stage14_junit_evidence.py \
    --contract config/commercial/stage14-junit-evidence-contract.json \
    --evidence-set "$evidence_set" \
    "${unit_reports[@]}" \
    --commit "$release_commit" \
    --summary "release-quality/stage14-evidence/${evidence_set}.json"
done

coverage_dir="app/build/reports/coverage/test/debug"
test -s "${coverage_dir}/report.xml"
test -s "${coverage_dir}/index.html"
grep -q '<report' "${coverage_dir}/report.xml"
python3 tools/verify_jacoco_coverage.py \
  --report "${coverage_dir}/report.xml" \
  --policy config/coverage/critical-packages.json \
  --summary "${coverage_dir}/critical-package-thresholds.json"

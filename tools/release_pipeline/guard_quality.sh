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

include_apk="false"
[[ "${AQL_INCLUDE_APK:-false}" == "true" ]] && include_apk="true"
{
  echo "release-tag=$release_tag"
  echo "release-version=${release_tag#v}"
  echo "release-commit=$release_commit"
  echo "include-apk=$include_apk"
} >> "$GITHUB_OUTPUT"

mkdir -p release-quality
for guard in \
  architecture_guard.py \
  composition_root_guard.py \
  ui_dependency_construction_guard.py \
  session_startup_guard.py \
  navigation_guard.py \
  ws_protocol_guard.py \
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

# Public/tag quality validation uses the minified non-production release-smoke
# variant. The real production Release variant and Firebase identity remain
# exclusive to the later production-release environment-protected job.
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
  :app:detekt \
  :app:lintDebug \
  :app:lintStaging \
  :app:lintReleaseSmoke \
  --continue \
  --no-daemon \
  --stacktrace \
  2>&1 | tee release-quality/lint-detekt.log

for report in \
  app/build/reports/lint-results-debug.xml \
  app/build/reports/lint-results-staging.xml \
  app/build/reports/lint-results-releaseSmoke.xml \
  app/build/reports/lint-results-detekt.xml \
  app/build/reports/detekt/detekt.sarif; do
  test -s "$report"
done

./gradlew \
  :app:createDebugUnitTestCoverageReport \
  :app:testReleaseSmokeUnitTest \
  --no-daemon \
  --stacktrace

coverage_dir="app/build/reports/coverage/test/debug"
test -s "${coverage_dir}/report.xml"
test -s "${coverage_dir}/index.html"
grep -q '<report' "${coverage_dir}/report.xml"
python3 tools/verify_jacoco_coverage.py \
  --report "${coverage_dir}/report.xml" \
  --policy config/coverage/critical-packages.json \
  --summary "${coverage_dir}/critical-package-thresholds.json"

#!/usr/bin/env bash
set -Eeuo pipefail

release_smoke_config="${AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64:-}"
if [[ -z "${release_smoke_config//[[:space:]]/}" ]]; then
  echo "AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64 is required." >&2
  exit 1
fi

smoke_apk_path_file="release-smoke-apk-path.txt"
upgrade_baseline_apk_path_file="release-smoke-upgrade-baseline-apk-path.txt"
mkdir -p release-quality
rm -f "$smoke_apk_path_file"
rm -f "$upgrade_baseline_apk_path_file"

candidate_version_code="${AQL_VERSION_CODE:-${GITHUB_RUN_NUMBER:-}}"
if [[ ! "$candidate_version_code" =~ ^[1-9][0-9]*$ ]]; then
  echo "AQL_VERSION_CODE or GITHUB_RUN_NUMBER must provide a positive integer." >&2
  exit 1
fi
if (( candidate_version_code < 2 )); then
  echo "Stage 14 upgrade validation requires candidate versionCode >= 2." >&2
  exit 1
fi
baseline_version_code=$((candidate_version_code - 1))

for token in \
  connectedDebugAndroidTest \
  verify_uninstall_clears_data.sh \
  RELEASE_SMOKE_PASS \
  ReleaseSmokeActivity \
  CleanInstallSmokeActivity \
  verify_clean_install_evidence.py \
  CLEAN_INSTALL_PASS \
  UpgradeInstallSmokeActivity \
  verify_upgrade_install_evidence.py \
  UPGRADE_INSTALL_CANDIDATE_PASS \
  "adb install -r" \
  AccountDeletionProcessDeathSmokeActivity \
  ACCOUNT_DELETION_PROCESS_DEATH_PASS \
  auth-confirmed-before-checkpoint \
  aqua_smoke_profile \
  large-font-light \
  rtl-light; do
  grep -Fq "$token" tools/run_release_smoke.sh
done

rm -f release-key.jks
keytool -genkeypair -noprompt \
  -keystore release-key.jks \
  -storepass aqualight-ci \
  -alias aqualight-ci \
  -keypass aqualight-ci \
  -keyalg RSA \
  -keysize 2048 \
  -validity 1 \
  -dname "CN=AquaLight CI,O=AquaLight,C=TR"

RELEASE_KEYSTORE_PASSWORD=aqualight-ci \
RELEASE_KEY_ALIAS=aqualight-ci \
RELEASE_KEY_PASSWORD=aqualight-ci \
  AQL_VERSION_CODE="$baseline_version_code" \
  ./gradlew clean assembleReleaseSmoke --no-daemon --stacktrace \
    2>&1 | tee release-quality/assemble-release-smoke-upgrade-baseline.log

baseline_smoke_apk="$(
  find app/build/outputs/apk/releaseSmoke -type f -name '*.apk' -print -quit
)"
test -n "$baseline_smoke_apk"
test -s "$baseline_smoke_apk"
install -m 0644 "$baseline_smoke_apk" release-quality/upgrade-baseline.apk
printf '%s\n' "release-quality/upgrade-baseline.apk" \
  > "$upgrade_baseline_apk_path_file"
test -s "$upgrade_baseline_apk_path_file"

RELEASE_KEYSTORE_PASSWORD=aqualight-ci \
RELEASE_KEY_ALIAS=aqualight-ci \
RELEASE_KEY_PASSWORD=aqualight-ci \
  AQL_VERSION_CODE="$candidate_version_code" \
  ./gradlew clean assembleReleaseSmoke --no-daemon --stacktrace \
    2>&1 | tee release-quality/assemble-release-smoke.log

smoke_apk="$(find app/build/outputs/apk/releaseSmoke -type f -name '*.apk' -print -quit)"
test -n "$smoke_apk"
test -s "$smoke_apk"
printf '%s\n' "$smoke_apk" > "$smoke_apk_path_file"
test -s "$smoke_apk_path_file"
if cmp -s release-quality/upgrade-baseline.apk "$smoke_apk"; then
  echo "Upgrade baseline and candidate APKs must not be byte-identical." >&2
  exit 1
fi

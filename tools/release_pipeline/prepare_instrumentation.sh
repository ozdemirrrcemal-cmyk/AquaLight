#!/usr/bin/env bash
set -Eeuo pipefail

release_smoke_config="${AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64:-}"
if [[ -z "${release_smoke_config//[[:space:]]/}" ]]; then
  echo "AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64 is required." >&2
  exit 1
fi

smoke_apk_path_file="release-smoke-apk-path.txt"
mkdir -p release-quality
rm -f "$smoke_apk_path_file"

for token in \
  connectedDebugAndroidTest \
  verify_uninstall_clears_data.sh \
  RELEASE_SMOKE_PASS \
  ReleaseSmokeActivity \
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
  ./gradlew assembleReleaseSmoke --no-daemon --stacktrace \
    2>&1 | tee release-quality/assemble-release-smoke.log

smoke_apk="$(find app/build/outputs/apk/releaseSmoke -type f -name '*.apk' -print -quit)"
test -n "$smoke_apk"
test -s "$smoke_apk"
printf '%s\n' "$smoke_apk" > "$smoke_apk_path_file"
test -s "$smoke_apk_path_file"

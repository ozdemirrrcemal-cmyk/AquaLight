#!/usr/bin/env bash
set -euo pipefail

readonly APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
readonly REQUESTED_APPLICATION_ID="${2:-}"
readonly TEST_PATHS=(
  "files/uninstall-marker"
  "files/datastore/known_devices.pb"
  "files/datastore/tank_device_assignments.pb"
  "shared_prefs/device_credentials.xml"
)

resolve_application_id() {
  local apk_path="$1"
  local requested_application_id="$2"
  local resolved_application_id=""

  if [[ -n "$requested_application_id" ]]; then
    resolved_application_id="$requested_application_id"
  else
    local apkanalyzer_bin=""
    if command -v apkanalyzer >/dev/null 2>&1; then
      apkanalyzer_bin="$(command -v apkanalyzer)"
    elif [[ -x "${ANDROID_HOME:-}/cmdline-tools/latest/bin/apkanalyzer" ]]; then
      apkanalyzer_bin="${ANDROID_HOME}/cmdline-tools/latest/bin/apkanalyzer"
    fi

    if [[ -n "$apkanalyzer_bin" ]]; then
      resolved_application_id="$(
        "$apkanalyzer_bin" manifest application-id "$apk_path" 2>/dev/null \
          | tail -n 1 \
          | tr -d '\r\n'
      )"
    fi

    if [[ -z "$resolved_application_id" ]]; then
      local aapt_bin=""
      if command -v aapt >/dev/null 2>&1; then
        aapt_bin="$(command -v aapt)"
      elif [[ -d "${ANDROID_HOME:-}/build-tools" ]]; then
        aapt_bin="$(
          find "${ANDROID_HOME}/build-tools" -maxdepth 2 -type f -name aapt -perm -u+x 2>/dev/null \
            | sort -V \
            | tail -n 1
        )"
      fi

      if [[ -n "$aapt_bin" ]]; then
        resolved_application_id="$(
          "$aapt_bin" dump badging "$apk_path" 2>/dev/null \
            | sed -n "s/^package: name='\([^']*\)'.*/\1/p" \
            | head -n 1 \
            | tr -d '\r\n'
        )"
      fi
    fi
  fi

  if [[ ! "$resolved_application_id" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
    echo "Could not resolve a valid application ID from APK: ${apk_path}" >&2
    exit 1
  fi

  printf '%s\n' "$resolved_application_id"
}

if [[ ! -s "${APK_PATH}" ]]; then
  echo "Debug APK is missing: ${APK_PATH}" >&2
  exit 1
fi

readonly APPLICATION_ID="$(resolve_application_id "$APK_PATH" "$REQUESTED_APPLICATION_ID")"
echo "Verifying uninstall data clearing for ${APPLICATION_ID}."

adb wait-for-device
adb install -r "${APK_PATH}" >/dev/null

adb shell "run-as ${APPLICATION_ID} sh -c 'mkdir -p files/datastore shared_prefs'"
for path in "${TEST_PATHS[@]}"; do
  adb shell "run-as ${APPLICATION_ID} sh -c 'printf aqualight-uninstall-test > ${path}'"
  adb shell "run-as ${APPLICATION_ID} sh -c 'test -s ${path}'"
done

adb uninstall "${APPLICATION_ID}" >/dev/null
adb install "${APK_PATH}" >/dev/null

for path in "${TEST_PATHS[@]}"; do
  if adb shell "run-as ${APPLICATION_ID} sh -c 'test -e ${path}'"; then
    echo "Private app data survived uninstall/reinstall: ${path}" >&2
    exit 1
  fi
done

echo "Uninstall/reinstall verification passed: private device data was not restored for ${APPLICATION_ID}."

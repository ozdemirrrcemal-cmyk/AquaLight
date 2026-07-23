#!/usr/bin/env bash
set -euo pipefail

readonly APK_PATH="${1:-app/build/outputs/apk/staging/debug/app-staging-debug.apk}"
readonly APPLICATION_ID="${2:-${AQL_APPLICATION_ID:-com.aqua.aqualight.staging}}"
readonly TEST_PATHS=(
  "files/uninstall-marker"
  "files/datastore/known_devices.pb"
  "files/datastore/tank_device_assignments.pb"
  "shared_prefs/device_credentials.xml"
)

if [[ ! -s "${APK_PATH}" ]]; then
  echo "Debug APK is missing: ${APK_PATH}" >&2
  exit 1
fi

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

echo "Uninstall/reinstall verification passed: private device data was not restored."

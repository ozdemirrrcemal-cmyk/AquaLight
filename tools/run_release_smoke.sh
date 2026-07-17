#!/usr/bin/env bash
set -Eeuo pipefail

API_LEVEL="${1:-unknown}"
PACKAGE_NAME="com.aqua.aqualight"
SMOKE_ACTIVITY="com.aqua.aqualight.smoke.ReleaseSmokeActivity"
SMOKE_COMPONENT="${PACKAGE_NAME}/${SMOKE_ACTIVITY}"
SMOKE_PREFIX="release-smoke"
RUN_LOG="${SMOKE_PREFIX}-run.txt"
WINDOW_DUMP="${SMOKE_PREFIX}-window.xml"
REMOTE_WINDOW_DUMP="/sdcard/${WINDOW_DUMP}"

exec > >(tee "$RUN_LOG") 2>&1

capture_smoke_diagnostics() {
  set +e
  adb shell uiautomator dump "$REMOTE_WINDOW_DUMP" >/dev/null 2>&1
  adb pull "$REMOTE_WINDOW_DUMP" "$WINDOW_DUMP" >/dev/null 2>&1
  adb logcat -d > "${SMOKE_PREFIX}-logcat.txt" 2>&1
  adb shell dumpsys activity activities > "${SMOKE_PREFIX}-activities.txt" 2>&1
  adb shell dumpsys package "$PACKAGE_NAME" > "${SMOKE_PREFIX}-package.txt" 2>&1
  adb shell pm path "$PACKAGE_NAME" > "${SMOKE_PREFIX}-package-path.txt" 2>&1
  set -e
}

finish() {
  status=$?
  trap - EXIT
  capture_smoke_diagnostics
  exit "$status"
}
trap finish EXIT

./gradlew connectedDebugAndroidTest --no-daemon --stacktrace
bash tools/verify_uninstall_clears_data.sh

adb uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true
SMOKE_APK="$(cat release-smoke-apk-path.txt)"
adb install "$SMOKE_APK" 2>&1 | tee "${SMOKE_PREFIX}-install.txt"
adb shell pm path "$PACKAGE_NAME" 2>&1 | tee "${SMOKE_PREFIX}-package-path.txt"
adb logcat -c
adb shell am force-stop "$PACKAGE_NAME"

set +e
adb shell am start -W -S -n "$SMOKE_COMPONENT" \
  2>&1 | tee "${SMOKE_PREFIX}-start.txt"
START_STATUS=${PIPESTATUS[0]}
set -e

if [ "$START_STATUS" -ne 0 ]; then
  echo "Smoke Activity could not be started on API ${API_LEVEL}."
  exit "$START_STATUS"
fi

rm -f "$WINDOW_DUMP"
for attempt in $(seq 1 40); do
  adb shell uiautomator dump "$REMOTE_WINDOW_DUMP" >/dev/null 2>&1 || true
  adb pull "$REMOTE_WINDOW_DUMP" "$WINDOW_DUMP" >/dev/null 2>&1 || true

  if grep -q "RELEASE_SMOKE_PASS" "$WINDOW_DUMP" 2>/dev/null; then
    echo "Minified release smoke passed on API ${API_LEVEL}."
    exit 0
  fi

  if grep -q "RELEASE_SMOKE_FAIL" "$WINDOW_DUMP" 2>/dev/null; then
    echo "Minified release smoke reported an application failure on API ${API_LEVEL}."
    cat "$WINDOW_DUMP" || true
    exit 1
  fi

  sleep 1
done

echo "Minified release smoke timed out on API ${API_LEVEL}."
exit 1

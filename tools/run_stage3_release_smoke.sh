#!/usr/bin/env bash
set -Eeuo pipefail

API_LEVEL="${1:-unknown}"
PACKAGE_NAME="com.aqua.aqualight"
SMOKE_ACTIVITY="com.aqua.aqualight.smoke.Stage3ReleaseSmokeActivity"
SMOKE_COMPONENT="${PACKAGE_NAME}/${SMOKE_ACTIVITY}"
RUN_LOG="stage3-release-smoke-run.txt"

exec > >(tee "$RUN_LOG") 2>&1

capture_smoke_diagnostics() {
  set +e
  adb shell uiautomator dump /sdcard/stage3-release-smoke-window.xml >/dev/null 2>&1
  adb pull /sdcard/stage3-release-smoke-window.xml stage3-release-smoke-window.xml >/dev/null 2>&1
  adb logcat -d > stage3-release-smoke-logcat.txt 2>&1
  adb shell dumpsys activity activities > stage3-release-smoke-activities.txt 2>&1
  adb shell dumpsys package "$PACKAGE_NAME" > stage3-release-smoke-package.txt 2>&1
  adb shell pm path "$PACKAGE_NAME" > stage3-release-smoke-package-path.txt 2>&1
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
adb install "$SMOKE_APK" 2>&1 | tee stage3-release-smoke-install.txt
adb shell pm path "$PACKAGE_NAME" 2>&1 | tee stage3-release-smoke-package-path.txt
adb logcat -c
adb shell am force-stop "$PACKAGE_NAME"

set +e
adb shell am start -W -S -n "$SMOKE_COMPONENT" \
  2>&1 | tee stage3-release-smoke-start.txt
START_STATUS=${PIPESTATUS[0]}
set -e

if [ "$START_STATUS" -ne 0 ]; then
  echo "Smoke Activity could not be started on API ${API_LEVEL}."
  exit "$START_STATUS"
fi

rm -f stage3-release-smoke-window.xml
for attempt in $(seq 1 40); do
  adb shell uiautomator dump /sdcard/stage3-release-smoke-window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/stage3-release-smoke-window.xml stage3-release-smoke-window.xml >/dev/null 2>&1 || true

  if grep -q "STAGE3_RELEASE_SMOKE_PASS" stage3-release-smoke-window.xml 2>/dev/null; then
    echo "Minified release smoke passed on API ${API_LEVEL}."
    exit 0
  fi

  if grep -q "STAGE3_RELEASE_SMOKE_FAIL" stage3-release-smoke-window.xml 2>/dev/null; then
    echo "Minified release smoke reported an application failure on API ${API_LEVEL}."
    cat stage3-release-smoke-window.xml || true
    exit 1
  fi

  sleep 1
done

echo "Minified release smoke timed out on API ${API_LEVEL}."
exit 1

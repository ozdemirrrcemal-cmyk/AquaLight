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
ORIGINAL_FONT_SCALE="$(adb shell settings get system font_scale 2>/dev/null | tr -d '\r' || true)"
ORIGINAL_FONT_SCALE="${ORIGINAL_FONT_SCALE:-1.0}"

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
  adb shell settings put system font_scale "$ORIGINAL_FONT_SCALE" >/dev/null 2>&1 || true
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
rm -rf release-smoke-screens
mkdir -p release-smoke-screens
adb shell rm -rf "/sdcard/Android/data/${PACKAGE_NAME}/files/smoke-screens" >/dev/null 2>&1 || true

run_visual_smoke() {
  variant="$1"
  theme="$2"
  font_scale="$3"
  rtl="$4"
  variant_dump="${SMOKE_PREFIX}-${variant}-window.xml"
  remote_variant_dump="/sdcard/${variant_dump}"

  adb shell settings put system font_scale "$font_scale"
  actual_font_scale="$(adb shell settings get system font_scale | tr -d '\r')"
  if ! awk -v expected="$font_scale" -v actual="$actual_font_scale" \
    'BEGIN { exit ((actual + 0) == (expected + 0) ? 0 : 1) }'; then
    echo "Expected font scale ${font_scale}, found ${actual_font_scale}."
    return 1
  fi

  adb shell am force-stop "$PACKAGE_NAME"
  set +e
  adb shell am start -W -S -n "$SMOKE_COMPONENT" \
    --es aqua_smoke_theme "$theme" \
    --es aqua_smoke_variant "$variant" \
    --ez aqua_smoke_rtl "$rtl" \
    2>&1 | tee "${SMOKE_PREFIX}-${variant}-start.txt"
  start_status=${PIPESTATUS[0]}
  set -e

  if [ "$start_status" -ne 0 ]; then
    echo "Smoke Activity could not be started for ${variant} on API ${API_LEVEL}."
    return "$start_status"
  fi

  rm -f "$variant_dump"
  for attempt in $(seq 1 60); do
    adb shell uiautomator dump "$remote_variant_dump" >/dev/null 2>&1 || true
    adb pull "$remote_variant_dump" "$variant_dump" >/dev/null 2>&1 || true

    if grep -q "RELEASE_SMOKE_PASS:${variant}" "$variant_dump" 2>/dev/null; then
      adb pull "/sdcard/Android/data/${PACKAGE_NAME}/files/smoke-screens/." \
        "release-smoke-screens/" >/dev/null
      screenshot_count="$(find release-smoke-screens -type f -name "${variant}-*.png" | wc -l | tr -d ' ')"
      if [ "$screenshot_count" -ne 4 ]; then
        echo "Expected 4 ${variant} screenshots, found ${screenshot_count}."
        return 1
      fi
      echo "${variant} visual smoke passed with ${screenshot_count} screenshots on API ${API_LEVEL}."
      return 0
    fi

    if grep -q "RELEASE_SMOKE_FAIL" "$variant_dump" 2>/dev/null; then
      echo "Minified release smoke reported an application failure for ${variant} on API ${API_LEVEL}."
      cat "$variant_dump" || true
      return 1
    fi
    sleep 1
  done

  echo "Minified release smoke timed out for ${variant} on API ${API_LEVEL}."
  return 1
}

run_visual_smoke light light 1.0 false
run_visual_smoke dark dark 1.0 false
run_visual_smoke font200 light 2.0 false
run_visual_smoke rtl light 1.0 true
cp "${SMOKE_PREFIX}-rtl-window.xml" "$WINDOW_DUMP"
echo "Minified light/dark/font200/RTL visual smoke passed on API ${API_LEVEL}."

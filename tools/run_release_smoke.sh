#!/usr/bin/env bash
set -Eeuo pipefail

API_LEVEL="${1:-unknown}"
PACKAGE_NAME="com.aqua.aqualight"
SMOKE_ACTIVITY="com.aqua.aqualight.smoke.ReleaseSmokeActivity"
SMOKE_COMPONENT="${PACKAGE_NAME}/${SMOKE_ACTIVITY}"
SMOKE_PREFIX="release-smoke"
PASS_MARKER="RELEASE_SMOKE_PASS"
RUN_LOG="${SMOKE_PREFIX}-run.txt"
WINDOW_DUMP="${SMOKE_PREFIX}-window.xml"
REMOTE_WINDOW_DUMP="/sdcard/${WINDOW_DUMP}"
ORIGINAL_FONT_SCALE="$(adb shell settings get system font_scale 2>/dev/null | tr -d '\r')"
ORIGINAL_FORCE_RTL="$(adb shell settings get global debug.force_rtl 2>/dev/null | tr -d '\r')"
ORIGINAL_FORCE_RTL_PROP="$(adb shell getprop debug.force_rtl 2>/dev/null | tr -d '\r')"

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

restore_setting() {
  namespace="$1"
  key="$2"
  value="$3"
  if [ -z "$value" ] || [ "$value" = "null" ]; then
    adb shell settings delete "$namespace" "$key" >/dev/null 2>&1 || true
  else
    adb shell settings put "$namespace" "$key" "$value" >/dev/null 2>&1 || true
  fi
}

restore_device_configuration() {
  restore_setting system font_scale "$ORIGINAL_FONT_SCALE"
  restore_setting global debug.force_rtl "$ORIGINAL_FORCE_RTL"
  adb shell setprop debug.force_rtl "$ORIGINAL_FORCE_RTL_PROP" >/dev/null 2>&1 || true
}

finish() {
  status=$?
  trap - EXIT
  capture_smoke_diagnostics
  restore_device_configuration
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

run_visual_profile() {
  profile="$1"
  theme="$2"
  font_scale="$3"
  force_rtl="$4"
  profile_dump="${SMOKE_PREFIX}-${profile}-window.xml"
  remote_profile_dump="/sdcard/${profile_dump}"

  adb shell settings put system font_scale "$font_scale"
  adb shell settings put global debug.force_rtl "$force_rtl"
  adb shell setprop debug.force_rtl "$force_rtl"
  adb shell am force-stop "$PACKAGE_NAME"

  set +e
  adb shell am start -W -S -n "$SMOKE_COMPONENT" \
    --es aqua_smoke_theme "$theme" \
    --es aqua_smoke_profile "$profile" \
    2>&1 | tee "${SMOKE_PREFIX}-${profile}-start.txt"
  start_status=${PIPESTATUS[0]}
  set -e

  if [ "$start_status" -ne 0 ]; then
    echo "Smoke Activity could not be started for ${profile} on API ${API_LEVEL}."
    return "$start_status"
  fi

  rm -f "$profile_dump"
  for attempt in $(seq 1 50); do
    adb shell uiautomator dump "$remote_profile_dump" >/dev/null 2>&1 || true
    adb pull "$remote_profile_dump" "$profile_dump" >/dev/null 2>&1 || true

    if grep -q "${PASS_MARKER}:${profile}" "$profile_dump" 2>/dev/null; then
      adb pull "/sdcard/Android/data/${PACKAGE_NAME}/files/smoke-screens/." \
        "release-smoke-screens/" >/dev/null
      screenshot_count="$(find release-smoke-screens -type f -name "${profile}-*.png" | wc -l | tr -d ' ')"
      if [ "$screenshot_count" -ne 4 ]; then
        echo "Expected 4 ${profile} screenshots, found ${screenshot_count}."
        return 1
      fi
      echo "${profile} visual smoke passed with ${screenshot_count} screenshots on API ${API_LEVEL}."
      return 0
    fi

    if grep -q "RELEASE_SMOKE_FAIL" "$profile_dump" 2>/dev/null; then
      echo "Minified release smoke reported an application failure for ${profile} on API ${API_LEVEL}."
      cat "$profile_dump" || true
      return 1
    fi
    sleep 1
  done

  echo "Minified release smoke timed out for ${profile} on API ${API_LEVEL}."
  return 1
}

run_visual_profile light light 1.0 0
run_visual_profile dark dark 1.0 0
run_visual_profile large-font-light light 2.0 0
run_visual_profile large-font-dark dark 2.0 0
run_visual_profile rtl-light light 1.0 1
run_visual_profile rtl-dark dark 1.0 1
cp "${SMOKE_PREFIX}-dark-window.xml" "$WINDOW_DUMP"
echo "Minified Light/Dark, 200% font and RTL visual smoke passed on API ${API_LEVEL}."

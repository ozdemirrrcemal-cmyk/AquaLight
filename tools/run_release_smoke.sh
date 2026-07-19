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

if [[ ! "$ORIGINAL_FONT_SCALE" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  ORIGINAL_FONT_SCALE="1.0"
fi

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

restore_emulator_configuration() {
  set +e
  adb shell settings put system font_scale "$ORIGINAL_FONT_SCALE" >/dev/null 2>&1
  adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1
  set -e
}

finish() {
  status=$?
  trap - EXIT
  restore_emulator_configuration
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

mode_key() {
  locale_tag="$1"
  theme="$2"
  font_scale="$3"
  normalized_locale="$(printf '%s' "$locale_tag" | tr '[:upper:]' '[:lower:]' | tr '-' '_')"
  normalized_scale="${font_scale//./_}"
  printf '%s-%s-font%s' "$normalized_locale" "$theme" "$normalized_scale"
}

run_visual_smoke() {
  locale_tag="$1"
  theme="$2"
  font_scale="$3"
  key="$(mode_key "$locale_tag" "$theme" "$font_scale")"
  mode_dump="${SMOKE_PREFIX}-${key}-window.xml"
  remote_mode_dump="/sdcard/${mode_dump}"

  echo "Running visual smoke: API=${API_LEVEL}, locale=${locale_tag}, theme=${theme}, fontScale=${font_scale}"
  adb shell settings put system font_scale "$font_scale"
  adb shell am force-stop "$PACKAGE_NAME"
  sleep 1

  set +e
  adb shell am start -W -S -n "$SMOKE_COMPONENT" \
    --es aqua_smoke_theme "$theme" \
    --es aqua_smoke_locale "$locale_tag" \
    --es aqua_smoke_font_scale "$font_scale" \
    2>&1 | tee "${SMOKE_PREFIX}-${key}-start.txt"
  start_status=${PIPESTATUS[0]}
  set -e

  if [ "$start_status" -ne 0 ]; then
    echo "Smoke Activity could not be started for ${key} on API ${API_LEVEL}."
    return "$start_status"
  fi

  rm -f "$mode_dump"
  for attempt in $(seq 1 60); do
    adb shell uiautomator dump "$remote_mode_dump" >/dev/null 2>&1 || true
    adb pull "$remote_mode_dump" "$mode_dump" >/dev/null 2>&1 || true

    if grep -q "RELEASE_SMOKE_PASS:${key}" "$mode_dump" 2>/dev/null; then
      adb pull "/sdcard/Android/data/${PACKAGE_NAME}/files/smoke-screens/." \
        "release-smoke-screens/" >/dev/null
      screenshot_count="$(find release-smoke-screens -type f -name "${key}-*.png" | wc -l | tr -d ' ')"
      if [ "$screenshot_count" -ne 4 ]; then
        echo "Expected 4 ${key} screenshots, found ${screenshot_count}."
        return 1
      fi
      echo "${key} visual smoke passed with ${screenshot_count} screenshots on API ${API_LEVEL}."
      return 0
    fi

    if grep -q "RELEASE_SMOKE_FAIL" "$mode_dump" 2>/dev/null; then
      echo "Minified release smoke reported an application failure for ${key} on API ${API_LEVEL}."
      cat "$mode_dump" || true
      return 1
    fi
    sleep 1
  done

  echo "Minified release smoke timed out for ${key} on API ${API_LEVEL}."
  return 1
}

run_visual_smoke "en" "light" "1.0"
run_visual_smoke "en" "dark" "1.0"

if [ "$API_LEVEL" -ge 35 ]; then
  run_visual_smoke "en" "light" "2.0"
  run_visual_smoke "en" "dark" "2.0"
  run_visual_smoke "en-XA" "light" "2.0"
  run_visual_smoke "ar-XB" "light" "1.0"
  run_visual_smoke "ar-XB" "dark" "1.0"
fi

latest_dump="$(find . -maxdepth 1 -type f -name "${SMOKE_PREFIX}-*-window.xml" | sort | tail -n 1)"
if [ -n "$latest_dump" ]; then
  cp "$latest_dump" "$WINDOW_DUMP"
fi

echo "Minified localization/accessibility visual smoke passed on API ${API_LEVEL}."

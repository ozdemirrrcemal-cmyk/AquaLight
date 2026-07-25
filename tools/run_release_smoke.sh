#!/usr/bin/env bash
set -Eeuo pipefail

API_LEVEL="${1:-unknown}"
PACKAGE_NAME="com.aqua.aqualight"
SMOKE_ACTIVITY="com.aqua.aqualight.smoke.ReleaseSmokeActivity"
SMOKE_COMPONENT="${PACKAGE_NAME}/${SMOKE_ACTIVITY}"
ACCOUNT_DELETION_SMOKE_ACTIVITY="com.aqua.aqualight.smoke.AccountDeletionProcessDeathSmokeActivity"
ACCOUNT_DELETION_SMOKE_COMPONENT="${PACKAGE_NAME}/${ACCOUNT_DELETION_SMOKE_ACTIVITY}"
SMOKE_PREFIX="release-smoke"
PASS_MARKER="RELEASE_SMOKE_PASS"
ACCOUNT_DELETION_PREPARED_MARKER="ACCOUNT_DELETION_PROCESS_DEATH_PREPARED"
ACCOUNT_DELETION_PASS_MARKER="ACCOUNT_DELETION_PROCESS_DEATH_PASS"
ACCOUNT_DELETION_FAIL_MARKER="ACCOUNT_DELETION_PROCESS_DEATH_FAIL"
RUN_LOG="${SMOKE_PREFIX}-run.txt"
WINDOW_DUMP="${SMOKE_PREFIX}-window.xml"
REMOTE_WINDOW_DUMP="/sdcard/${WINDOW_DUMP}"
ORIGINAL_FONT_SCALE="$(adb shell settings get system font_scale 2>/dev/null | tr -d '\r')"
ORIGINAL_FORCE_RTL="$(adb shell settings get global debug.force_rtl 2>/dev/null | tr -d '\r')"
ORIGINAL_FORCE_RTL_PROP="$(adb shell getprop debug.force_rtl 2>/dev/null | tr -d '\r')"
ORIGINAL_HIDE_ERROR_DIALOGS="$(adb shell settings get global hide_error_dialogs 2>/dev/null | tr -d '\r')"

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
  restore_setting global hide_error_dialogs "$ORIGINAL_HIDE_ERROR_DIALOGS"
  adb shell setprop debug.force_rtl "$ORIGINAL_FORCE_RTL_PROP" >/dev/null 2>&1 || true
}

# Android 16 headless emulator images can occasionally surface a launcher-only ANR
# above the application under test. Dismiss only known system launcher dialogs. An
# AquaLight or unknown process ANR remains a hard failure and is never hidden.
dismiss_known_system_anr() {
  local_dump="$1"
  coordinates="$(python3 - "$local_dump" <<'PY'
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

path = Path(sys.argv[1])
try:
    root = ET.parse(path).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(1)

allowed_titles = (
    "Quickstep isn't responding",
    "System UI isn't responding",
    "Pixel Launcher isn't responding",
    "Launcher3 isn't responding",
)
titles = {
    node.attrib.get("text", "").strip()
    for node in root.iter()
    if node.attrib.get("resource-id") == "android:id/alertTitle"
}
if not any(title in allowed_titles for title in titles):
    raise SystemExit(1)

for node in root.iter():
    if node.attrib.get("resource-id") != "android:id/aerr_close":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match is None:
        continue
    left, top, right, bottom = map(int, match.groups())
    print(f"{(left + right) // 2} {(top + bottom) // 2}")
    raise SystemExit(0)

raise SystemExit(1)
PY
  )" || return 1

  read -r tap_x tap_y <<< "$coordinates"
  [[ "$tap_x" =~ ^[0-9]+$ && "$tap_y" =~ ^[0-9]+$ ]] || return 1
  echo "Dismissing known system launcher ANR at ${tap_x},${tap_y} on API ${API_LEVEL}."
  adb shell input tap "$tap_x" "$tap_y" >/dev/null 2>&1 || return 1
  sleep 1
}

handle_anr_dialog() {
  local_dump="$1"
  if ! grep -Fq 'resource-id="android:id/aerr_close"' "$local_dump" 2>/dev/null; then
    return 1
  fi
  if dismiss_known_system_anr "$local_dump"; then
    return 0
  fi
  echo "Unexpected ANR dialog detected while waiting for AquaLight UI on API ${API_LEVEL}."
  cat "$local_dump" || true
  return 2
}

wait_for_ui_marker() {
  expected_marker="$1"
  local_dump="$2"
  remote_dump="/sdcard/${local_dump}"

  rm -f "$local_dump"
  for attempt in $(seq 1 40); do
    adb shell uiautomator dump "$remote_dump" >/dev/null 2>&1 || true
    adb pull "$remote_dump" "$local_dump" >/dev/null 2>&1 || true

    if grep -Fq "$expected_marker" "$local_dump" 2>/dev/null; then
      return 0
    fi
    if grep -Fq "$ACCOUNT_DELETION_FAIL_MARKER" "$local_dump" 2>/dev/null; then
      cat "$local_dump" || true
      return 1
    fi

    set +e
    handle_anr_dialog "$local_dump"
    anr_status=$?
    set -e
    if [ "$anr_status" -eq 0 ]; then
      continue
    fi
    if [ "$anr_status" -eq 2 ]; then
      return 1
    fi
    sleep 1
  done

  echo "Timed out waiting for UI marker: ${expected_marker}"
  cat "$local_dump" 2>/dev/null || true
  return 1
}

run_account_deletion_process_death_scenario() {
  scenario="$1"
  scenario_prefix="${SMOKE_PREFIX}-account-deletion-${scenario}"
  prepare_dump="${scenario_prefix}-prepare-window.xml"
  resume_dump="${scenario_prefix}-resume-window.xml"

  adb shell am start -W -S -n "$ACCOUNT_DELETION_SMOKE_COMPONENT" \
    --es aqua_account_deletion_action prepare \
    --es aqua_account_deletion_scenario "$scenario" \
    2>&1 | tee "${scenario_prefix}-prepare-start.txt"
  wait_for_ui_marker \
    "${ACCOUNT_DELETION_PREPARED_MARKER}:${scenario}" \
    "$prepare_dump"

  adb shell am force-stop "$PACKAGE_NAME"
  for attempt in $(seq 1 20); do
    if [ -z "$(adb shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r')" ]; then
      break
    fi
    sleep 1
  done
  test -z "$(adb shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r')"

  adb shell am start -W -n "$ACCOUNT_DELETION_SMOKE_COMPONENT" \
    --es aqua_account_deletion_action resume \
    --es aqua_account_deletion_scenario "$scenario" \
    2>&1 | tee "${scenario_prefix}-resume-start.txt"
  wait_for_ui_marker \
    "${ACCOUNT_DELETION_PASS_MARKER}:${scenario}" \
    "$resume_dump"

  echo "Account-deletion process-death scenario passed: ${scenario} (API ${API_LEVEL})."
  adb shell am force-stop "$PACKAGE_NAME"
}

finish() {
  status=$?
  trap - EXIT
  capture_smoke_diagnostics
  restore_device_configuration
  exit "$status"
}
trap finish EXIT

# Suppress platform-owned crash/ANR dialogs from obscuring the app under test.
# AquaLight failures still fail through missing markers, explicit fail markers and log evidence.
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true

./gradlew connectedDebugAndroidTest --no-daemon --stacktrace
DEBUG_APK="$(
  find app/build/outputs/apk/debug -type f -name '*.apk' ! -name '*androidTest*' \
    | sort \
    | head -n 1
)"
test -n "$DEBUG_APK"
test -s "$DEBUG_APK"
bash tools/verify_uninstall_clears_data.sh "$DEBUG_APK"

adb uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true
SMOKE_APK="$(cat release-smoke-apk-path.txt)"
adb install "$SMOKE_APK" 2>&1 | tee "${SMOKE_PREFIX}-install.txt"
adb shell pm path "$PACKAGE_NAME" 2>&1 | tee "${SMOKE_PREFIX}-package-path.txt"
adb logcat -c
rm -rf release-smoke-screens
mkdir -p release-smoke-screens

for deletion_scenario in \
  started \
  cloud-cleared \
  auth-delete-requested \
  auth-confirmed-before-checkpoint \
  account-deleted; do
  run_account_deletion_process_death_scenario "$deletion_scenario"
done
echo "Account-deletion process-death release matrix passed on API ${API_LEVEL}."

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

    set +e
    handle_anr_dialog "$profile_dump"
    anr_status=$?
    set -e
    if [ "$anr_status" -eq 0 ]; then
      continue
    fi
    if [ "$anr_status" -eq 2 ]; then
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

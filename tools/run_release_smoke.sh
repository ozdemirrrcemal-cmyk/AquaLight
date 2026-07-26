#!/usr/bin/env bash
set -Eeuo pipefail

API_LEVEL="${1:-unknown}"
PACKAGE_NAME="com.aqua.aqualight.smoke"
SMOKE_ACTIVITY="com.aqua.aqualight.smoke.ReleaseSmokeActivity"
SMOKE_COMPONENT="${PACKAGE_NAME}/${SMOKE_ACTIVITY}"
ACCOUNT_DELETION_SMOKE_ACTIVITY="com.aqua.aqualight.smoke.AccountDeletionProcessDeathSmokeActivity"
ACCOUNT_DELETION_SMOKE_COMPONENT="${PACKAGE_NAME}/${ACCOUNT_DELETION_SMOKE_ACTIVITY}"
CLEAN_INSTALL_SMOKE_ACTIVITY="com.aqua.aqualight.smoke.CleanInstallSmokeActivity"
CLEAN_INSTALL_SMOKE_COMPONENT="${PACKAGE_NAME}/${CLEAN_INSTALL_SMOKE_ACTIVITY}"
UPGRADE_INSTALL_SMOKE_ACTIVITY="com.aqua.aqualight.smoke.UpgradeInstallSmokeActivity"
UPGRADE_INSTALL_SMOKE_COMPONENT="${PACKAGE_NAME}/${UPGRADE_INSTALL_SMOKE_ACTIVITY}"
SMOKE_PREFIX="release-smoke-api-${API_LEVEL}"
SMOKE_SCREEN_DIR="release-smoke-screens/api-${API_LEVEL}"
PASS_MARKER="RELEASE_SMOKE_PASS"
CLEAN_INSTALL_PASS_MARKER="CLEAN_INSTALL_PASS"
CLEAN_INSTALL_FAIL_MARKER="CLEAN_INSTALL_FAIL"
UPGRADE_BASELINE_PASS_MARKER="UPGRADE_INSTALL_BASELINE_PASS"
UPGRADE_CANDIDATE_PASS_MARKER="UPGRADE_INSTALL_CANDIDATE_PASS"
UPGRADE_INSTALL_FAIL_MARKER="UPGRADE_INSTALL_FAIL"
ACCOUNT_DELETION_PREPARED_MARKER="ACCOUNT_DELETION_PROCESS_DEATH_PREPARED"
ACCOUNT_DELETION_PASS_MARKER="ACCOUNT_DELETION_PROCESS_DEATH_PASS"
ACCOUNT_DELETION_FAIL_MARKER="ACCOUNT_DELETION_PROCESS_DEATH_FAIL"
RUN_LOG="${SMOKE_PREFIX}-run.txt"
WINDOW_DUMP="${SMOKE_PREFIX}-window.xml"
REMOTE_WINDOW_DUMP="/sdcard/${WINDOW_DUMP}"

exec > >(tee "$RUN_LOG") 2>&1

wait_for_unlocked_user() {
  local user_id
  local state="unknown"

  user_id="$(adb shell am get-current-user 2>/dev/null | tr -d '\r')"
  case "$user_id" in
    ''|*[!0-9]*)
      echo "Could not resolve the current Android user: ${user_id:-empty}" >&2
      return 1
      ;;
  esac

  # A boot-complete emulator can still be in Direct Boot. Instrumentation is not
  # direct-boot aware and must not start until credential-encrypted storage exists.
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell am unlock-user "$user_id" >/dev/null 2>&1 || true

  for attempt in $(seq 1 60); do
    state="$(
      adb shell am get-started-user-state "$user_id" 2>/dev/null \
        | tr -d '\r' \
        || true
    )"
    if [ "$state" = "3" ]; then
      echo "Android user ${user_id} is RUNNING_UNLOCKED."
      return 0
    fi
    sleep 1
  done

  echo "Android user ${user_id} did not reach RUNNING_UNLOCKED (state=${state})." >&2
  return 1
}

wait_for_unlocked_user

ORIGINAL_FONT_SCALE="$(adb shell settings get system font_scale 2>/dev/null | tr -d '\r')"
ORIGINAL_FORCE_RTL="$(adb shell settings get global debug.force_rtl 2>/dev/null | tr -d '\r')"
ORIGINAL_FORCE_RTL_PROP="$(adb shell getprop debug.force_rtl 2>/dev/null | tr -d '\r')"

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
    if grep -Fq "$CLEAN_INSTALL_FAIL_MARKER" "$local_dump" 2>/dev/null; then
      cat "$local_dump" || true
      return 1
    fi
    if grep -Fq "$UPGRADE_INSTALL_FAIL_MARKER" "$local_dump" 2>/dev/null; then
      cat "$local_dump" || true
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

  adb logcat -c
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
  adb logcat -d > "${scenario_prefix}-logcat.txt" 2>&1

  echo "Account-deletion process-death scenario passed: ${scenario} (API ${API_LEVEL})."
  adb shell am force-stop "$PACKAGE_NAME"
}

run_upgrade_install_gate() {
  upgrade_prefix="${SMOKE_PREFIX}-upgrade-install"
  baseline_apk="$(cat release-smoke-upgrade-baseline-apk-path.txt)"
  test -s "$baseline_apk"
  test -s "$SMOKE_APK"

  adb shell am force-stop "$PACKAGE_NAME"
  adb uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true
  if adb shell pm list packages --user 0 "$PACKAGE_NAME" 2>/dev/null \
    | tr -d '\r' \
    | grep -Fxq "package:${PACKAGE_NAME}"; then
    echo "Release-smoke package remained installed before upgrade baseline." >&2
    return 1
  fi

  adb install "$baseline_apk" 2>&1 | tee "${upgrade_prefix}-baseline-install.txt"
  adb logcat -c
  adb shell am start -W -S -n "$UPGRADE_INSTALL_SMOKE_COMPONENT" \
    --es aqua_upgrade_install_action seed \
    2>&1 | tee "${upgrade_prefix}-baseline-start.txt"
  set +e
  wait_for_ui_marker \
    "$UPGRADE_BASELINE_PASS_MARKER" \
    "${upgrade_prefix}-baseline-window.xml"
  baseline_marker_status=$?
  set -e
  adb pull \
    "/sdcard/Android/data/${PACKAGE_NAME}/files/stage14/upgrade-install-baseline.json" \
    "${upgrade_prefix}-baseline-activity.json" >/dev/null 2>&1 || true
  adb shell dumpsys package "$PACKAGE_NAME" \
    > "${upgrade_prefix}-baseline-package.txt"
  adb logcat -d > "${upgrade_prefix}-baseline-logcat.txt" 2>&1
  test "$baseline_marker_status" -eq 0

  adb shell am force-stop "$PACKAGE_NAME"
  adb install -r "$SMOKE_APK" \
    2>&1 | tee "${upgrade_prefix}-candidate-install.txt"
  adb logcat -c
  adb shell am start -W -S -n "$UPGRADE_INSTALL_SMOKE_COMPONENT" \
    --es aqua_upgrade_install_action verify \
    2>&1 | tee "${upgrade_prefix}-candidate-start.txt"
  set +e
  wait_for_ui_marker \
    "$UPGRADE_CANDIDATE_PASS_MARKER" \
    "${upgrade_prefix}-candidate-window.xml"
  candidate_marker_status=$?
  set -e
  adb pull \
    "/sdcard/Android/data/${PACKAGE_NAME}/files/stage14/upgrade-install-candidate.json" \
    "${upgrade_prefix}-candidate-activity.json" >/dev/null 2>&1 || true
  adb shell dumpsys package "$PACKAGE_NAME" \
    > "${upgrade_prefix}-candidate-package.txt"
  adb logcat -d > "${upgrade_prefix}-candidate-logcat.txt" 2>&1

  python3 tools/verify_upgrade_install_evidence.py \
    --baseline-evidence "${upgrade_prefix}-baseline-activity.json" \
    --candidate-evidence "${upgrade_prefix}-candidate-activity.json" \
    --baseline-apk "$baseline_apk" \
    --candidate-apk "$SMOKE_APK" \
    --baseline-install-log "${upgrade_prefix}-baseline-install.txt" \
    --candidate-install-log "${upgrade_prefix}-candidate-install.txt" \
    --baseline-launch-log "${upgrade_prefix}-baseline-start.txt" \
    --candidate-launch-log "${upgrade_prefix}-candidate-start.txt" \
    --baseline-window "${upgrade_prefix}-baseline-window.xml" \
    --candidate-window "${upgrade_prefix}-candidate-window.xml" \
    --baseline-package-dump "${upgrade_prefix}-baseline-package.txt" \
    --candidate-package-dump "${upgrade_prefix}-candidate-package.txt" \
    --baseline-logcat "${upgrade_prefix}-baseline-logcat.txt" \
    --candidate-logcat "${upgrade_prefix}-candidate-logcat.txt" \
    --api-level "$API_LEVEL" \
    --commit "$(git rev-parse HEAD)" \
    --summary "stage14-evidence/upgrade-install-api-${API_LEVEL}.json"
  test "$candidate_marker_status" -eq 0
  echo "Upgrade-install release candidate gate passed on API ${API_LEVEL}."
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

mkdir -p stage14-evidence
rm -rf \
  app/build/outputs/androidTest-results/connected \
  app/build/reports/androidTests/connected
./gradlew connectedDebugAndroidTest --rerun-tasks --no-daemon --stacktrace
instrumentation_report_source="app/build/outputs/androidTest-results/connected"
instrumentation_report_archive="stage14-evidence/junit-api-${API_LEVEL}"
test -d "$instrumentation_report_source"
rm -rf "$instrumentation_report_archive"
mkdir -p "$instrumentation_report_archive"
cp -R "${instrumentation_report_source}/." "$instrumentation_report_archive/"
test -n "$(
  find "$instrumentation_report_archive" -type f -name 'TEST-*.xml' -size +0c \
    -print -quit
)"
for evidence_set in \
  accessibility-instrumentation \
  process-recreation-instrumentation \
  tank-care-corruption-instrumentation; do
  python3 tools/verify_stage14_junit_evidence.py \
    --contract config/commercial/stage14-junit-evidence-contract.json \
    --evidence-set "$evidence_set" \
    --report "api-${API_LEVEL}=${instrumentation_report_archive}" \
    --api-level "$API_LEVEL" \
    --commit "$(git rev-parse HEAD)" \
    --summary "stage14-evidence/${evidence_set}-api-${API_LEVEL}.json"
done
DEBUG_APK="$(
  find app/build/outputs/apk/debug -type f -name '*.apk' ! -name '*androidTest*' \
    | sort \
    | head -n 1
)"
test -n "$DEBUG_APK"
test -s "$DEBUG_APK"
bash tools/verify_uninstall_clears_data.sh "$DEBUG_APK"

adb uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true
if adb shell pm list packages --user 0 "$PACKAGE_NAME" 2>/dev/null \
  | tr -d '\r' \
  | grep -Fxq "package:${PACKAGE_NAME}"; then
  echo "Release-smoke package remained installed before the clean-install gate." >&2
  exit 1
fi
SMOKE_APK="$(cat release-smoke-apk-path.txt)"
adb install "$SMOKE_APK" 2>&1 | tee "${SMOKE_PREFIX}-install.txt"
adb shell pm path "$PACKAGE_NAME" 2>&1 | tee "${SMOKE_PREFIX}-package-path.txt"
adb logcat -c
rm -rf "$SMOKE_SCREEN_DIR"
mkdir -p "$SMOKE_SCREEN_DIR"

clean_install_prefix="${SMOKE_PREFIX}-clean-install"
clean_install_activity_evidence="${clean_install_prefix}-activity.json"
clean_install_window="${clean_install_prefix}-window.xml"
clean_install_logcat="${clean_install_prefix}-logcat.txt"
clean_install_start="${clean_install_prefix}-start.txt"
adb shell am start -W -S -n "$CLEAN_INSTALL_SMOKE_COMPONENT" \
  2>&1 | tee "$clean_install_start"
set +e
wait_for_ui_marker "$CLEAN_INSTALL_PASS_MARKER" "$clean_install_window"
clean_install_marker_status=$?
set -e
adb pull \
  "/sdcard/Android/data/${PACKAGE_NAME}/files/stage14/clean-install-activity.json" \
  "$clean_install_activity_evidence" >/dev/null 2>&1 || true
adb logcat -d > "$clean_install_logcat" 2>&1
python3 tools/verify_clean_install_evidence.py \
  --activity-evidence "$clean_install_activity_evidence" \
  --install-log "${SMOKE_PREFIX}-install.txt" \
  --launch-log "$clean_install_start" \
  --window-dump "$clean_install_window" \
  --logcat "$clean_install_logcat" \
  --api-level "$API_LEVEL" \
  --commit "$(git rev-parse HEAD)" \
  --summary "stage14-evidence/clean-install-api-${API_LEVEL}.json"
test "$clean_install_marker_status" -eq 0
echo "Clean-install release candidate gate passed on API ${API_LEVEL}."
adb shell am force-stop "$PACKAGE_NAME"

run_upgrade_install_gate

for deletion_scenario in \
  started \
  cloud-cleared \
  auth-delete-requested \
  auth-confirmed-before-checkpoint \
  account-deleted; do
  run_account_deletion_process_death_scenario "$deletion_scenario"
done
python3 tools/verify_force_stop_evidence.py \
  --prefix "${SMOKE_PREFIX}-account-deletion" \
  --api-level "$API_LEVEL" \
  --commit "$(git rev-parse HEAD)" \
  --summary "stage14-evidence/account-deletion-force-stop-api-${API_LEVEL}.json"
echo "Account-deletion process-death release matrix passed on API ${API_LEVEL}."

run_visual_profile() {
  profile="$1"
  theme="$2"
  font_scale="$3"
  force_rtl="$4"
  profile_dump="${SMOKE_PREFIX}-${profile}-window.xml"
  remote_profile_dump="/sdcard/${profile_dump}"
  profile_logcat="${SMOKE_PREFIX}-${profile}-logcat.txt"

  adb shell settings put system font_scale "$font_scale"
  adb shell settings put global debug.force_rtl "$force_rtl"
  adb shell setprop debug.force_rtl "$force_rtl"
  adb shell am force-stop "$PACKAGE_NAME"
  adb logcat -c

  set +e
  adb shell am start -W -S -n "$SMOKE_COMPONENT" \
    --es aqua_smoke_theme "$theme" \
    --es aqua_smoke_profile "$profile" \
    2>&1 | tee "${SMOKE_PREFIX}-${profile}-start.txt"
  start_status=${PIPESTATUS[0]}
  set -e

  if [ "$start_status" -ne 0 ]; then
    adb logcat -d > "$profile_logcat" 2>&1
    echo "Smoke Activity could not be started for ${profile} on API ${API_LEVEL}."
    return "$start_status"
  fi

  rm -f "$profile_dump"
  for attempt in $(seq 1 50); do
    adb shell uiautomator dump "$remote_profile_dump" >/dev/null 2>&1 || true
    adb pull "$remote_profile_dump" "$profile_dump" >/dev/null 2>&1 || true

    if grep -q "${PASS_MARKER}:${profile}" "$profile_dump" 2>/dev/null; then
      adb pull "/sdcard/Android/data/${PACKAGE_NAME}/files/smoke-screens/." \
        "$SMOKE_SCREEN_DIR/" >/dev/null
      screenshot_count="$(
        find "$SMOKE_SCREEN_DIR" -type f -name "${profile}-*.png" \
          | wc -l \
          | tr -d ' '
      )"
      if [ "$screenshot_count" -ne 4 ]; then
        adb logcat -d > "$profile_logcat" 2>&1
        echo "Expected 4 ${profile} screenshots, found ${screenshot_count}."
        return 1
      fi
      adb logcat -d > "$profile_logcat" 2>&1
      echo "${profile} visual smoke passed with ${screenshot_count} screenshots on API ${API_LEVEL}."
      return 0
    fi

    if grep -q "RELEASE_SMOKE_FAIL" "$profile_dump" 2>/dev/null; then
      adb logcat -d > "$profile_logcat" 2>&1
      echo "Minified release smoke reported an application failure for ${profile} on API ${API_LEVEL}."
      cat "$profile_dump" || true
      return 1
    fi
    sleep 1
  done

  adb logcat -d > "$profile_logcat" 2>&1
  echo "Minified release smoke timed out for ${profile} on API ${API_LEVEL}."
  return 1
}

run_visual_profile light light 1.0 0
run_visual_profile dark dark 1.0 0
run_visual_profile large-font-light light 2.0 0
run_visual_profile large-font-dark dark 2.0 0
run_visual_profile rtl-light light 1.0 1
run_visual_profile rtl-dark dark 1.0 1
python3 tools/verify_accessibility_evidence.py \
  --prefix "$SMOKE_PREFIX" \
  --screens "$SMOKE_SCREEN_DIR" \
  --api-level "$API_LEVEL" \
  --commit "$(git rev-parse HEAD)" \
  --summary "stage14-evidence/accessibility-api-${API_LEVEL}.json"
cp "${SMOKE_PREFIX}-dark-window.xml" "$WINDOW_DUMP"
echo "Minified Light/Dark, 200% font and RTL visual smoke passed on API ${API_LEVEL}."

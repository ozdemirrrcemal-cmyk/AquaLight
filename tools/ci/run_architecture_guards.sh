#!/usr/bin/env bash
set -Eeuo pipefail

GUARDS=(
  architecture_guard.py
  session_startup_guard.py
  composition_root_guard.py
  ui_dependency_construction_guard.py
  device_application_boundary_guard.py
  device_root_application_boundary_guard.py
  tank_device_assignment_boundary_guard.py
  aquarium_application_boundary_guard.py
  care_application_boundary_guard.py
  provisioning_discovery_boundary_guard.py
  provisioning_progress_boundary_guard.py
  provisioning_commit_recovery_guard.py
  navigation_guard.py
  ws_protocol_guard.py
  permission_architecture_guard.py
  notification_reminder_architecture_guard.py
  process_safe_feedback_guard.py
  stage9_feedback_media_guard.py
  firebase_telemetry_guard.py
  privacy_legal_guard.py
  design_system_resource_guard.py
  localization_accessibility_guard.py
)

for guard in "${GUARDS[@]}"; do
  path="tools/${guard}"
  if [[ ! -f "$path" ]]; then
    echo "Required guard is missing: $path" >&2
    exit 1
  fi
  echo "==> $path"
  python3 "$path"
done

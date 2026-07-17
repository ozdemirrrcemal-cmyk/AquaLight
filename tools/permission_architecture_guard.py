#!/usr/bin/env python3
"""Fail CI when a UI screen bypasses the central capability permission system."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight/ui"
ALLOWED_ROOT = UI_ROOT / "common/permission"

FORBIDDEN_TOKENS = {
    "ActivityResultContracts.RequestPermission(": (
        "runtime permission launchers must live in CapabilityPermissionCoordinator"
    ),
    "ActivityResultContracts.RequestMultiplePermissions(": (
        "runtime permission launchers must live in CapabilityPermissionCoordinator"
    ),
    "ContextCompat.checkSelfPermission(": (
        "screens must query AppCapability through CapabilityPermissionCoordinator"
    ),
    "shouldShowRequestPermissionRationale(": (
        "rationale decisions must be produced by PermissionPolicy"
    ),
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS": (
        "app permission settings must be opened by CapabilityPermissionCoordinator"
    ),
    "Settings.ACTION_APP_NOTIFICATION_SETTINGS": (
        "notification permission settings must be opened by CapabilityPermissionCoordinator"
    ),
    "Manifest.permission.CAMERA": (
        "camera access must be requested as CAMERA_PHOTO or CAMERA_QR"
    ),
    "Manifest.permission.POST_NOTIFICATIONS": (
        "notification access must be requested as NOTIFICATIONS"
    ),
    "Manifest.permission.BLUETOOTH_SCAN": (
        "BLE scan access must be requested through PermissionPolicy"
    ),
    "Manifest.permission.BLUETOOTH_CONNECT": (
        "BLE connection access must be requested through PermissionPolicy"
    ),
    "Manifest.permission.ACCESS_FINE_LOCATION": (
        "location-sensitive capability access must be requested through PermissionPolicy"
    ),
}

errors: list[str] = []

if not UI_ROOT.exists():
    errors.append(f"Missing UI source root: {UI_ROOT.relative_to(ROOT)}")
else:
    for source in UI_ROOT.rglob("*.kt"):
        if source.is_relative_to(ALLOWED_ROOT):
            continue

        text = source.read_text(encoding="utf-8", errors="ignore")
        relative = source.relative_to(ROOT)
        for token, reason in FORBIDDEN_TOKENS.items():
            if token in text:
                errors.append(f"{relative}: {reason}: {token}")

required_files = (
    "app/src/main/java/com/aqua/aqualight/platform/permissions/AppCapability.kt",
    "app/src/main/java/com/aqua/aqualight/platform/permissions/PermissionPolicy.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionCoordinator.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionBottomSheet.kt",
)
for relative_path in required_files:
    if not (ROOT / relative_path).is_file():
        errors.append(f"{relative_path}: required central permission component is missing")

if errors:
    print("Central permission architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Central permission architecture guard passed.")

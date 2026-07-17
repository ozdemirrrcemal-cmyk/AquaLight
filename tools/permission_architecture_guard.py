#!/usr/bin/env python3
"""Fail CI when a UI screen bypasses the central capability permission system."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight/ui"
ALLOWED_ROOT = UI_ROOT / "common/permission"
RES_LAYOUT_ROOT = ROOT / "app/src/main/res/layout"
UI_SPEC_PATH = (
    ALLOWED_ROOT / "CapabilityPermissionUiSpecResolver.kt"
)
SHEET_PATH = ALLOWED_ROOT / "CapabilityPermissionBottomSheet.kt"

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

# Capability-specific artwork is part of the same central contract as copy and actions.
# No screen, Fragment or XML layout may choose a permission icon independently.
if ALLOWED_ROOT.exists():
    for source in ALLOWED_ROOT.rglob("*.kt"):
        if source == UI_SPEC_PATH:
            continue
        text = source.read_text(encoding="utf-8", errors="ignore")
        if "R.drawable.ic_permission_" in text:
            errors.append(
                f"{source.relative_to(ROOT)}: permission artwork must be selected only "
                "by CapabilityPermissionUiSpecResolver"
            )

if RES_LAYOUT_ROOT.exists():
    for layout in RES_LAYOUT_ROOT.rglob("*.xml"):
        text = layout.read_text(encoding="utf-8", errors="ignore")
        if "@drawable/ic_permission_" in text:
            errors.append(
                f"{layout.relative_to(ROOT)}: layouts must render the icon resource "
                "provided by the central permission UI resolver"
            )

required_files = (
    "app/src/main/java/com/aqua/aqualight/platform/permissions/AppCapability.kt",
    "app/src/main/java/com/aqua/aqualight/platform/permissions/PermissionPolicy.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionCoordinator.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionBottomSheet.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionUiSpecResolver.kt",
    "app/src/test/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionUiSpecResolverTest.kt",
)
for relative_path in required_files:
    if not (ROOT / relative_path).is_file():
        errors.append(f"{relative_path}: required central permission component is missing")

if SHEET_PATH.is_file():
    sheet_text = SHEET_PATH.read_text(encoding="utf-8", errors="ignore")
    if "CapabilityPermissionUiSpecResolver.resolve" not in sheet_text:
        errors.append(
            f"{SHEET_PATH.relative_to(ROOT)}: shared sheet must render the central UI spec"
        )
    if "when (capability)" in sheet_text:
        errors.append(
            f"{SHEET_PATH.relative_to(ROOT)}: capability copy/artwork decisions belong in "
            "CapabilityPermissionUiSpecResolver"
        )

if errors:
    print("Central permission architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(
    "Central permission architecture guard passed: policy, launchers, copy and artwork "
    "remain capability-driven and centrally owned."
)

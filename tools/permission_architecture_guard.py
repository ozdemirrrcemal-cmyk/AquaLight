#!/usr/bin/env python3
"""Fail CI when production code bypasses the central capability permission system."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
UI_ROOT = APP_ROOT / "ui"
ALLOWED_ROOT = UI_ROOT / "common/permission"
RES_LAYOUT_ROOT = ROOT / "app/src/main/res/layout"
UI_SPEC_PATH = ALLOWED_ROOT / "CapabilityPermissionUiSpecResolver.kt"
SHEET_PATH = ALLOWED_ROOT / "CapabilityPermissionBottomSheet.kt"
COORDINATOR_PATH = ALLOWED_ROOT / "CapabilityPermissionCoordinator.kt"
CONTINUATION_PATH = ALLOWED_ROOT / "CapabilityPermissionContinuationState.kt"
PERMISSION_POLICY_PATH = APP_ROOT / "platform/permissions/PermissionPolicy.kt"
PRECISE_REMINDER_POLICY_PATH = APP_ROOT / "platform/permissions/PreciseReminderAccessPolicy.kt"
NOTIFICATION_POLICY_PATH = APP_ROOT / "data/notifications/AndroidNotificationPermissionPolicy.kt"
BLE_PERMISSION_CHECK_PATHS = {
    APP_ROOT / "data/devices/provisioning/ble/AqlBleProvisioningGattClient.kt",
    APP_ROOT / "data/devices/provisioning/ble/AqlBleDeviceInfoPreflightClient.kt",
    APP_ROOT / "data/devices/provisioning/ble/AqlBleProvisioningScanner.kt",
    APP_ROOT / "data/devices/provisioning/ble/AqlBleProvisioningAddressResolver.kt",
}

UI_FORBIDDEN_TOKENS = {
    "ActivityResultContracts.RequestPermission(": (
        "runtime permission launchers must live in CapabilityPermissionCoordinator"
    ),
    "ActivityResultContracts.RequestMultiplePermissions(": (
        "runtime permission launchers must live in CapabilityPermissionCoordinator"
    ),
    "ContextCompat.checkSelfPermission(": (
        "screens must query AppCapability through CapabilityPermissionCoordinator"
    ),
    "ActivityCompat.checkSelfPermission(": (
        "screens must query AppCapability through CapabilityPermissionCoordinator"
    ),
    "shouldShowRequestPermissionRationale": (
        "rationale decisions must be produced by PermissionPolicy"
    ),
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS": (
        "app permission settings must be opened by CapabilityPermissionCoordinator"
    ),
    "Settings.ACTION_APP_NOTIFICATION_SETTINGS": (
        "notification permission settings must be opened by CapabilityPermissionCoordinator"
    ),
    "Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS": (
        "notification channel settings must be opened by CapabilityPermissionCoordinator"
    ),
    "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM": (
        "Alarms & reminders special access must be opened by CapabilityPermissionCoordinator"
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

# State-changing permission APIs and permission-specific Settings routes are central-only.
# BLE transport adapters may perform read-only defensive checks immediately before a
# platform call because permission can be revoked after the UI coordinator grants it.
GLOBAL_PERMISSION_BOUNDARIES = {
    "ActivityCompat.requestPermissions(": set(),
    "requestPermissions(": set(),
    "ActivityResultContracts.RequestPermission(": {COORDINATOR_PATH},
    "ActivityResultContracts.RequestMultiplePermissions(": {COORDINATOR_PATH},
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS": {COORDINATOR_PATH},
    "Settings.ACTION_APP_NOTIFICATION_SETTINGS": {COORDINATOR_PATH},
    "Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS": {COORDINATOR_PATH},
    "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM": {COORDINATOR_PATH},
    "ContextCompat.checkSelfPermission(": {
        PERMISSION_POLICY_PATH,
        NOTIFICATION_POLICY_PATH,
        *BLE_PERMISSION_CHECK_PATHS,
    },
    "ActivityCompat.checkSelfPermission(": set(),
}

errors: list[str] = []

if not APP_ROOT.exists():
    errors.append(f"Missing production source root: {APP_ROOT.relative_to(ROOT)}")
else:
    for source in APP_ROOT.rglob("*.kt"):
        text = source.read_text(encoding="utf-8", errors="ignore")
        relative = source.relative_to(ROOT)
        for token, allowed_paths in GLOBAL_PERMISSION_BOUNDARIES.items():
            if token in text and source not in allowed_paths:
                errors.append(
                    f"{relative}: production permission boundary bypass: {token}"
                )

if not UI_ROOT.exists():
    errors.append(f"Missing UI source root: {UI_ROOT.relative_to(ROOT)}")
else:
    for source in UI_ROOT.rglob("*.kt"):
        if source.is_relative_to(ALLOWED_ROOT):
            continue

        text = source.read_text(encoding="utf-8", errors="ignore")
        relative = source.relative_to(ROOT)
        for token, reason in UI_FORBIDDEN_TOKENS.items():
            if token in text:
                errors.append(f"{relative}: {reason}: {token}")

# Capability-specific artwork is part of the same central contract as copy and actions.
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
    "app/src/main/java/com/aqua/aqualight/platform/permissions/PreciseReminderAccessPolicy.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionCoordinator.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionBottomSheet.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionUiSpecResolver.kt",
    "app/src/main/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionContinuationState.kt",
    "app/src/test/java/com/aqua/aqualight/platform/permissions/PermissionPolicyTest.kt",
    "app/src/test/java/com/aqua/aqualight/platform/permissions/PreciseReminderAccessPolicyTest.kt",
    "app/src/test/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionUiSpecResolverTest.kt",
    "app/src/test/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionContinuationStateTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/platform/permissions/PermissionInfrastructureInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionCoordinatorRecreationInstrumentedTest.kt",
    "app/src/debug/java/com/aqua/aqualight/ui/common/permission/CapabilityPermissionRecreationTestFragment.kt",
)
for relative_path in required_files:
    if not (ROOT / relative_path).is_file():
        errors.append(f"{relative_path}: required central permission component is missing")

if PERMISSION_POLICY_PATH.is_file():
    policy_text = PERMISSION_POLICY_PATH.read_text(encoding="utf-8", errors="ignore")
    for token, reason in (
        ("AppCapability.PRECISE_REMINDERS", "precise reminder access must be capability-driven"),
        ("PreciseReminderAccessPolicy", "special access must be evaluated centrally"),
        ("PermissionDecision.OPEN_SETTINGS", "missing special access must route to Settings"),
    ):
        if token not in policy_text:
            errors.append(
                f"{PERMISSION_POLICY_PATH.relative_to(ROOT)}: {reason}: missing {token}"
            )

if PRECISE_REMINDER_POLICY_PATH.is_file():
    precise_text = PRECISE_REMINDER_POLICY_PATH.read_text(encoding="utf-8", errors="ignore")
    for token, reason in (
        ("canScheduleExactAlarms", "Android 12+ exact access must be queried from AlarmManager"),
        ("Build.VERSION_CODES.S", "special access must begin at API 31"),
    ):
        if token not in precise_text:
            errors.append(
                f"{PRECISE_REMINDER_POLICY_PATH.relative_to(ROOT)}: {reason}: missing {token}"
            )

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

if UI_SPEC_PATH.is_file():
    ui_spec_text = UI_SPEC_PATH.read_text(encoding="utf-8", errors="ignore")
    for token, reason in (
        ("AppCapability.PRECISE_REMINDERS", "precise reminder access needs common UI copy"),
        ("ic_permission_precise_reminders", "precise reminder access needs a professional icon"),
    ):
        if token not in ui_spec_text:
            errors.append(f"{UI_SPEC_PATH.relative_to(ROOT)}: {reason}: missing {token}")

if COORDINATOR_PATH.is_file():
    coordinator_text = COORDINATOR_PATH.read_text(encoding="utf-8", errors="ignore")
    for token, reason in (
        (
            "fun openNotificationChannelSettingsFor(",
            "blocked notification categories require one central settings entry point",
        ),
        (
            "Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS",
            "channel Settings Intent construction must remain central",
        ),
        (
            "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM",
            "Alarms & reminders Settings Intent construction must remain central",
        ),
        (
            "CapabilityPermissionContinuationState()",
            "settings return must use the one-shot continuation state",
        ),
        (
            "consumeSettingsReturn",
            "lifecycle and Activity Result callbacks must share one-shot consumption",
        ),
        (
            "if (policy.isGranted(capability))",
            "empty runtime-permission sets must not bypass ungranted special access",
        ),
        (
            "STATE_NOTIFICATION_CHANNEL_ID",
            "channel settings destination must survive rotation/process recreation",
        ),
        (
            "STATE_WAITING_FOR_SETTINGS to snapshot.waitingForSettings",
            "settings-return state must be persisted through SavedStateRegistry",
        ),
        (
            "STATE_EXPLANATION_MODE to snapshot.explanationModeName",
            "permission explanation state must survive rotation/process recreation",
        ),
        (
            "continuation.pendingExplanationMode",
            "an explanation must not be consumed as a completed permission action",
        ),
    ):
        if token not in coordinator_text:
            errors.append(
                f"{COORDINATOR_PATH.relative_to(ROOT)}: {reason}: missing {token}"
            )

if CONTINUATION_PATH.is_file():
    continuation_text = CONTINUATION_PATH.read_text(encoding="utf-8", errors="ignore")
    for token, reason in (
        ("fun consumeSettingsReturn", "settings return must be explicitly consumed"),
        ("if (!waitingForSettings) return null", "duplicate callbacks must be ignored"),
        ("clear()", "denied or completed actions must clear pending state"),
        ("fun snapshot()", "pending continuation must be recreatable"),
        ("fun restore(", "pending continuation must restore after recreation"),
    ):
        if token not in continuation_text:
            errors.append(
                f"{CONTINUATION_PATH.relative_to(ROOT)}: {reason}: missing {token}"
            )

for defensive_path in BLE_PERMISSION_CHECK_PATHS:
    if not defensive_path.is_file():
        errors.append(
            f"{defensive_path.relative_to(ROOT)}: required BLE permission boundary is missing"
        )
        continue
    text = defensive_path.read_text(encoding="utf-8", errors="ignore")
    if "ContextCompat.checkSelfPermission(" not in text:
        errors.append(
            f"{defensive_path.relative_to(ROOT)}: BLE platform adapter must fail closed after runtime revocation"
        )
    for forbidden in (
        "requestPermissions(",
        "ActivityResultContracts",
        "Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
        "Settings.ACTION_APP_NOTIFICATION_SETTINGS",
        "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM",
    ):
        if forbidden in text:
            errors.append(
                f"{defensive_path.relative_to(ROOT)}: defensive BLE check must not request permission or route Settings: {forbidden}"
            )

if errors:
    print("Central permission architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(
    "Central permission architecture guard passed: runtime permissions, precise-reminder "
    "special access, launchers, one-shot Settings continuation, defensive BLE checks, "
    "copy, artwork and process-safe app/channel routing remain central."
)

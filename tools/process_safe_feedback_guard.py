#!/usr/bin/env python3
"""Fail CI when dialogs, sheets or transient feedback bypass the Stage 8 contract."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
APP_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
UI_ROOT = APP_ROOT / "ui"
BASE_ACTIVITY = APP_ROOT / "base/BaseActivity.kt"
BASE_LOADING_ROOT = APP_ROOT / "base/loading"
DIALOG_MANAGER = APP_ROOT / "utils/DialogManager.kt"
COMMON_DIALOG_ROOT = UI_ROOT / "common/dialog"
CONFIRM_DIALOG = COMMON_DIALOG_ROOT / "ConfirmDialogFragment.kt"

REQUIRED_FILES = (
    UI_ROOT / "common/bottomsheet/ThemeBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/TankSettingsEditorBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/GlobalActionBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/AquaTimePickerBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/SingleChoiceBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/TextInputBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/CareProfileBottomSheet.kt",
    UI_ROOT / "common/feedback/FeedbackBottomSheet.kt",
    UI_ROOT / "common/dialog/AppDatePickerDialogFragment.kt",
    UI_ROOT / "common/dialog/AppTimePickerDialogFragment.kt",
    CONFIRM_DIALOG,
    BASE_LOADING_ROOT / "LoadingOverlayDialogFragment.kt",
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/ui/common/feedback/ProcessSafeFeedbackInstrumentedTest.kt",
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/base/loading/LoadingOverlayRaceInstrumentedTest.kt",
    ROOT / "app/src/debug/java/com/aqua/aqualight/ui/common/feedback/Stage8DialogTestActivity.kt",
    ROOT / "app/src/debug/AndroidManifest.xml",
    ROOT / "docs/stage8-process-safe-feedback-contract.md",
)

LEGACY_PROCESS_UNSAFE_FILES = (
    UI_ROOT / "tabs/devices/common/feedback/DeviceConfirmBottomSheet.kt",
    UI_ROOT / "tabs/devices/common/feedback/DeviceConfirmTone.kt",
    UI_ROOT / "common/bottomsheet/SettingsContentBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/TankTypeBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/TankSizeBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/SetupDateBottomSheet.kt",
    UI_ROOT / "common/bottomsheet/TankStyleBottomSheet.kt",
    UI_ROOT / "common/loading/LoadingOverlayDialogFragment.kt",
    UI_ROOT / "tabs/aquarium/materials/CustomMaterialSheet.kt",
)

errors: list[str] = []

for path in REQUIRED_FILES:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required Stage 8 component is missing")

for path in LEGACY_PROCESS_UNSAFE_FILES:
    if path.exists():
        errors.append(f"{path.relative_to(ROOT)}: legacy callback/raw-dialog component must stay removed")

callback_field_pattern = re.compile(
    r"^\s*(?:var|val)\s+on[A-Z][A-Za-z0-9_]*\s*:\s*\(.*\)\s*->",
    re.MULTILINE,
)
constructor_pattern = re.compile(
    r"class\s+[A-Za-z0-9_]*(?:BottomSheet|DialogFragment)[A-Za-z0-9_]*\s*\((.*?)\)\s*:\s*(?:BottomSheetDialogFragment|DialogFragment)",
    re.DOTALL,
)

for source in APP_ROOT.rglob("*.kt"):
    text = source.read_text(encoding="utf-8", errors="ignore")
    relative = source.relative_to(ROOT)
    is_fragment_dialog = "BottomSheetDialogFragment" in text or "DialogFragment" in text

    if is_fragment_dialog:
        if callback_field_pattern.search(text):
            errors.append(f"{relative}: Fragment dialog must return actions through Fragment Result")
        constructor_match = constructor_pattern.search(text)
        if constructor_match and constructor_match.group(1).strip():
            errors.append(f"{relative}: Fragment dialog must expose a public no-argument constructor")

    if "BottomSheetDialog(" in text and not is_fragment_dialog:
        errors.append(f"{relative}: raw BottomSheetDialog is forbidden")

    if "AlertDialog.Builder(" in text:
        errors.append(f"{relative}: raw AlertDialog is forbidden")

    if "MaterialAlertDialogBuilder(" in text and not source.is_relative_to(COMMON_DIALOG_ROOT):
        errors.append(f"{relative}: Material confirmation dialogs must stay under ui/common/dialog")

    if "DatePickerDialog(" in text and not source.is_relative_to(COMMON_DIALOG_ROOT):
        errors.append(f"{relative}: date picker must use AppDatePickerDialogFragment")

    if "TimePickerDialog(" in text and not source.is_relative_to(COMMON_DIALOG_ROOT):
        errors.append(
            f"{relative}: time picker must use AppTimePickerDialogFragment "
            "or AquaTimePickerBottomSheet"
        )

    if "Toast.makeText(" in text:
        errors.append(f"{relative}: Toast is forbidden; use the shared Snackbar renderer")

    if "Snackbar.make(" in text and source != BASE_ACTIVITY:
        errors.append(f"{relative}: Snackbar creation must remain under BaseActivity")

    if "DialogManager.showConfirmDialog" in text:
        errors.append(f"{relative}: confirmations must use ConfirmDialogFragment Fragment Result")

if CONFIRM_DIALOG.is_file():
    confirm_text = CONFIRM_DIALOG.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "BottomSheetDeviceConfirmBinding",
        "R.drawable.ic_info",
        "R.drawable.ic_success",
        "R.drawable.ic_warning",
        "R.drawable.ic_error",
        "R.color.aqua_bottom_sheet_surface",
        "R.color.aqua_bottom_sheet_sheet_border",
        "R.dimen.aqua_size_28",
    ):
        if token not in confirm_text:
            errors.append(
                f"{CONFIRM_DIALOG.relative_to(ROOT)}: central confirm dialog must reuse shared Aqua resources: {token}"
            )

if DIALOG_MANAGER.is_file():
    manager_text = DIALOG_MANAGER.read_text(encoding="utf-8", errors="ignore")
    for token in ("onDismiss:", "onConfirm:", "onCancel:", "showConfirmDialog"):
        if token in manager_text:
            errors.append(f"{DIALOG_MANAGER.relative_to(ROOT)}: callback API is forbidden: {token}")
    if "FeedbackBottomSheet.show(" not in manager_text:
        errors.append(f"{DIALOG_MANAGER.relative_to(ROOT)}: info feedback must delegate to FeedbackBottomSheet")

loading_renderer = BASE_LOADING_ROOT / "LoadingOverlayDialogFragment.kt"
if loading_renderer.is_file():
    loading_text = loading_renderer.read_text(encoding="utf-8", errors="ignore")
    for token in ("pendingOverlays", "WeakReference", "dismissAllowingStateLoss"):
        if token not in loading_text:
            errors.append(
                f"{loading_renderer.relative_to(ROOT)}: pending loading show/hide race protection is missing: {token}"
            )

for source in (
    UI_ROOT / "common/bottomsheet/CareTaskTypeBottomSheetFragment.kt",
    UI_ROOT / "common/bottomsheet/GlobalActionBottomSheet.kt",
    CONFIRM_DIALOG,
    BASE_ACTIVITY,
):
    if not source.is_file():
        continue
    text = source.read_text(encoding="utf-8", errors="ignore")
    for token in ("textSize =", ".dp()", ".dp(requireContext", "setMargins(24", "elevation = 8f"):
        if token in text:
            errors.append(f"{source.relative_to(ROOT)}: Stage 8 dimensions must come from resources: {token}")

if errors:
    print("Process-safe feedback architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(
    "Process-safe feedback architecture guard passed: all dialogs and sheets are recreatable, "
    "results are callback-free, loading show/hide races are covered, Snackbar rendering is centralized, "
    "Toast is absent, and Stage 8 dimensions are resource-backed."
)

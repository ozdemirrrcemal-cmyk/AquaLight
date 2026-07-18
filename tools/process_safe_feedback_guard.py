#!/usr/bin/env python3
"""Fail CI when sheets or transient feedback bypass the process-safe UI contract."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
APP_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight"
UI_ROOT = APP_ROOT / "ui"
THEME_SHEET = UI_ROOT / "common/bottomsheet/ThemeBottomSheet.kt"
FEEDBACK_SHEET = UI_ROOT / "common/feedback/FeedbackBottomSheet.kt"
LEGACY_DEVICE_CONFIRM = UI_ROOT / "tabs/devices/common/feedback/DeviceConfirmBottomSheet.kt"
LEGACY_DEVICE_TONE = UI_ROOT / "tabs/devices/common/feedback/DeviceConfirmTone.kt"
SNACKBAR_RENDERER = APP_ROOT / "base/BaseActivity.kt"
TRANSITIONAL_RAW_BOTTOM_SHEET_ALLOWLIST = {
    UI_ROOT / "common/bottomsheet/SettingsContentBottomSheet.kt",
}
TRANSITIONAL_TOAST_ALLOWLIST = {
    UI_ROOT / "tabs/devices/add/DeviceWifiProvisioningFragment.kt",
    UI_ROOT / "tabs/devices/add/DeviceAddFragment.kt",
    UI_ROOT / "tabs/devices/detail/light/DeviceLightRootFragment.kt",
    UI_ROOT / "tabs/aquarium/create/CreateTankFragment.kt",
}

errors: list[str] = []

required_files = (
    THEME_SHEET,
    FEEDBACK_SHEET,
    ROOT / "app/src/androidTest/java/com/aqua/aqualight/ui/common/feedback/ProcessSafeFeedbackInstrumentedTest.kt",
    ROOT / "docs/stage8-process-safe-feedback-contract.md",
)
for path in required_files:
    if not path.is_file():
        errors.append(f"{path.relative_to(ROOT)}: required stage-8 component is missing")

for legacy_path in (LEGACY_DEVICE_CONFIRM, LEGACY_DEVICE_TONE):
    if legacy_path.exists():
        errors.append(f"{legacy_path.relative_to(ROOT)}: legacy callback-based feedback component must stay removed")

if THEME_SHEET.is_file():
    theme_text = THEME_SHEET.read_text(encoding="utf-8", errors="ignore")
    for token in ("onBeforeThemeApplied", "onThemeChanged"):
        if token in theme_text:
            errors.append(
                f"{THEME_SHEET.relative_to(ROOT)}: runtime callback field is process-unsafe: {token}"
            )
    for token in ("setFragmentResult(", "REQUEST_KEY", "RESULT_THEME_MODE"):
        if token not in theme_text:
            errors.append(
                f"{THEME_SHEET.relative_to(ROOT)}: theme result contract is incomplete: missing {token}"
            )

if FEEDBACK_SHEET.is_file():
    feedback_text = FEEDBACK_SHEET.read_text(encoding="utf-8", errors="ignore")
    for token in (
        "class FeedbackBottomSheet : BottomSheetDialogFragment",
        "arguments = bundleOf(",
        "setFragmentResult(",
        "fun newInstance(",
        "fun show(",
    ):
        if token not in feedback_text:
            errors.append(
                f"{FEEDBACK_SHEET.relative_to(ROOT)}: shared feedback contract is incomplete: missing {token}"
            )

if UI_ROOT.exists():
    callback_field_pattern = re.compile(
        r"^\s*(?:var|val)\s+on[A-Z][A-Za-z0-9_]*\s*:\s*\(.*\)\s*->",
        re.MULTILINE,
    )
    constructor_pattern = re.compile(
        r"class\s+[A-Za-z0-9_]*BottomSheet[A-Za-z0-9_]*\s*\((.*?)\)\s*:\s*BottomSheetDialogFragment",
        re.DOTALL,
    )

    for source in UI_ROOT.rglob("*.kt"):
        text = source.read_text(encoding="utf-8", errors="ignore")
        relative = source.relative_to(ROOT)

        if "BottomSheetDialogFragment" in text:
            if callback_field_pattern.search(text):
                errors.append(
                    f"{relative}: Fragment-based sheet must return actions through Fragment Result or SavedStateHandle"
                )
            constructor_match = constructor_pattern.search(text)
            if constructor_match and constructor_match.group(1).strip():
                errors.append(
                    f"{relative}: Fragment-based sheet must expose an empty constructor and store state in arguments"
                )

        if "BottomSheetDialog(" in text and source not in TRANSITIONAL_RAW_BOTTOM_SHEET_ALLOWLIST:
            errors.append(
                f"{relative}: raw BottomSheetDialog is forbidden; use a recreatable Fragment-based sheet"
            )

        if "Snackbar.make(" in text and source != SNACKBAR_RENDERER:
            errors.append(
                f"{relative}: Snackbar creation must remain under the shared renderer boundary"
            )

        if "Toast.makeText(" in text and source not in TRANSITIONAL_TOAST_ALLOWLIST:
            errors.append(
                f"{relative}: new Toast usage is forbidden; route transient feedback through the shared UI contract"
            )

if errors:
    print("Process-safe feedback architecture guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(
    "Process-safe feedback architecture guard passed: Fragment sheets remain recreatable, "
    "theme and confirmation results are callback-free, and transient feedback debt cannot expand."
)

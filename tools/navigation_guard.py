#!/usr/bin/env python3
"""Static navigation contract guard for AquaLight.

Rules:
1. Kotlin code must not navigate with raw R.id.action_* values. Use Safe Args
   Directions for graph actions.
2. Every nav graph destination with declared <argument> entries must read those
   arguments through Safe Args navArgs(), not requireArguments()/arguments.
3. Device-root entry, header, connection-state and resource ownership must remain
   on the shared root UI architecture guarded by device_root_ui_architecture_guard.
4. Family-owned Dosing and Cooling implementations must remain strictly isolated;
   cross-family dependencies are rejected by device_family_isolation_guard.
5. UI and data layers are mutually isolated repository-wide; direct dependencies
   in either direction are rejected by ui_data_layer_isolation_guard.

Intentional exceptions: BottomSheet/FragmentResult/manual child-fragment bundles
are not nav graph destinations and are outside Safe Args scope.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from device_family_isolation_guard import validate_repository as validate_family_isolation
from device_root_ui_architecture_guard import validate_repository as validate_device_root_ui
from ui_data_layer_isolation_guard import validate_repository as validate_ui_data_isolation

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app" / "src" / "main" / "java"
NAV_ROOT = ROOT / "app" / "src" / "main" / "res" / "navigation"
violations: list[str] = []

for path in SOURCE_ROOT.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    if "R.id.action_" in text:
        violations.append(
            f"raw action reference: {path.relative_to(ROOT)}"
        )

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
APP_NS = "{http://schemas.android.com/apk/res-auto}"
LIGHT_DESTINATION_PREFIX = "com.aqua.aqualight.ui.tabs.devices.detail.light."
light_safe_args_contracts: dict[str, tuple[Path, tuple[object, ...]]] = {}

def argument_signature(element: ET.Element) -> tuple[tuple[str, str, str, str], ...]:
    return tuple(
        sorted(
            (
                child.get(f"{ANDROID_NS}name", ""),
                child.get(f"{APP_NS}argType", ""),
                child.get(f"{ANDROID_NS}defaultValue", ""),
                child.get(f"{APP_NS}nullable", ""),
            )
            for child in list(element)
            if child.tag == "argument"
        )
    )

def safe_args_signature(fragment: ET.Element) -> tuple[object, ...]:
    actions = tuple(
        sorted(
            (
                action.get(f"{ANDROID_NS}id", ""),
                action.get(f"{APP_NS}destination", ""),
                argument_signature(action),
            )
            for action in list(fragment)
            if action.tag == "action"
        )
    )
    return argument_signature(fragment), actions

manual_arg_tokens = (
    "requireArguments().get",
    "arguments?.get",
    "Args.fromBundle(requireArguments())",
)

for nav_path in NAV_ROOT.glob("*.xml"):
    try:
        graph = ET.parse(nav_path).getroot()
    except ET.ParseError as exc:
        violations.append(
            f"invalid navigation XML: {nav_path.relative_to(ROOT)}: {exc}"
        )
        continue

    for fragment in graph.iter("fragment"):
        fragment_class = fragment.get(f"{ANDROID_NS}name")
        if not fragment_class:
            continue

        signature = safe_args_signature(fragment)
        if fragment_class.startswith(LIGHT_DESTINATION_PREFIX):
            previous_contract = light_safe_args_contracts.get(fragment_class)
            if previous_contract is None:
                light_safe_args_contracts[fragment_class] = (nav_path, signature)
            elif previous_contract[1] != signature:
                violations.append(
                    "inconsistent Light Safe Args contract for "
                    f"{fragment_class}: {previous_contract[0].relative_to(ROOT)} vs "
                    f"{nav_path.relative_to(ROOT)}"
                )

        has_nav_args = bool(signature[0])
        if not has_nav_args:
            continue

        source_path = SOURCE_ROOT / Path(*fragment_class.split(".")).with_suffix(".kt")
        if not source_path.exists():
            violations.append(
                f"nav destination source missing: {fragment_class} -> {source_path.relative_to(ROOT)}"
            )
            continue

        text = source_path.read_text(encoding="utf-8")
        if "by navArgs()" not in text:
            violations.append(
                f"missing navArgs delegate: {source_path.relative_to(ROOT)}"
            )

        for token in manual_arg_tokens:
            if token in text:
                violations.append(
                    f"manual nav argument read ({token}): {source_path.relative_to(ROOT)}"
                )

violations.extend(
    f"device-root UI architecture: {error}"
    for error in validate_device_root_ui(ROOT)
)
violations.extend(
    f"device-family isolation: {error}"
    for error in validate_family_isolation(ROOT)
)
violations.extend(
    f"UI/data layer isolation: {error}"
    for error in validate_ui_data_isolation(ROOT)
)

if violations:
    print("Navigation guard failed:")
    for violation in violations:
        print(f" - {violation}")
    sys.exit(1)

print(
    "Navigation guard passed: Light Safe Args parity, shared device-root UI, "
    "device-family isolation and UI/data layer isolation contracts are enforced."
)

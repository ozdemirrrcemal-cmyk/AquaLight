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
6. Cooling Compose hero rendering must remain reference-driven and family-owned.

Intentional exceptions: BottomSheet/FragmentResult/manual child-fragment bundles
are not nav graph destinations and are outside Safe Args scope.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from cooling_compose_architecture_guard import validate_repository as validate_cooling_compose
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
        violations.append(f"raw action reference: {path.relative_to(ROOT)}")

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
manual_arg_tokens = (
    "requireArguments().get",
    "arguments?.get",
    "Args.fromBundle(requireArguments())",
)

for nav_path in NAV_ROOT.glob("*.xml"):
    try:
        graph = ET.parse(nav_path).getroot()
    except ET.ParseError as exc:
        violations.append(f"invalid navigation XML: {nav_path.relative_to(ROOT)}: {exc}")
        continue

    for fragment in graph.iter("fragment"):
        fragment_class = fragment.get(f"{ANDROID_NS}name")
        has_nav_args = any(child.tag == "argument" for child in list(fragment))
        if not fragment_class or not has_nav_args:
            continue

        source_path = SOURCE_ROOT / Path(*fragment_class.split(".")).with_suffix(".kt")
        if not source_path.exists():
            violations.append(
                f"nav destination source missing: {fragment_class} -> {source_path.relative_to(ROOT)}"
            )
            continue

        text = source_path.read_text(encoding="utf-8")
        if "by navArgs()" not in text:
            violations.append(f"missing navArgs delegate: {source_path.relative_to(ROOT)}")

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
violations.extend(
    f"Cooling Compose architecture: {error}"
    for error in validate_cooling_compose(ROOT)
)

if violations:
    print("Navigation guard failed:")
    for violation in violations:
        print(f" - {violation}")
    sys.exit(1)

print(
    "Navigation guard passed: Safe Args, shared device-root UI, device-family isolation, "
    "UI/data isolation and Cooling Compose contracts are enforced."
)

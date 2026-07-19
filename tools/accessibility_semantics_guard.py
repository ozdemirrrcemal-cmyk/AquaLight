#!/usr/bin/env python3
"""Static accessibility semantics checks for AquaLight production layouts."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAYOUT = ROOT / "app/src/main/res/layout"
UI = ROOT / "app/src/main/java/com/aqua/aqualight/ui"
ANDROID = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"

RUNTIME_DESCRIBED_CONTROLS = {
    ("layout_aqua_header.xml", "@+id/btnActionOne"),
    ("layout_aqua_header.xml", "@+id/btnActionTwo"),
    ("layout_aqua_header.xml", "@+id/btnActionThree"),
    ("layout_aqua_header.xml", "@+id/btnFilledIconAction"),
    ("layout_aqua_header.xml", "@+id/btnCardIconAction"),
}


class GuardFailure(RuntimeError):
    pass


def fail(message: str) -> None:
    raise GuardFailure(message)


def short_tag(tag: str) -> str:
    return tag.rsplit(".", 1)[-1]


def is_ignored(view: ET.Element) -> bool:
    return view.attrib.get(f"{ANDROID}importantForAccessibility") in {
        "no",
        "noHideDescendants",
    }


def has_description(view: ET.Element) -> bool:
    value = view.attrib.get(f"{ANDROID}contentDescription", "").strip()
    return bool(value and value != "@null")


def is_interactive(view: ET.Element) -> bool:
    return (
        view.attrib.get(f"{ANDROID}clickable") == "true"
        or view.attrib.get(f"{ANDROID}focusable") == "true"
    )


def is_icon_only_control(view: ET.Element) -> bool:
    kind = short_tag(view.tag)
    interactive = is_interactive(view)
    if kind == "ImageButton":
        return True
    if kind == "ImageView" and interactive:
        return True
    if kind == "MaterialButton":
        has_icon = f"{APP}icon" in view.attrib
        has_text = bool(view.attrib.get(f"{ANDROID}text", "").strip())
        return interactive and has_icon and not has_text
    return False


def check_layouts() -> None:
    errors: list[str] = []
    for path in sorted(LAYOUT.glob("*.xml")):
        root = ET.parse(path).getroot()
        for view in root.iter():
            if not is_icon_only_control(view) or is_ignored(view):
                continue
            view_id = view.attrib.get(f"{ANDROID}id", short_tag(view.tag))
            if (path.name, view_id) in RUNTIME_DESCRIBED_CONTROLS:
                continue
            if not has_description(view):
                errors.append(f"{path.relative_to(ROOT)}:{view_id}")

    if errors:
        fail(
            "icon-only controls without contentDescription or decorative exclusion:\n- "
            + "\n- ".join(errors)
        )


def check_runtime_header_descriptions() -> None:
    path = UI / "common/header/AquaHeaderBindingExt.kt"
    source = path.read_text(encoding="utf-8")
    required_fragments = (
        "button.contentDescription =",
        "action.contentDescription",
        "btnFilledIconAction.contentDescription =",
        "filledIconAction.contentDescription",
        "btnCardIconAction.contentDescription =",
        "cardIconAction.contentDescription",
    )
    missing = [fragment for fragment in required_fragments if fragment not in source]
    if missing:
        fail(f"runtime header content-description contract is incomplete: {missing}")


def check_dynamic_status_semantics() -> None:
    files = (
        UI / "common/devicecard/DeviceCompactCardBinder.kt",
        UI / "tabs/settings/device/DeviceStatusAdapter.kt",
    )
    required = (
        "R.string.device_online",
        "R.string.device_offline",
        "contentDescription",
        "setStateDescription",
        "ACCESSIBILITY_LIVE_REGION_POLITE",
    )
    for path in files:
        source = path.read_text(encoding="utf-8")
        missing = [fragment for fragment in required if fragment not in source]
        if missing:
            fail(f"{path.relative_to(ROOT)} status semantics incomplete: {missing}")


def main() -> int:
    try:
        check_layouts()
        check_runtime_header_descriptions()
        check_dynamic_status_semantics()
    except (GuardFailure, ET.ParseError) as error:
        print(f"ACCESSIBILITY_SEMANTICS_GUARD_FAILED: {error}", file=sys.stderr)
        return 1

    print("ACCESSIBILITY_SEMANTICS_GUARD_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

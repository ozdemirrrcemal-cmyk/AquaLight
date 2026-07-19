#!/usr/bin/env python3
"""Static Android View accessibility checks for Stage 11."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
RES = APP / "res"
BASE_ACTIVITY = APP / "java" / "com" / "aqua" / "aqualight" / "base" / "BaseActivity.kt"
TOUCH_TARGET_HELPER = (
    APP
    / "java"
    / "com"
    / "aqua"
    / "aqualight"
    / "base"
    / "accessibility"
    / "TouchTargetExt.kt"
)
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
DIMEN_REFERENCE = re.compile(r"@dimen/([A-Za-z0-9_]+)$")
DP_VALUE = re.compile(r"(-?\d+(?:\.\d+)?)dp$")

INTERACTIVE_TAG_SUFFIXES = (
    "Button",
    "ImageButton",
    "CheckBox",
    "RadioButton",
    "Switch",
    "Chip",
    "FloatingActionButton",
)
ICON_ONLY_TAG_SUFFIXES = ("ImageButton", "FloatingActionButton")
RTL_FORBIDDEN_ATTRIBUTES = {
    "paddingLeft",
    "paddingRight",
    "layout_marginLeft",
    "layout_marginRight",
    "drawableLeft",
    "drawableRight",
    "layout_alignParentLeft",
    "layout_alignParentRight",
    "layout_toLeftOf",
    "layout_toRightOf",
}


def android_attr(element: ET.Element, name: str) -> str:
    return element.attrib.get(f"{ANDROID_NS}{name}", "").strip()


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].rsplit(".", 1)[-1]


def load_dimensions() -> dict[str, str]:
    dimensions: dict[str, str] = {}
    for path in sorted((RES / "values").glob("*dimens.xml")):
        root = ET.parse(path).getroot()
        for element in root.findall("dimen"):
            name = element.attrib.get("name")
            value = "".join(element.itertext()).strip()
            if name and value:
                dimensions[name] = value
    return dimensions


def resolve_dp(value: str, dimensions: dict[str, str], stack: tuple[str, ...] = ()) -> float | None:
    raw_match = DP_VALUE.fullmatch(value)
    if raw_match:
        return float(raw_match.group(1))
    reference = DIMEN_REFERENCE.fullmatch(value)
    if reference is None:
        return None
    name = reference.group(1)
    if name in stack:
        raise ValueError(f"Dimension alias cycle: {' -> '.join((*stack, name))}")
    target = dimensions.get(name)
    if target is None:
        raise ValueError(f"Missing dimension resource: {name}")
    return resolve_dp(target, dimensions, (*stack, name))


def is_interactive(element: ET.Element) -> bool:
    tag = local_name(element.tag)
    return (
        tag.endswith(INTERACTIVE_TAG_SUFFIXES)
        or android_attr(element, "clickable") == "true"
        or android_attr(element, "longClickable") == "true"
    )


def has_speakable_icon_description(element: ET.Element) -> bool:
    description = android_attr(element, "contentDescription")
    return bool(description and description != "@null")


def has_central_touch_target_contract() -> bool:
    activity = BASE_ACTIVITY.read_text(encoding="utf-8")
    helper = TOUCH_TARGET_HELPER.read_text(encoding="utf-8")
    return (
        "installAutomaticTouchTargets(window.decorView)" in activity
        and "const val MINIMUM_TOUCH_TARGET_DP = 48" in helper
        and "TouchDelegate" in helper
        and "addOnGlobalLayoutListener" in helper
    )


def is_explicitly_small(element: ET.Element, axis: str, dimensions: dict[str, str]) -> bool:
    size = resolve_dp(android_attr(element, f"layout_{axis}"), dimensions)
    minimum_value = android_attr(element, f"min{axis.capitalize()}")
    minimum = resolve_dp(minimum_value, dimensions) if minimum_value else None
    if size is None or size == 0.0 or size >= 48.0:
        return False
    return minimum is None or minimum < 48.0


def validate_layout(path: Path, dimensions: dict[str, str]) -> tuple[list[str], int]:
    errors: list[str] = []
    small_targets = 0
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        return [f"{path.relative_to(ROOT)}: malformed XML: {error}"], 0

    for element in root.iter():
        tag = local_name(element.tag)
        for qualified_name in element.attrib:
            attribute_name = qualified_name.rsplit("}", 1)[-1]
            if attribute_name in RTL_FORBIDDEN_ATTRIBUTES:
                errors.append(
                    f"{path.relative_to(ROOT)}: {tag} uses RTL-unsafe {attribute_name}; "
                    "use the start/end equivalent."
                )

        if not is_interactive(element):
            continue
        if android_attr(element, "importantForAccessibility") == "no":
            errors.append(
                f"{path.relative_to(ROOT)}: interactive {tag} must not be hidden from accessibility."
            )

        runtime_configured = android_attr(element, "visibility") == "gone"
        if not runtime_configured:
            if tag.endswith(ICON_ONLY_TAG_SUFFIXES) and not has_speakable_icon_description(element):
                errors.append(
                    f"{path.relative_to(ROOT)}: icon-only {tag} requires a contentDescription."
                )
            if tag.endswith("ImageView") and android_attr(element, "clickable") == "true":
                if not has_speakable_icon_description(element):
                    errors.append(
                        f"{path.relative_to(ROOT)}: clickable ImageView requires a contentDescription."
                    )

        if (
            is_explicitly_small(element, "width", dimensions)
            or is_explicitly_small(element, "height", dimensions)
        ):
            small_targets += 1

    return errors, small_targets


def main() -> int:
    try:
        dimensions = load_dimensions()
        central_touch_contract = has_central_touch_target_contract()
    except (ET.ParseError, OSError, ValueError) as error:
        print(f"Accessibility static guard failed:\n - {error}")
        return 1

    errors: list[str] = []
    small_target_count = 0
    for path in sorted((RES / "layout").glob("*.xml")):
        layout_errors, layout_small_targets = validate_layout(path, dimensions)
        errors.extend(layout_errors)
        small_target_count += layout_small_targets

    if small_target_count > 0 and not central_touch_contract:
        errors.append(
            f"Found {small_target_count} explicit sub-48dp controls without the central "
            "BaseActivity TouchDelegate contract."
        )

    if errors:
        print("Accessibility static guard failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(
        "Accessibility static guard passed: icon descriptions and RTL-safe attributes are intact; "
        f"{small_target_count} compact controls are protected by the central 48dp TouchDelegate."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

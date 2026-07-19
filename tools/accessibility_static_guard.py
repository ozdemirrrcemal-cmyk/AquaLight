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
JAVA = APP / "java"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
DIMEN_REFERENCE = re.compile(r"@dimen/([A-Za-z0-9_]+)$")
DP_VALUE = re.compile(r"(-?\d+(?:\.\d+)?)dp$")
TOUCH_DELEGATE_CALL = re.compile(r"\b([A-Za-z][A-Za-z0-9_]*)\.ensureMinimumTouchTarget\s*\(")

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


def resource_id(element: ET.Element) -> str:
    value = android_attr(element, "id")
    return value.rsplit("/", 1)[-1] if "/" in value else ""


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


def load_delegated_touch_targets() -> set[str]:
    delegated: set[str] = set()
    for path in sorted(JAVA.rglob("*.kt")):
        delegated.update(TOUCH_DELEGATE_CALL.findall(path.read_text(encoding="utf-8")))
    return delegated


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


def validate_touch_dimension(
    errors: list[str],
    path: Path,
    element: ET.Element,
    axis: str,
    dimensions: dict[str, str],
    delegated_targets: set[str],
) -> None:
    element_id = resource_id(element)
    if element_id in delegated_targets:
        return

    size = android_attr(element, f"layout_{axis}")
    minimum = android_attr(element, f"min{axis.capitalize()}")
    try:
        size_dp = resolve_dp(size, dimensions)
        minimum_dp = resolve_dp(minimum, dimensions) if minimum else None
    except ValueError as error:
        errors.append(f"{path.relative_to(ROOT)}: {error}")
        return

    if size_dp is None or size_dp == 0.0 or size_dp >= 48.0:
        return
    if minimum_dp is not None and minimum_dp >= 48.0:
        return

    errors.append(
        f"{path.relative_to(ROOT)}: {local_name(element.tag)} "
        f"{android_attr(element, 'id') or '<no-id>'} has {axis}={size_dp:g}dp "
        "without a 48dp minimum or ensureMinimumTouchTarget() contract."
    )


def validate_layout(
    path: Path,
    dimensions: dict[str, str],
    delegated_targets: set[str],
) -> list[str]:
    errors: list[str] = []
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        return [f"{path.relative_to(ROOT)}: malformed XML: {error}"]

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

        validate_touch_dimension(errors, path, element, "width", dimensions, delegated_targets)
        validate_touch_dimension(errors, path, element, "height", dimensions, delegated_targets)
    return errors


def main() -> int:
    try:
        dimensions = load_dimensions()
        delegated_targets = load_delegated_touch_targets()
    except (ET.ParseError, OSError, ValueError) as error:
        print(f"Accessibility static guard failed:\n - {error}")
        return 1

    errors: list[str] = []
    for path in sorted((RES / "layout").glob("*.xml")):
        errors.extend(validate_layout(path, dimensions, delegated_targets))

    if errors:
        print("Accessibility static guard failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(
        "Accessibility static guard passed: icon descriptions, RTL-safe attributes and "
        "48dp/touch-delegate contracts are intact."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

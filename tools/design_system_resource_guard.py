#!/usr/bin/env python3
"""Fail fast when product UI bypasses AquaLight design-system resources."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
RES = APP / "res"
JAVA = APP / "java"
TOOLS_NS = "http://schemas.android.com/tools"

HEX_LITERAL = re.compile(r"#[0-9A-Fa-f]{3,8}\b")
RAW_DIMENSION = re.compile(r"(?<![\w@])(?:-?\d+(?:\.\d+)?)(?:dp|sp)\b")
RAW_DP_CALL = re.compile(r"(?<![\w.])-?\d+(?:\.\d+)?f?\.dp\(\)")
RAW_COMPOSE_DP = re.compile(
    r"(?<![\w.])(?:-?\d+(?:\.\d+)?|[A-Za-z_][A-Za-z0-9_]*)\.dp\b"
)
RAW_KOTLIN_ARGB = re.compile(r"\b0x[0-9A-Fa-f]{6,8}\b")
RAW_TEXT_SIZE = re.compile(r"\btextSize\s*=\s*\d+(?:\.\d+)?f?\b")
RAW_UI_COPY = re.compile(
    r"\b(?:text|title|subtitle|message|description|contentDescription)\s*=\s*"
    r'"(?=[^"\n]*[A-Za-z])[^"\n]+"'
)
LEGACY_STYLE = re.compile(r"\b(?:RedButton|BlackButton|WhiteButton)\b")
PALETTE_NAME = re.compile(r"aqua_palette_hex_[0-9a-f]{6,8}")
SIZE_NAME = re.compile(r"aqua_size_(?:negative_)?\d+(?:_\d+)?")
ANDROID_COLOR_REFERENCE = re.compile(r"@android:color/[A-Za-z0-9_]+")
PRIMITIVE_XML_COLOR = re.compile(r"@color/aqua_palette_hex_[0-9a-f]{6,8}")
PRIMITIVE_KOTLIN_COLOR = re.compile(r"R\.color\.aqua_palette_hex_[0-9a-f]{6,8}")
KOTLIN_COLOR_CONSTANT = re.compile(r"\bColor\.(?:WHITE|BLACK|TRANSPARENT)\b")
VISIBLE_SYMBOL_LITERAL = re.compile(r'"[^"\n]*(?:✓|W/L/H|L/gal)[^"\n]*"')
VISIBLE_UNIT_INTERPOLATION = re.compile(
    r'"[^"\n]*\$(?:\{[^}]+}|[A-Za-z_][A-Za-z0-9_]*)[^"\n]*(?:\sW|\sL|\sH|\sgal)[^"\n]*"'
)

DOSING_UI_ROOT = JAVA / "com" / "aqua" / "aqualight" / "ui" / "tabs" / "devices" / "detail" / "dosing"
DOSING_COMPOSE_STYLE = (
    JAVA / "com" / "aqua" / "aqualight" / "ui" / "common" / "dosing" /
    "AquaDosingComposeStyle.kt"
)
COOLING_UI_ROOT = JAVA / "com" / "aqua" / "aqualight" / "ui" / "tabs" / "devices" / "detail" / "cooling"
COOLING_COMPOSE_STYLE = (
    JAVA / "com" / "aqua" / "aqualight" / "ui" / "common" / "cooling" /
    "AquaCoolingComposeStyle.kt"
)
COOLING_STRING_REFERENCE = re.compile(r"R\.string\.([A-Za-z0-9_]+)")

UI_COPY_ATTRIBUTE_NAMES = {
    "text",
    "hint",
    "contentDescription",
    "title",
    "label",
    "helperText",
    "placeholderText",
    "prefixText",
    "suffixText",
}

REQUIRED_COMPONENT_STYLES = {
    "Widget.Aqua.Button",
    "Widget.Aqua.Input.Layout",
    "Widget.Aqua.Card",
    "Widget.Aqua.Chip.Status",
    "Widget.Aqua.BottomSheet.Root",
    "Widget.Aqua.BottomSheet.Button.EditorSave",
    "Widget.Aqua.Card.Inline",
}


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def add_matches(errors: list[str], path: Path, text: str, pattern: re.Pattern[str], reason: str) -> None:
    for match in pattern.finditer(text):
        errors.append(f"{relative(path)}:{line_number(text, match.start())}: {reason}: {match.group(0)}")


def is_dimension_definition(path: Path) -> bool:
    return path.parent.name.startswith("values") and path.name.endswith("dimens.xml")


def is_color_definition_layer(path: Path) -> bool:
    return path.parent.name.startswith("values") and "color" in path.name


def validate_component_style(errors: list[str], path: Path, element: ET.Element) -> None:
    tag = element.tag.rsplit("}", 1)[-1]
    expected_prefixes: tuple[str, ...] | None = None
    if tag.endswith("MaterialButton"):
        expected_prefixes = ("@style/Widget.Aqua.Button", "@style/Widget.Aqua.BottomSheet.Button")
    elif tag.endswith("TextInputLayout"):
        expected_prefixes = ("@style/Widget.Aqua.Input",)
    elif tag.endswith("MaterialCardView"):
        expected_prefixes = ("@style/Widget.Aqua.Card",)
    if expected_prefixes is None:
        return
    style = element.attrib.get("style", "")
    if not style.startswith(expected_prefixes):
        errors.append(
            f"{relative(path)}: {tag} must use an Aqua component style; found {style or '<missing>'}"
        )


def validate_xml(errors: list[str]) -> set[str]:
    style_names: set[str] = set()
    definitions: dict[tuple[str, str, str], list[str]] = defaultdict(list)

    for path in sorted(RES.rglob("*.xml")):
        text = path.read_text(encoding="utf-8")
        if path.name != "palette_colors.xml":
            add_matches(errors, path, text, HEX_LITERAL, "raw color outside the primitive palette")
        if not is_dimension_definition(path):
            add_matches(errors, path, text, RAW_DIMENSION, "raw dp/sp outside a dimension resource")
        add_matches(errors, path, text, ANDROID_COLOR_REFERENCE, "platform color bypasses semantic tokens")
        if not is_color_definition_layer(path) and path.name != "palette_colors.xml":
            add_matches(errors, path, text, PRIMITIVE_XML_COLOR, "primitive palette bypasses semantic tokens")
        add_matches(errors, path, text, LEGACY_STYLE, "legacy button style")

        try:
            root = ET.fromstring(text)
        except ET.ParseError as error:
            errors.append(f"{relative(path)}: malformed XML: {error}")
            continue

        for element in root.iter():
            validate_component_style(errors, path, element)
            for attribute, value in element.attrib.items():
                namespace = attribute[1:].split("}", 1)[0] if attribute.startswith("{") else ""
                attribute_name = attribute.rsplit("}", 1)[-1]
                if namespace == TOOLS_NS or attribute_name not in UI_COPY_ATTRIBUTE_NAMES:
                    continue
                normalized = value.strip()
                if normalized.startswith(("@", "?")) or not re.search(r"[A-Za-z]", normalized):
                    continue
                errors.append(
                    f"{relative(path)}: raw Android UI copy in {attribute_name}: {value}"
                )

        if not path.parent.name.startswith("values"):
            continue
        for element in root:
            name = element.attrib.get("name")
            if not name:
                continue
            resource_type = element.attrib.get("type", element.tag)
            key = (path.parent.name, resource_type, name)
            definitions[key].append(relative(path))
            if resource_type == "style":
                style_names.add(name)

    for (configuration, resource_type, name), paths in sorted(definitions.items()):
        if len(paths) > 1:
            errors.append(
                f"duplicate {resource_type}/{name} in {configuration}: {', '.join(paths)}"
            )

    palette = RES / "values" / "palette_colors.xml"
    if palette.exists():
        root = ET.parse(palette).getroot()
        for color in root.findall("color"):
            name = color.attrib.get("name", "")
            if not PALETTE_NAME.fullmatch(name):
                errors.append(f"{relative(palette)}: non-standard primitive color name: {name}")

    dimensions = RES / "values" / "design_system_dimens.xml"
    if dimensions.exists():
        root = ET.parse(dimensions).getroot()
        for dimen in root.findall("dimen"):
            name = dimen.attrib.get("name", "")
            if not SIZE_NAME.fullmatch(name):
                errors.append(f"{relative(dimensions)}: non-standard primitive size name: {name}")

    missing_styles = REQUIRED_COMPONENT_STYLES - style_names
    if missing_styles:
        errors.append(f"missing component style contracts: {', '.join(sorted(missing_styles))}")

    return style_names


def validate_kotlin(errors: list[str]) -> None:
    ui_root = JAVA / "com" / "aqua" / "aqualight" / "ui"
    catalog_root = JAVA / "com" / "aqua" / "aqualight" / "data" / "aquarium" / "catalog"

    for path in sorted(JAVA.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        add_matches(errors, path, text, HEX_LITERAL, "raw Kotlin color")
        add_matches(errors, path, text, RAW_DP_CALL, "raw Kotlin dp conversion")
        add_matches(errors, path, text, PRIMITIVE_KOTLIN_COLOR, "primitive palette bypasses semantic tokens")
        add_matches(errors, path, text, KOTLIN_COLOR_CONSTANT, "raw platform color constant")
        add_matches(errors, path, text, LEGACY_STYLE, "legacy button style")
        add_matches(errors, path, text, VISIBLE_SYMBOL_LITERAL, "visible symbol/unit literal must use a resource")
        add_matches(errors, path, text, VISIBLE_UNIT_INTERPOLATION, "visible formatted unit must use a resource")
        if path.is_relative_to(DOSING_UI_ROOT):
            add_matches(
                errors,
                path,
                text,
                RAW_COMPOSE_DP,
                "Dosing Compose dimension bypasses the central style contract",
            )
            add_matches(
                errors,
                path,
                text,
                RAW_KOTLIN_ARGB,
                "Dosing Compose color bypasses the central style contract",
            )
        if "getIdentifier(" in text:
            errors.append(f"{relative(path)}: dynamic resource lookup requires an explicit audited contract")
        if "Color.parseColor(" in text or "Color.rgb(" in text or "Color.argb(" in text:
            # Color.argb is allowed only for runtime alpha composition; literal palettes are caught above.
            if "Color.parseColor(" in text:
                errors.append(f"{relative(path)}: Color.parseColor bypasses color resources")
            if "Color.rgb(" in text:
                errors.append(f"{relative(path)}: Color.rgb bypasses color resources")

        if path.is_relative_to(ui_root):
            if path.name != "TankPdfExporter.kt":
                add_matches(errors, path, text, RAW_TEXT_SIZE, "raw Android text size")
            for match in RAW_UI_COPY.finditer(text):
                quoted_value = match.group(0).split('"', 1)[1].rsplit('"', 1)[0]
                literal_copy = re.sub(r"\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*", "", quoted_value)
                if re.search(r"[A-Za-z]", literal_copy):
                    errors.append(
                        f"{relative(path)}:{line_number(text, match.start())}: "
                        f"raw user-facing Kotlin copy: {match.group(0)}"
                    )

        if path.is_relative_to(COOLING_UI_ROOT):
            for match in COOLING_STRING_REFERENCE.finditer(text):
                if not match.group(1).startswith("device_cooling_"):
                    errors.append(
                        f"{relative(path)}:{line_number(text, match.start())}: "
                        f"Cooling UI string must live in device_cooling_strings.xml: "
                        f"{match.group(0)}"
                    )
            if "object AquaCoolingPalette" in text or "fun aquaCoolingTextStyle" in text:
                errors.append(
                    f"{relative(path)}: Cooling style declarations must live in the central "
                    "AquaCoolingComposeStyle contract"
                )

        if path.is_relative_to(catalog_root):
            for match in re.finditer(r"\b(?:name|brand|categoryTitle)\s*=\s*\"", text):
                errors.append(
                    f"{relative(path)}:{line_number(text, match.start())}: catalog copy must use @StringRes"
                )
            for match in re.finditer(r"\b(?:keywords|keywordRes)\s*=\s*listOf\([^)]*\"", text, re.DOTALL):
                errors.append(
                    f"{relative(path)}:{line_number(text, match.start())}: catalog chip copy must use string resources"
                )

    if not DOSING_COMPOSE_STYLE.is_file():
        errors.append(
            f"{relative(DOSING_COMPOSE_STYLE)}: missing central Dosing Compose style contract"
        )
    if not COOLING_COMPOSE_STYLE.is_file():
        errors.append(
            f"{relative(COOLING_COMPOSE_STYLE)}: missing central Cooling Compose style contract"
        )


def validate_cooling_contract(errors: list[str]) -> None:
    root_layout = RES / "layout" / "fragment_device_cooling_root.xml"
    layout_text = root_layout.read_text(encoding="utf-8") if root_layout.exists() else ""
    if "@layout/layout_aqua_header" not in layout_text:
        errors.append(
            f"{relative(root_layout)}: Cooling root must use the central AquaHeader layout"
        )

    components = COOLING_UI_ROOT / "CoolingDashboardComponents.kt"
    component_text = components.read_text(encoding="utf-8") if components.exists() else ""
    if "AquaDeviceCardSurface" not in component_text:
        errors.append(
            f"{relative(components)}: Cooling cards must use the central AquaDeviceCardSurface"
        )


def validate_dynamic_resource_contract(errors: list[str]) -> None:
    keep_file = RES / "raw" / "aqua_dynamic_resource_keep.xml"
    expected = (
        "@layout/ucrop_activity_photobox",
        "@string/ucrop_label_edit_photo",
        "@dimen/aqua_size_negative_12",
    )
    if not keep_file.exists():
        errors.append(f"{relative(keep_file)}: missing dynamic resource keep contract")
    else:
        text = keep_file.read_text(encoding="utf-8")
        for resource in expected:
            if resource not in text:
                errors.append(f"{relative(keep_file)}: missing dynamic resource proof for {resource}")

    layout = RES / "layout" / "ucrop_activity_photobox.xml"
    if not layout.exists():
        errors.append(f"{relative(layout)}: missing dynamically loaded uCrop layout")
    elif "@dimen/aqua_size_negative_12" not in layout.read_text(encoding="utf-8"):
        errors.append(f"{relative(layout)}: missing audited uCrop dimension dependency")

    audit = ROOT / "docs" / "stage10-unused-resources-audit.md"
    if not audit.exists():
        errors.append(f"{relative(audit)}: missing unused-resource classification audit")


def validate_theme_contract(errors: list[str]) -> None:
    for path in (RES / "values" / "themes.xml", RES / "values-night" / "themes.xml"):
        text = path.read_text(encoding="utf-8")
        for item in ("android:windowLightStatusBar", "android:windowLightNavigationBar"):
            expected = f'<item name="{item}">false</item>'
            if expected not in text:
                errors.append(f"{relative(path)}: expected light system-bar icons on the dark bar surface")


def main() -> int:
    errors: list[str] = []
    validate_xml(errors)
    validate_kotlin(errors)
    validate_cooling_contract(errors)
    validate_dynamic_resource_contract(errors)
    validate_theme_contract(errors)

    if errors:
        print("Design-system resource guard failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Design-system resource guard passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Commercial localization and accessibility guard for AquaLight.

The guard intentionally allows empty values-xx staging folders. A staged locale becomes subject to
complete resource coverage only after it is enabled in SupportedLocaleRegistry/locales_config.xml.
"""

from __future__ import annotations

import collections
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
REGISTRY = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "aqua"
    / "aqualight"
    / "localization"
    / "SupportedLocaleRegistry.kt"
)
LOCALES_CONFIG = RES / "xml" / "locales_config.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
PLACEHOLDER_RE = re.compile(
    r"(?<!%)%(?!%)(?:(?P<index>\d+)\$)?[-+#, 0(<]*\d*(?:\.\d+)?(?P<type>[a-zA-Z])"
)
STAGING_LOCALES = {"tr", "de", "fr", "ru", "zh"}
RUNTIME_MANAGED_ICON_CONTROLS = {
    ("layout_aqua_header.xml", "@+id/btnActionOne"),
    ("layout_aqua_header.xml", "@+id/btnActionTwo"),
    ("layout_aqua_header.xml", "@+id/btnActionThree"),
    ("layout_aqua_header.xml", "@+id/btnFilledIconAction"),
}
HEADER_BINDER = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "aqua"
    / "aqualight"
    / "ui"
    / "common"
    / "header"
    / "AquaHeaderBindingExt.kt"
)


class GuardFailure(RuntimeError):
    pass


def fail(message: str) -> None:
    raise GuardFailure(message)


def xml_files(directory: Path) -> list[Path]:
    return sorted(path for path in directory.glob("*.xml") if path.is_file())


def element_text(element: ET.Element) -> str:
    return "".join(element.itertext())


def placeholders(text: str) -> collections.Counter[tuple[str, str]]:
    result: collections.Counter[tuple[str, str]] = collections.Counter()
    implicit_index = 0
    for match in PLACEHOLDER_RE.finditer(text):
        conversion = match.group("type").lower()
        if conversion == "n":
            continue
        explicit_index = match.group("index")
        if explicit_index is None:
            implicit_index += 1
            index = f"implicit:{implicit_index}"
        else:
            index = explicit_index
        result[(index, conversion)] += 1
    return result


def collect_text_resources(directory: Path) -> dict[str, collections.Counter[tuple[str, str]]]:
    resources: dict[str, collections.Counter[tuple[str, str]]] = {}
    for path in xml_files(directory):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            fail(f"Invalid XML in {path.relative_to(ROOT)}: {error}")
        if root.tag != "resources":
            continue
        for child in root:
            name = child.attrib.get("name")
            if not name:
                continue
            translatable = child.attrib.get("translatable", "true").lower() != "false"
            if not translatable:
                continue
            if child.tag == "string":
                resources[f"string:{name}"] = placeholders(element_text(child))
            elif child.tag == "plurals":
                for item in child.findall("item"):
                    quantity = item.attrib.get("quantity", "")
                    resources[f"plurals:{name}:{quantity}"] = placeholders(element_text(item))
    return resources


def enabled_locale_tags() -> set[str]:
    registry_text = REGISTRY.read_text(encoding="utf-8")
    default_match = re.search(
        r'const\s+val\s+DEFAULT_LANGUAGE_TAG\s*=\s*"([^"]+)"',
        registry_text,
    )
    if not default_match:
        fail("SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG is missing")
    tags = {default_match.group(1)}
    tags.update(re.findall(r'languageTag\s*=\s*"([^"]+)"', registry_text))

    config_root = ET.parse(LOCALES_CONFIG).getroot()
    config_tags = {
        item.attrib[f"{ANDROID_NS}name"]
        for item in config_root.findall("locale")
        if f"{ANDROID_NS}name" in item.attrib
    }
    if tags != config_tags:
        fail(
            "SupportedLocaleRegistry and locales_config.xml disagree: "
            f"registry={sorted(tags)}, config={sorted(config_tags)}"
        )
    return tags


def locale_directories() -> dict[str, Path]:
    found: dict[str, Path] = {}
    for directory in RES.glob("values-*"):
        if not directory.is_dir():
            continue
        match = re.fullmatch(r"values-([a-z]{2,3})(?:-r[A-Z]{2})?", directory.name)
        if match:
            found[match.group(1)] = directory
    return found


def check_translation_resources(enabled: set[str]) -> None:
    base = collect_text_resources(RES / "values")
    if not base:
        fail("No translatable base resources were found")

    directories = locale_directories()
    missing_staging = STAGING_LOCALES.difference(directories)
    if missing_staging:
        fail(f"Missing translation staging folders: {sorted(missing_staging)}")

    for language, directory in sorted(directories.items()):
        localized = collect_text_resources(directory)
        unknown = localized.keys() - base.keys()
        if unknown:
            fail(
                f"{directory.name} contains resources absent from the base catalog: "
                f"{sorted(unknown)[:10]}"
            )
        for key, localized_tokens in localized.items():
            if localized_tokens != base[key]:
                fail(
                    f"Placeholder mismatch for {key} in {directory.name}: "
                    f"base={base[key]}, localized={localized_tokens}"
                )

        if language in enabled and language != "en":
            missing = base.keys() - localized.keys()
            if missing:
                fail(
                    f"Enabled locale {language} is incomplete; missing "
                    f"{len(missing)} resources. First entries: {sorted(missing)[:10]}"
                )


def dp_value(raw: str | None) -> float | None:
    if raw is None:
        return None
    match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)dp", raw.strip())
    return float(match.group(1)) if match else None


def check_runtime_managed_header_descriptions() -> None:
    source = HEADER_BINDER.read_text(encoding="utf-8")
    required_assignments = [
        "button.contentDescription =",
        "action.contentDescription",
        "btnFilledIconAction.contentDescription =",
        "filledIconAction.contentDescription",
    ]
    missing = [token for token in required_assignments if token not in source]
    if missing:
        fail(f"Runtime-managed header icon descriptions are incomplete: {missing}")


def check_icon_controls_and_explicit_touch_targets() -> None:
    check_runtime_managed_header_descriptions()
    failures: list[str] = []
    for path in sorted((RES / "layout").glob("*.xml")):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            fail(f"Invalid layout XML in {path.relative_to(ROOT)}: {error}")
        for view in root.iter():
            simple_name = view.tag.rsplit(".", 1)[-1]
            clickable = view.attrib.get(f"{ANDROID_NS}clickable") == "true"
            focusable = view.attrib.get(f"{ANDROID_NS}focusable") == "true"
            icon_control = simple_name == "ImageButton" or (
                simple_name == "ImageView" and (clickable or focusable)
            )
            if not icon_control:
                continue

            ignored = view.attrib.get(f"{ANDROID_NS}importantForAccessibility") == "no"
            description = view.attrib.get(f"{ANDROID_NS}contentDescription", "").strip()
            view_id = view.attrib.get(f"{ANDROID_NS}id", simple_name)
            location = f"{path.relative_to(ROOT)}:{view_id}"
            runtime_managed = (path.name, view_id) in RUNTIME_MANAGED_ICON_CONTROLS
            if not runtime_managed and not ignored and (not description or description == "@null"):
                failures.append(f"{location} has no accessibility description")

            width = dp_value(view.attrib.get(f"{ANDROID_NS}layout_width"))
            height = dp_value(view.attrib.get(f"{ANDROID_NS}layout_height"))
            min_width = dp_value(view.attrib.get(f"{ANDROID_NS}minWidth")) or 0.0
            min_height = dp_value(view.attrib.get(f"{ANDROID_NS}minHeight")) or 0.0
            if width is not None and max(width, min_width) < 48.0:
                failures.append(f"{location} has an explicit width below 48dp")
            if height is not None and max(height, min_height) < 48.0:
                failures.append(f"{location} has an explicit height below 48dp")

    if failures:
        fail("Icon-control accessibility violations:\n- " + "\n- ".join(failures[:30]))


def parse_color_catalog(directory: Path, base: dict[str, str] | None = None) -> dict[str, str]:
    catalog = dict(base or {})
    for path in xml_files(directory):
        root = ET.parse(path).getroot()
        if root.tag != "resources":
            continue
        for color in root.findall("color"):
            name = color.attrib.get("name")
            value = element_text(color).strip()
            if name and value:
                catalog[name] = value
    return catalog


def resolve_color(name: str, catalog: dict[str, str], stack: tuple[str, ...] = ()) -> str:
    if name in stack:
        fail(f"Circular color reference: {' -> '.join(stack + (name,))}")
    raw = catalog.get(name)
    if raw is None:
        fail(f"Missing color resource: {name}")
    if raw.startswith("@color/"):
        return resolve_color(raw.removeprefix("@color/"), catalog, stack + (name,))
    if not re.fullmatch(r"#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}", raw):
        fail(f"Unsupported color value for {name}: {raw}")
    if len(raw) == 9:
        alpha = raw[1:3]
        if alpha.lower() != "ff":
            fail(f"Contrast audit requires an opaque color for {name}: {raw}")
        raw = "#" + raw[3:]
    return raw


def relative_luminance(hex_color: str) -> float:
    channels = [int(hex_color[index : index + 2], 16) / 255.0 for index in (1, 3, 5)]
    linear = [
        value / 12.92 if value <= 0.03928 else ((value + 0.055) / 1.055) ** 2.4
        for value in channels
    ]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast_ratio(foreground: str, background: str) -> float:
    first = relative_luminance(foreground)
    second = relative_luminance(background)
    lighter, darker = max(first, second), min(first, second)
    return (lighter + 0.05) / (darker + 0.05)


def check_contrast() -> None:
    base = parse_color_catalog(RES / "values")
    night = parse_color_catalog(RES / "values-night", base)
    checks = [
        ("light-primary-text", base, "aqua_content_primary", "background_color", 4.5),
        ("light-muted-text", base, "aqua_content_muted", "background_color", 4.5),
        ("light-primary-action", base, "md_theme_light_onPrimary", "md_theme_light_primary", 3.0),
        ("positive-status", base, "aqua_accent_positive", "aqua_surface_positive", 4.5),
        ("dark-primary-text", night, "md_theme_dark_onSurface", "md_theme_dark_surface", 4.5),
    ]
    failures: list[str] = []
    for label, catalog, foreground_name, background_name, minimum in checks:
        foreground = resolve_color(foreground_name, catalog)
        background = resolve_color(background_name, catalog)
        ratio = contrast_ratio(foreground, background)
        if ratio + 1e-9 < minimum:
            failures.append(
                f"{label}: {foreground_name} on {background_name} is {ratio:.2f}:1; "
                f"minimum is {minimum:.1f}:1"
            )
    if failures:
        fail("WCAG contrast violations:\n- " + "\n- ".join(failures))


def check_dynamic_device_status_accessibility() -> None:
    files = [
        ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "aqua"
        / "aqualight"
        / "ui"
        / "common"
        / "devicecard"
        / "DeviceCompactCardBinder.kt",
        ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "aqua"
        / "aqualight"
        / "ui"
        / "tabs"
        / "settings"
        / "device"
        / "DeviceStatusAdapter.kt",
    ]
    for path in files:
        text = path.read_text(encoding="utf-8")
        required = ["R.string.device_online", "R.string.device_offline", "contentDescription"]
        missing = [token for token in required if token not in text]
        if missing:
            fail(f"Dynamic device status accessibility is incomplete in {path}: {missing}")


def main() -> int:
    try:
        enabled = enabled_locale_tags()
        check_translation_resources(enabled)
        check_icon_controls_and_explicit_touch_targets()
        check_dynamic_device_status_accessibility()
        check_contrast()
    except GuardFailure as error:
        print(f"LOCALIZATION_ACCESSIBILITY_GUARD_FAILED: {error}", file=sys.stderr)
        return 1

    print(
        "LOCALIZATION_ACCESSIBILITY_GUARD_PASS "
        f"enabled_locales={','.join(sorted(enabled))} "
        f"staging_locales={','.join(sorted(STAGING_LOCALES))}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

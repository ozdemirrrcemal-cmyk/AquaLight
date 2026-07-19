#!/usr/bin/env python3
"""Commercial localization and accessibility release guard for AquaLight."""

from __future__ import annotations

import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
RES = APP / "res"
JAVA = APP / "java"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID = f"{{{ANDROID_NS}}}"

PLACEHOLDER = re.compile(
    r"%(?!%)(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])"
)
STRING_LITERAL = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')


class GuardFailure(Exception):
    pass


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def parse_xml(path: Path) -> ET.Element:
    try:
        return ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        raise GuardFailure(f"{relative(path)} could not be parsed: {error}") from error


def locale_config_tags(errors: list[str]) -> set[str]:
    path = RES / "xml" / "locales_config.xml"
    if not path.exists():
        errors.append(f"{relative(path)} is missing")
        return set()

    root = parse_xml(path)
    tags = {
        element.attrib.get(f"{ANDROID}name", "").strip()
        for element in root.findall("locale")
    }
    tags.discard("")
    if not tags:
        errors.append(f"{relative(path)} must declare at least one locale")
    return tags


def registry_tags(errors: list[str]) -> set[str]:
    path = JAVA / "com" / "aqua" / "aqualight" / "i18n" / "SupportedLocaleRegistry.kt"
    if not path.exists():
        errors.append(f"{relative(path)} is missing")
        return set()

    text = path.read_text(encoding="utf-8")
    default_match = re.search(
        r'const\s+val\s+DEFAULT_LANGUAGE_TAG\s*=\s*"([^"]+)"',
        text,
    )
    block_match = re.search(
        r"supportedLanguageTags\s*=\s*linkedSetOf\((.*?)\)",
        text,
        re.DOTALL,
    )
    if default_match is None or block_match is None:
        errors.append(f"{relative(path)} does not expose the expected registry contract")
        return set()

    default = default_match.group(1)
    block = block_match.group(1)
    tags = {match.group(1) for match in STRING_LITERAL.finditer(block)}
    if "DEFAULT_LANGUAGE_TAG" in block:
        tags.add(default)
    if default not in tags:
        errors.append("SupportedLocaleRegistry default must be included in supported locales")
    return tags


def resource_entries(directory: Path) -> dict[tuple[str, str], str]:
    entries: dict[tuple[str, str], str] = {}
    if not directory.exists():
        return entries

    for path in sorted(directory.glob("*.xml")):
        root = parse_xml(path)
        if root.tag != "resources":
            continue
        for element in root:
            name = element.attrib.get("name", "").strip()
            if not name or element.attrib.get("translatable") == "false":
                continue
            if element.tag not in {"string", "plurals", "string-array"}:
                continue
            entries[(element.tag, name)] = "".join(element.itertext())
    return entries


def qualifier_directory(language_tag: str) -> Path:
    parts = language_tag.split("-")
    if len(parts) == 1:
        return RES / f"values-{parts[0]}"
    return RES / ("values-b+" + "+".join(parts))


def placeholder_signature(value: str) -> list[tuple[str, str]]:
    signature: list[tuple[str, str]] = []
    implicit_position = 1
    for match in PLACEHOLDER.finditer(value):
        position = match.group(1)
        if position is None:
            position = str(implicit_position)
            implicit_position += 1
        signature.append((position, match.group(2).lower()))
    return signature


def validate_localized_resources(
    errors: list[str],
    supported_tags: set[str],
) -> None:
    base = resource_entries(RES / "values")
    if not base:
        errors.append("base values resources do not contain translatable strings")
        return

    for language_tag in sorted(supported_tags):
        if language_tag == "en":
            localized = base
        else:
            directory = qualifier_directory(language_tag)
            localized = resource_entries(directory)
            if not localized:
                errors.append(
                    f"supported locale {language_tag} requires complete resources in "
                    f"{relative(directory)}"
                )
                continue

        missing = sorted(set(base) - set(localized))
        for resource_type, name in missing:
            errors.append(
                f"locale {language_tag} is missing {resource_type}/{name}"
            )

        for key in sorted(set(base) & set(localized)):
            base_signature = placeholder_signature(base[key])
            localized_signature = placeholder_signature(localized[key])
            if base_signature != localized_signature:
                resource_type, name = key
                errors.append(
                    f"locale {language_tag} placeholder mismatch for "
                    f"{resource_type}/{name}: {base_signature} != {localized_signature}"
                )


def validate_manifest(errors: list[str]) -> None:
    path = APP / "AndroidManifest.xml"
    root = parse_xml(path)
    application = root.find("application")
    if application is None:
        errors.append("AndroidManifest.xml has no application element")
        return
    if application.attrib.get(f"{ANDROID}localeConfig") != "@xml/locales_config":
        errors.append("application must reference @xml/locales_config")
    if application.attrib.get(f"{ANDROID}supportsRtl") != "true":
        errors.append("application must keep android:supportsRtl=true")


def validate_icon_descriptions(errors: list[str]) -> None:
    allowed_dynamic_header_ids = {
        "btnActionOne",
        "btnActionTwo",
        "btnActionThree",
        "btnFilledIconAction",
    }

    for path in sorted((RES / "layout").glob("*.xml")):
        root = parse_xml(path)
        for element in root.iter():
            tag = element.tag.rsplit("}", 1)[-1]
            if not tag.endswith("ImageButton"):
                continue

            view_id = element.attrib.get(f"{ANDROID}id", "").rsplit("/", 1)[-1]
            description = element.attrib.get(f"{ANDROID}contentDescription", "").strip()
            if not description and view_id not in allowed_dynamic_header_ids:
                errors.append(
                    f"{relative(path)} icon-only control {view_id or '<no-id>'} "
                    "requires a content description"
                )

    binding_path = (
        JAVA
        / "com"
        / "aqua"
        / "aqualight"
        / "ui"
        / "common"
        / "header"
        / "AquaHeaderBindingExt.kt"
    )
    binding = binding_path.read_text(encoding="utf-8")
    required_fragments = (
        "button.contentDescription",
        "action.contentDescription",
        "btnFilledIconAction.contentDescription",
        "filledIconAction.contentDescription",
        "btnCardIconAction.contentDescription",
        "cardIconAction.contentDescription",
    )
    for fragment in required_fragments:
        if fragment not in binding:
            errors.append(
                f"{relative(binding_path)} is missing dynamic icon label contract: {fragment}"
            )


def validate_dynamic_device_status(errors: list[str]) -> None:
    paths = (
        JAVA
        / "com"
        / "aqua"
        / "aqualight"
        / "ui"
        / "common"
        / "devicecard"
        / "DeviceCompactCardBinder.kt",
        JAVA
        / "com"
        / "aqua"
        / "aqualight"
        / "ui"
        / "tabs"
        / "settings"
        / "device"
        / "DeviceStatusAdapter.kt",
    )
    for path in paths:
        text = path.read_text(encoding="utf-8")
        if not re.search(
            r"ivPresenceIcon\.contentDescription\s*=\s*presenceText",
            text,
        ):
            errors.append(
                f"{relative(path)} must expose Online/Offline through contentDescription"
            )
        if not re.search(r"root\.contentDescription\s*=", text):
            errors.append(
                f"{relative(path)} must expose the complete device status row description"
            )


def validate_touch_target_contract(errors: list[str]) -> None:
    installer = (
        JAVA
        / "com"
        / "aqua"
        / "aqualight"
        / "ui"
        / "common"
        / "accessibility"
        / "MinimumTouchTargetInstaller.kt"
    )
    runtime = installer.with_name("AccessibilityRuntimeInstaller.kt")
    app = JAVA / "com" / "aqua" / "aqualight" / "app" / "AquaApp.kt"

    for path in (installer, runtime, app):
        if not path.exists():
            errors.append(f"{relative(path)} is missing")
            return

    installer_text = installer.read_text(encoding="utf-8")
    if "MIN_TOUCH_TARGET_DP = 48" not in installer_text:
        errors.append("minimum touch target contract must remain 48dp")
    if "TouchDelegate" not in installer_text:
        errors.append("minimum touch targets must expand hit areas without resizing views")

    app_text = app.read_text(encoding="utf-8")
    if "registerActivityLifecycleCallbacks" not in app_text:
        errors.append("AquaApp must install accessibility lifecycle callbacks")
    if "AccessibilityRuntimeInstaller()" not in app_text:
        errors.append("AquaApp must register AccessibilityRuntimeInstaller")


def parse_base_colors() -> dict[str, str]:
    colors: dict[str, str] = {}
    for path in sorted((RES / "values").glob("*.xml")):
        root = parse_xml(path)
        if root.tag != "resources":
            continue
        for element in root.findall("color"):
            name = element.attrib.get("name", "").strip()
            value = "".join(element.itertext()).strip()
            if name:
                colors[name] = value
    return colors


def resolve_color(name: str, colors: dict[str, str], seen: set[str] | None = None) -> str:
    seen = set() if seen is None else seen
    if name in seen:
        raise GuardFailure(f"cyclic color alias at {name}")
    seen.add(name)

    value = colors.get(name)
    if value is None:
        raise GuardFailure(f"missing color resource {name}")
    if value.startswith("@color/"):
        return resolve_color(value.split("/", 1)[1], colors, seen)
    return value


def rgb_from_hex(value: str) -> tuple[int, int, int]:
    normalized = value.lstrip("#")
    if len(normalized) == 8:
        normalized = normalized[2:]
    if len(normalized) != 6:
        raise GuardFailure(f"unsupported contrast color {value}")
    return tuple(int(normalized[index : index + 2], 16) for index in (0, 2, 4))


def luminance(rgb: tuple[int, int, int]) -> float:
    channels = []
    for channel in rgb:
        value = channel / 255.0
        channels.append(
            value / 12.92
            if value <= 0.04045
            else math.pow((value + 0.055) / 1.055, 2.4)
        )
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]


def contrast_ratio(first: tuple[int, int, int], second: tuple[int, int, int]) -> float:
    first_luminance = luminance(first)
    second_luminance = luminance(second)
    lighter = max(first_luminance, second_luminance)
    darker = min(first_luminance, second_luminance)
    return (lighter + 0.05) / (darker + 0.05)


def validate_contrast(errors: list[str]) -> None:
    colors = parse_base_colors()
    pairs = (
        ("aqua_card_text_primary", "aqua_card_surface"),
        ("aqua_card_text_secondary", "aqua_card_surface"),
        ("md_theme_light_onSurface", "md_theme_light_background"),
        ("md_theme_dark_onSurface", "md_theme_dark_background"),
    )

    for foreground, background in pairs:
        try:
            ratio = contrast_ratio(
                rgb_from_hex(resolve_color(foreground, colors)),
                rgb_from_hex(resolve_color(background, colors)),
            )
        except GuardFailure as error:
            errors.append(str(error))
            continue
        if ratio < 4.5:
            errors.append(
                f"WCAG AA contrast failed for {foreground} on {background}: {ratio:.2f}:1"
            )


def validate_locale_formatting(errors: list[str]) -> None:
    formatter = JAVA / "com" / "aqua" / "aqualight" / "i18n" / "LocaleFormatter.kt"
    if not formatter.exists():
        errors.append(f"{relative(formatter)} is missing")
        return

    dimension_formatter = (
        JAVA
        / "com"
        / "aqua"
        / "aqualight"
        / "ui"
        / "tabs"
        / "aquarium"
        / "common"
        / "AquariumDimensionFormatter.kt"
    )
    dimension_text = dimension_formatter.read_text(encoding="utf-8")
    if "Locale.US" in dimension_text or "DecimalFormatSymbols" in dimension_text:
        errors.append(
            f"{relative(dimension_formatter)} must not force US display formatting"
        )
    if "LocaleFormatter" not in dimension_text:
        errors.append(
            f"{relative(dimension_formatter)} must use the locale formatting boundary"
        )


def validate_visual_smoke_contract(errors: list[str]) -> None:
    script = ROOT / "tools" / "run_release_smoke.sh"
    text = script.read_text(encoding="utf-8")
    for required in (
        "large-font-light",
        "large-font-dark",
        "rtl-light",
        "rtl-dark",
        "font_scale",
        "debug.force_rtl",
    ):
        if required not in text:
            errors.append(
                f"{relative(script)} is missing visual accessibility profile {required}"
            )


def main() -> int:
    errors: list[str] = []

    try:
        configured = locale_config_tags(errors)
        registered = registry_tags(errors)
        if configured != registered:
            errors.append(
                f"locale registry {sorted(registered)} does not match locale config "
                f"{sorted(configured)}"
            )

        validate_manifest(errors)
        validate_localized_resources(errors, registered)
        validate_locale_formatting(errors)
        validate_icon_descriptions(errors)
        validate_dynamic_device_status(errors)
        validate_touch_target_contract(errors)
        validate_visual_smoke_contract(errors)
        validate_contrast(errors)
    except GuardFailure as error:
        errors.append(str(error))

    if errors:
        print("Localization/accessibility guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("Localization/accessibility guard passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

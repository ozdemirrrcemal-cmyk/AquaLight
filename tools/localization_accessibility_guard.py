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
ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED_SUPPORTED_LOCALES = {"en", "tr"}
REMOVED_LANGUAGE_ARTIFACTS = (
    RES / "drawable" / "flag_de.png",
    RES / "drawable" / "flag_fr.png",
    RES / "drawable" / "flag_ru.png",
    RES / "drawable" / "flag_cn.png",
)
REMOVED_LANGUAGE_RESOURCE_PREFIXES = (
    "language_german",
    "language_french",
    "language_russian",
    "language_chinese",
)
PLACEHOLDER = re.compile(
    r"%(?!%)(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])"
)
STRING_LITERAL = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')
CONST_STRING = re.compile(
    r'const\s+val\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"\\]*(?:\\.[^"\\]*)*)"'
)
CONST_REFERENCE = re.compile(r"\b[A-Z][A-Z0-9_]*\b")


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

    tags = {
        node.attrib.get(f"{ANDROID}name", "").strip()
        for node in parse_xml(path).findall("locale")
    }
    tags.discard("")
    if not tags:
        errors.append(f"{relative(path)} must declare at least one locale")
    return tags


def registry_tags(errors: list[str]) -> set[str]:
    path = JAVA / "com/aqua/aqualight/i18n/SupportedLocaleRegistry.kt"
    if not path.exists():
        errors.append(f"{relative(path)} is missing")
        return set()

    text = path.read_text(encoding="utf-8")
    constants = dict(CONST_STRING.findall(text))
    default_match = re.search(
        r"const\s+val\s+DEFAULT_LANGUAGE_TAG\s*=\s*"
        r'(?:"([^"\\]*(?:\\.[^"\\]*)*)"|([A-Z][A-Z0-9_]*))',
        text,
    )
    block_match = re.search(
        r"supportedLanguageTags\s*=\s*linkedSetOf\((.*?)\)",
        text,
        re.DOTALL,
    )
    if default_match is None or block_match is None:
        errors.append(f"{relative(path)} does not expose the registry contract")
        return set()

    default_literal, default_reference = default_match.groups()
    default = default_literal or constants.get(default_reference or "")
    if default is None:
        errors.append("SupportedLocaleRegistry default constant cannot be resolved")
        return set()

    block = block_match.group(1)
    tags = {match.group(1) for match in STRING_LITERAL.finditer(block)}
    for reference in CONST_REFERENCE.findall(block):
        value = constants.get(reference)
        if value is not None:
            tags.add(value)

    if default not in tags:
        errors.append("SupportedLocaleRegistry default must be supported")
    return tags


def resource_entries(directory: Path) -> dict[tuple[str, str], str]:
    entries: dict[tuple[str, str], str] = {}
    source_paths: dict[tuple[str, str], Path] = {}
    if not directory.exists():
        return entries

    for path in sorted(directory.glob("*.xml")):
        root = parse_xml(path)
        if root.tag != "resources":
            continue
        for node in root:
            name = node.attrib.get("name", "").strip()
            if not name or node.attrib.get("translatable") == "false":
                continue
            if node.tag not in {"string", "plurals", "string-array"}:
                continue

            key = (node.tag, name)
            previous_path = source_paths.get(key)
            if previous_path is not None:
                raise GuardFailure(
                    f"duplicate {node.tag}/{name} in {relative(previous_path)} "
                    f"and {relative(path)}"
                )
            source_paths[key] = path
            entries[key] = "".join(node.itertext())
    return entries


def qualifier_directory(language_tag: str) -> Path:
    parts = language_tag.split("-")
    return (
        RES / f"values-{parts[0]}"
        if len(parts) == 1
        else RES / ("values-b+" + "+".join(parts))
    )


def placeholder_signature(value: str) -> list[tuple[str, str]]:
    signature: list[tuple[str, str]] = []
    implicit_position = 1
    value_without_escaped_percent = value.replace("%%", "")
    for match in PLACEHOLDER.finditer(value_without_escaped_percent):
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
        errors.append("base values resources have no translatable entries")
        return

    for language_tag in sorted(supported_tags):
        if language_tag == "en":
            localized = base
        else:
            directory = qualifier_directory(language_tag)
            localized = resource_entries(directory)
            if not localized:
                errors.append(
                    f"supported locale {language_tag} requires a complete "
                    f"{relative(directory)} resource pack"
                )
                continue

        for resource_type, name in sorted(set(base) - set(localized)):
            errors.append(
                f"locale {language_tag} is missing {resource_type}/{name}"
            )

        for resource_type, name in sorted(set(localized) - set(base)):
            errors.append(
                f"locale {language_tag} contains unknown {resource_type}/{name}"
            )

        for key in sorted(set(base) & set(localized)):
            expected = placeholder_signature(base[key])
            actual = placeholder_signature(localized[key])
            if expected != actual:
                resource_type, name = key
                errors.append(
                    f"locale {language_tag} placeholder mismatch for "
                    f"{resource_type}/{name}: {expected} != {actual}"
                )


def validate_removed_languages(errors: list[str]) -> None:
    for path in REMOVED_LANGUAGE_ARTIFACTS:
        if path.exists():
            errors.append(f"unsupported language asset must be removed: {relative(path)}")

    for directory in (RES / "values", RES / "values-tr"):
        if not directory.exists():
            continue
        for path in sorted(directory.glob("*.xml")):
            root = parse_xml(path)
            if root.tag != "resources":
                continue
            for node in root:
                name = node.attrib.get("name", "").strip()
                if name.startswith(REMOVED_LANGUAGE_RESOURCE_PREFIXES):
                    errors.append(
                        f"unsupported language resource must be removed: "
                        f"{relative(path)} {node.tag}/{name}"
                    )


def validate_manifest(errors: list[str]) -> None:
    path = APP / "AndroidManifest.xml"
    application = parse_xml(path).find("application")
    if application is None:
        errors.append("AndroidManifest.xml has no application element")
        return
    if application.attrib.get(f"{ANDROID}localeConfig") != "@xml/locales_config":
        errors.append("application must reference @xml/locales_config")
    if application.attrib.get(f"{ANDROID}supportsRtl") != "true":
        errors.append("application must keep android:supportsRtl=true")


def validate_icon_descriptions(errors: list[str]) -> None:
    dynamic_header_ids = {
        "btnActionOne",
        "btnActionTwo",
        "btnActionThree",
        "btnFilledIconAction",
    }

    for path in sorted((RES / "layout").glob("*.xml")):
        for node in parse_xml(path).iter():
            tag = node.tag.rsplit("}", 1)[-1]
            if not tag.endswith("ImageButton"):
                continue
            view_id = node.attrib.get(f"{ANDROID}id", "").rsplit("/", 1)[-1]
            description = node.attrib.get(f"{ANDROID}contentDescription", "").strip()
            if not description and view_id not in dynamic_header_ids:
                errors.append(
                    f"{relative(path)} icon-only control {view_id or '<no-id>'} "
                    "requires a content description"
                )

    binding_directory = JAVA / "com/aqua/aqualight/ui/common/header"
    binding_paths = sorted(binding_directory.glob("AquaHeader*BindingExt.kt"))
    binding = "\n".join(
        path.read_text(encoding="utf-8") for path in binding_paths
    )
    assignments = (
        ("button.contentDescription", "action?.contentDescription"),
        ("btnFilledIconAction.contentDescription", "action?.contentDescription"),
        ("btnCardIconAction.contentDescription", "action?.contentDescription"),
    )
    for target, source in assignments:
        if target not in binding or source not in binding:
            errors.append(
                f"{relative(binding_directory)} is missing dynamic icon label assignment "
                f"{target} <- {source}"
            )


def validate_dynamic_device_status(errors: list[str]) -> None:
    paths = (
        JAVA / "com/aqua/aqualight/ui/common/devicecard/DeviceCompactCardBinder.kt",
        JAVA / "com/aqua/aqualight/ui/tabs/settings/device/DeviceStatusAdapter.kt",
    )
    for path in paths:
        text = path.read_text(encoding="utf-8")
        if not re.search(
            r"ivPresenceIcon\.contentDescription\s*=\s*presenceText",
            text,
        ):
            errors.append(
                f"{relative(path)} must announce dynamic Online/Offline state"
            )
        if not re.search(r"root\.contentDescription\s*=", text):
            errors.append(
                f"{relative(path)} must expose a complete row description"
            )


def validate_touch_target_contract(errors: list[str]) -> None:
    directory = JAVA / "com/aqua/aqualight/base/accessibility"
    installer = directory / "MinimumTouchTargetInstaller.kt"
    runtime = directory / "AccessibilityRuntimeInstaller.kt"
    app = JAVA / "com/aqua/aqualight/app/AquaApp.kt"

    for path in (installer, runtime, app):
        if not path.exists():
            errors.append(f"{relative(path)} is missing")
            return

    installer_text = installer.read_text(encoding="utf-8")
    runtime_text = runtime.read_text(encoding="utf-8")
    if "MIN_TOUCH_TARGET_DP = 48" not in installer_text:
        errors.append("minimum touch target contract must remain 48dp")
    if "TouchDelegate" not in installer_text:
        errors.append("touch targets must expand without resizing rendered controls")
    if "offsetDescendantRectToMyCoords" not in installer_text:
        errors.append("touch target expansion must use root coordinates")
    if "MinimumTouchTargetInstaller.install" not in runtime_text:
        errors.append("accessibility runtime must install minimum touch targets")

    app_text = app.read_text(encoding="utf-8")
    expected_import = (
        "import com.aqua.aqualight.base.accessibility.AccessibilityRuntimeInstaller"
    )
    if expected_import not in app_text:
        errors.append("AquaApp must install accessibility from the base layer")
    if "registerActivityLifecycleCallbacks" not in app_text:
        errors.append("AquaApp must register accessibility lifecycle callbacks")


def parse_base_colors() -> dict[str, str]:
    colors: dict[str, str] = {}
    for path in sorted((RES / "values").glob("*.xml")):
        root = parse_xml(path)
        if root.tag != "resources":
            continue
        for node in root.findall("color"):
            name = node.attrib.get("name", "").strip()
            if name:
                colors[name] = "".join(node.itertext()).strip()
    return colors


def resolve_color(
    name: str,
    colors: dict[str, str],
    seen: set[str] | None = None,
) -> str:
    visited = set() if seen is None else seen
    if name in visited:
        raise GuardFailure(f"cyclic color alias at {name}")
    visited.add(name)

    value = colors.get(name)
    if value is None:
        raise GuardFailure(f"missing color resource {name}")
    if value.startswith("@color/"):
        return resolve_color(value.split("/", 1)[1], colors, visited)
    return value


def rgb_from_hex(value: str) -> tuple[int, int, int]:
    normalized = value.lstrip("#")
    if len(normalized) == 8:
        normalized = normalized[2:]
    if len(normalized) != 6:
        raise GuardFailure(f"unsupported contrast color {value}")
    return tuple(
        int(normalized[index : index + 2], 16)
        for index in (0, 2, 4)
    )


def luminance(rgb: tuple[int, int, int]) -> float:
    channels: list[float] = []
    for channel in rgb:
        value = channel / 255.0
        channels.append(
            value / 12.92
            if value <= 0.04045
            else math.pow((value + 0.055) / 1.055, 2.4)
        )
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]


def contrast_ratio(
    first: tuple[int, int, int],
    second: tuple[int, int, int],
) -> float:
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
                f"WCAG AA contrast failed for {foreground} on {background}: "
                f"{ratio:.2f}:1"
            )


def validate_locale_formatting(errors: list[str]) -> None:
    formatter = JAVA / "com/aqua/aqualight/i18n/LocaleFormatter.kt"
    if not formatter.exists():
        errors.append(f"{relative(formatter)} is missing")
        return

    dimension_formatter = (
        JAVA
        / "com/aqua/aqualight/ui/tabs/aquarium/common/AquariumDimensionFormatter.kt"
    )
    text = dimension_formatter.read_text(encoding="utf-8")
    if "Locale.US" in text or "DecimalFormatSymbols" in text:
        errors.append(
            f"{relative(dimension_formatter)} must not force US display formatting"
        )
    if "LocaleFormatter" not in text:
        errors.append(
            f"{relative(dimension_formatter)} must use LocaleFormatter"
        )


def validate_visual_smoke_contract(errors: list[str]) -> None:
    script = ROOT / "tools/run_release_smoke.sh"
    script_text = script.read_text(encoding="utf-8")
    activity = (
        ROOT
        / "app/src/releaseSmoke/java/com/aqua/aqualight/smoke/ReleaseSmokeActivity.kt"
    )
    activity_text = activity.read_text(encoding="utf-8")
    required_script_tokens = (
        "large-font-light",
        "large-font-dark",
        "rtl-light",
        "rtl-dark",
        "font_scale",
    )
    for token in required_script_tokens:
        if token not in script_text:
            errors.append(
                f"{relative(script)} is missing visual profile token {token}"
            )
    if "debug.force_rtl" in script_text:
        errors.append(
            f"{relative(script)} must not restart the API 27 framework with debug.force_rtl"
        )

    required_activity_tokens = (
        "smokeProfile.startsWith(RTL_PROFILE_PREFIX)",
        "View.LAYOUT_DIRECTION_RTL",
        "verifyRequestedLayoutDirection",
    )
    for token in required_activity_tokens:
        if token not in activity_text:
            errors.append(
                f"{relative(activity)} is missing in-process RTL verification token {token}"
            )


def main() -> int:
    errors: list[str] = []
    try:
        configured = locale_config_tags(errors)
        registered = registry_tags(errors)
        if registered != EXPECTED_SUPPORTED_LOCALES:
            errors.append(
                f"supported locales must be exactly "
                f"{sorted(EXPECTED_SUPPORTED_LOCALES)}, got {sorted(registered)}"
            )
        if configured != registered:
            errors.append(
                f"locale registry {sorted(registered)} does not match "
                f"locale config {sorted(configured)}"
            )

        validate_manifest(errors)
        validate_localized_resources(errors, registered)
        validate_removed_languages(errors)
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

#!/usr/bin/env python3
"""Stage 11 localization/accessibility contract guard."""

from __future__ import annotations

import collections
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
RES = APP / "res"
REGISTRY = APP / "java" / "com" / "aqua" / "aqualight" / "localization" / "SupportedLocaleRegistry.kt"
LANGUAGE_FRAGMENT = APP / "java" / "com" / "aqua" / "aqualight" / "ui" / "tabs" / "settings" / "app" / "LanguageSettingsFragment.kt"
LANGUAGE_LAYOUT = RES / "layout" / "fragment_language_settings.xml"
LOCALE_CONFIG = RES / "xml" / "locales_config.xml"
MANIFEST = APP / "AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

REMOVED_TAGS = {"ru", "zh"}
PLACEHOLDER = re.compile(
    r"%(?:(?P<index>\d+)\$)?(?P<flags>[-#+ 0,(<]*)"
    r"(?P<width>\d*)(?:\.(?P<precision>\d+))?(?P<type>[a-zA-Z%])"
)
LOCALE_ENTRY = re.compile(
    r"^\s{8}SupportedLocale\(\n(?P<body>.*?)^\s{8}\)",
    flags=re.DOTALL | re.MULTILINE,
)


@dataclass(frozen=True)
class LocaleEntry:
    language_tag: str
    availability: str

    @property
    def published(self) -> bool:
        return self.availability == "PUBLISHED"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_registry(errors: list[str]) -> tuple[str, list[LocaleEntry]]:
    text = read(REGISTRY)
    default_match = re.search(r'const val DEFAULT_LANGUAGE_TAG\s*=\s*"([^"]+)"', text)
    if default_match is None:
        errors.append("SupportedLocaleRegistry must declare DEFAULT_LANGUAGE_TAG.")
        default_tag = ""
    else:
        default_tag = default_match.group(1)

    entries: list[LocaleEntry] = []
    for match in LOCALE_ENTRY.finditer(text):
        block = match.group("body")
        tag_match = re.search(r"languageTag\s*=\s*(?:\"([^\"]+)\"|DEFAULT_LANGUAGE_TAG)", block)
        availability_match = re.search(r"LocaleAvailability\.(PUBLISHED|PLANNED)", block)
        if tag_match is None or availability_match is None:
            errors.append("Every SupportedLocale entry must declare a tag and availability.")
            continue
        tag = tag_match.group(1) or default_tag
        entries.append(LocaleEntry(tag, availability_match.group(1)))

    if not entries:
        errors.append("SupportedLocaleRegistry must declare at least one locale entry.")

    tags = [entry.language_tag for entry in entries]
    if len(tags) != len(set(tags)):
        errors.append("SupportedLocaleRegistry contains duplicate language tags.")
    if any(tag in REMOVED_TAGS for tag in tags):
        errors.append("Removed Russian/Chinese tags must not exist in SupportedLocaleRegistry.")
    if default_tag not in {entry.language_tag for entry in entries if entry.published}:
        errors.append("The default locale must be PUBLISHED.")
    if [entry.language_tag for entry in entries if entry.published] != ["en"]:
        errors.append("Until reviewed translations exist, English must be the only PUBLISHED locale.")
    if [entry.language_tag for entry in entries if not entry.published] != ["tr", "de", "fr"]:
        errors.append("Planned locale order must remain tr, de, fr.")

    return default_tag, entries


def parse_locale_config(errors: list[str]) -> list[str]:
    root = ET.parse(LOCALE_CONFIG).getroot()
    tags = [element.attrib.get(f"{ANDROID_NS}name", "") for element in root.findall("locale")]
    if not tags or any(not tag for tag in tags):
        errors.append("locales_config.xml must contain non-empty locale names.")
    if any(tag in REMOVED_TAGS for tag in tags):
        errors.append("Removed Russian/Chinese tags must not exist in LocaleConfig.")
    return tags


def validate_registry_and_manifest(errors: list[str]) -> list[LocaleEntry]:
    default_tag, entries = parse_registry(errors)
    published_tags = [entry.language_tag for entry in entries if entry.published]
    config_tags = parse_locale_config(errors)

    if config_tags != published_tags:
        errors.append(
            "LocaleConfig must exactly match PUBLISHED registry tags: "
            f"config={config_tags}, registry={published_tags}."
        )

    manifest = read(MANIFEST)
    if 'android:localeConfig="@xml/locales_config"' not in manifest:
        errors.append("AndroidManifest.xml must reference @xml/locales_config.")
    if 'android:supportsRtl="true"' not in manifest:
        errors.append("AndroidManifest.xml must keep RTL support enabled.")
    if default_tag != "en":
        errors.append("The base values resources are English; default locale must remain en.")

    return entries


def android_values_directory(language_tag: str) -> Path:
    parts = language_tag.split("-")
    if len(parts) == 1:
        qualifier = parts[0]
    elif len(parts) == 2 and len(parts[1]) == 2:
        qualifier = f"{parts[0]}-r{parts[1].upper()}"
    else:
        qualifier = "b+" + "+".join(parts)
    return RES / f"values-{qualifier}"


def resource_key(element: ET.Element) -> tuple[str, str] | None:
    name = element.attrib.get("name")
    if not name:
        return None
    tag = element.tag.rsplit("}", 1)[-1]
    if tag not in {"string", "plurals", "string-array"}:
        return None
    return tag, name


def translatable_resources(directory: Path) -> dict[tuple[str, str], ET.Element]:
    result: dict[tuple[str, str], ET.Element] = {}
    if not directory.exists():
        return result
    for path in sorted(directory.glob("*.xml")):
        root = ET.parse(path).getroot()
        for element in root:
            key = resource_key(element)
            if key is None or element.attrib.get("translatable") == "false":
                continue
            if key in result:
                raise ValueError(f"Duplicate translatable resource {key} in {directory.name}.")
            result[key] = element
    return result


def element_texts(element: ET.Element) -> list[str]:
    tag = element.tag.rsplit("}", 1)[-1]
    if tag == "string":
        return ["".join(element.itertext())]
    return ["".join(child.itertext()) for child in element]


def placeholder_signature(text: str) -> collections.Counter[tuple[str, str]]:
    signature: collections.Counter[tuple[str, str]] = collections.Counter()
    for match in PLACEHOLDER.finditer(text):
        conversion_type = match.group("type")
        if conversion_type == "%":
            continue
        signature[(match.group("index") or "implicit", conversion_type)] += 1
    return signature


def validate_translation_packs(errors: list[str], entries: list[LocaleEntry]) -> None:
    try:
        base_resources = translatable_resources(RES / "values")
    except (ET.ParseError, ValueError) as error:
        errors.append(str(error))
        return

    for entry in entries:
        if entry.language_tag == "en":
            continue
        directory = android_values_directory(entry.language_tag)
        if not entry.published:
            if directory.exists():
                errors.append(
                    f"{directory.name} exists while {entry.language_tag} is PLANNED. "
                    "Do not ship empty or fallback translation packs."
                )
            continue

        if not directory.exists():
            errors.append(f"Published locale {entry.language_tag} requires {directory.name}.")
            continue

        try:
            translated = translatable_resources(directory)
        except (ET.ParseError, ValueError) as error:
            errors.append(str(error))
            continue

        missing = sorted(set(base_resources) - set(translated))
        extra = sorted(set(translated) - set(base_resources))
        if missing:
            errors.append(f"{directory.name} is missing {len(missing)} translatable resources: {missing[:10]}")
        if extra:
            errors.append(f"{directory.name} contains unknown resources: {extra[:10]}")

        for key in sorted(set(base_resources) & set(translated)):
            base_texts = element_texts(base_resources[key])
            translated_texts = element_texts(translated[key])
            if len(base_texts) != len(translated_texts):
                errors.append(f"{directory.name} quantity/item count differs for {key}.")
                continue
            for index, (base_text, translated_text) in enumerate(zip(base_texts, translated_texts)):
                if placeholder_signature(base_text) != placeholder_signature(translated_text):
                    errors.append(
                        f"{directory.name} placeholder mismatch for {key} item {index}: "
                        f"base={placeholder_signature(base_text)}, "
                        f"translation={placeholder_signature(translated_text)}"
                    )

    for removed_tag in REMOVED_TAGS:
        directory = android_values_directory(removed_tag)
        if directory.exists():
            errors.append(f"Removed locale directory must not exist: {directory.name}.")


def validate_language_screen(errors: list[str]) -> None:
    fragment = read(LANGUAGE_FRAGMENT)
    if "SupportedLocaleRegistry" not in fragment:
        errors.append("LanguageSettingsFragment must be driven by SupportedLocaleRegistry.")
    for removed_tag in REMOVED_TAGS:
        if re.search(rf'LanguageRow\("{removed_tag}"', fragment):
            errors.append(f"LanguageSettingsFragment must not expose removed locale {removed_tag}.")
    if "suppressDescendantAccessibility" not in fragment:
        errors.append("Language rows must expose one TalkBack node rather than duplicate child nodes.")
    if "ViewCompat.setStateDescription" not in fragment:
        errors.append("Language rows must publish selected/not-selected state descriptions.")

    layout = read(LANGUAGE_LAYOUT)
    visual_contract = {
        'style="@style/Widget.Aqua.Card"': 6,
        'android:padding="@dimen/aqua_size_12"': 6,
        'android:layout_width="@dimen/aqua_size_32"': 6,
        'android:layout_height="@dimen/aqua_size_32"': 6,
        'style="@style/Widget.Aqua.SettingsRadio"': 6,
    }
    for token, minimum_count in visual_contract.items():
        actual = layout.count(token)
        if actual < minimum_count:
            errors.append(
                f"Language screen visual contract changed for {token}: "
                f"expected at least {minimum_count}, found {actual}."
            )


def main() -> int:
    errors: list[str] = []
    entries = validate_registry_and_manifest(errors)
    validate_translation_packs(errors, entries)
    validate_language_screen(errors)

    if errors:
        print("Stage 11 localization/accessibility guard failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Stage 11 localization/accessibility guard passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

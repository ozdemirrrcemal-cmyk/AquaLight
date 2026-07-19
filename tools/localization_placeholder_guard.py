#!/usr/bin/env python3
"""Validate production locale declarations and Android string placeholders."""
from __future__ import annotations

import collections
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
REGISTRY = ROOT / "app/src/main/java/com/aqua/aqualight/localization/SupportedLocaleRegistry.kt"
LOCALES_CONFIG = RES / "xml/locales_config.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
PLACEHOLDER = re.compile(
    r"(?<!%)%(?!%)(?:(?P<index>\d+)\$)?[-+#, 0(<]*\d*(?:\.\d+)?(?P<type>[a-zA-Z])"
)


class GuardFailure(RuntimeError):
    pass


def fail(message: str) -> None:
    raise GuardFailure(message)


def placeholders(value: str) -> collections.Counter[tuple[str, str]]:
    result: collections.Counter[tuple[str, str]] = collections.Counter()
    implicit_index = 0
    for match in PLACEHOLDER.finditer(value):
        placeholder_type = match.group("type").lower()
        if placeholder_type == "n":
            continue
        index = match.group("index")
        if index is None:
            implicit_index += 1
            index = f"implicit:{implicit_index}"
        result[(index, placeholder_type)] += 1
    return result


def read_catalog(directory: Path) -> dict[str, collections.Counter[tuple[str, str]]]:
    catalog: dict[str, collections.Counter[tuple[str, str]]] = {}
    for xml_path in sorted(directory.glob("*.xml")):
        root = ET.parse(xml_path).getroot()
        if root.tag != "resources":
            continue
        for child in root:
            name = child.attrib.get("name")
            if not name or child.attrib.get("translatable", "true").lower() == "false":
                continue
            if child.tag == "string":
                catalog[f"string:{name}"] = placeholders("".join(child.itertext()))
            elif child.tag == "plurals":
                for item in child.findall("item"):
                    quantity = item.attrib.get("quantity", "")
                    catalog[f"plurals:{name}:{quantity}"] = placeholders(
                        "".join(item.itertext())
                    )
    return catalog


def registry_tags() -> set[str]:
    source = REGISTRY.read_text(encoding="utf-8")
    default_match = re.search(
        r'const\s+val\s+DEFAULT_LANGUAGE_TAG\s*=\s*"([^"]+)"', source
    )
    if default_match is None:
        fail("SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG is missing")
    return {
        default_match.group(1),
        *re.findall(r'languageTag\s*=\s*"([^"]+)"', source),
    }


def configured_tags() -> set[str]:
    root = ET.parse(LOCALES_CONFIG).getroot()
    return {
        locale.attrib[f"{ANDROID_NS}name"]
        for locale in root.findall("locale")
    }


def locale_directories() -> dict[str, Path]:
    result: dict[str, Path] = {}
    for directory in RES.glob("values-*"):
        match = re.fullmatch(r"values-([a-z]{2,3})(?:-r[A-Z]{2})?", directory.name)
        if directory.is_dir() and match:
            result[match.group(1)] = directory
    return result


def main() -> int:
    try:
        registry = registry_tags()
        configured = configured_tags()
        if registry != configured:
            fail(
                "SupportedLocaleRegistry/locales_config mismatch: "
                f"{sorted(registry)} != {sorted(configured)}"
            )

        base_catalog = read_catalog(RES / "values")
        if not base_catalog:
            fail("Default string catalog is empty")

        for language, directory in locale_directories().items():
            localized_catalog = read_catalog(directory)
            unknown_keys = localized_catalog.keys() - base_catalog.keys()
            if unknown_keys:
                fail(
                    f"{directory.name} contains unknown resources: "
                    f"{sorted(unknown_keys)[:10]}"
                )

            for key, localized_tokens in localized_catalog.items():
                base_tokens = base_catalog[key]
                if localized_tokens != base_tokens:
                    fail(
                        f"Placeholder mismatch for {directory.name}/{key}: "
                        f"base={base_tokens}, locale={localized_tokens}"
                    )

            if language in registry and language != "en":
                missing_keys = base_catalog.keys() - localized_catalog.keys()
                if missing_keys:
                    fail(
                        f"Enabled locale {language} is incomplete: "
                        f"{len(missing_keys)} resources missing"
                    )

    except (GuardFailure, ET.ParseError) as error:
        print(f"LOCALIZATION_PLACEHOLDER_GUARD_FAILED: {error}", file=sys.stderr)
        return 1

    print(
        "LOCALIZATION_PLACEHOLDER_GUARD_PASS "
        f"enabled_locales={','.join(sorted(registry))}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

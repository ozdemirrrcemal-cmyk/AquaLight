#!/usr/bin/env python3
"""Move XML UI literals to Android resources without changing rendered values.

This migration is deterministic and idempotent. It intentionally limits itself
 to XML/resource boundaries; Kotlin presentation models are migrated separately
 so @StringRes contracts remain explicit and type-safe.
"""

from __future__ import annotations

from collections import defaultdict
from html import unescape
from pathlib import Path
import re
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
VALUES = RES / "values"
STRINGS = VALUES / "strings.xml"
DIMENS = VALUES / "dimens.xml"
COLORS = VALUES / "colors.xml"

TEXT_ATTRS = (
    "text",
    "hint",
    "title",
    "summary",
    "contentDescription",
    "label",
)
LEGACY_STYLES = {
    "RedButton": "Widget.Aqua.Button.Auth.Primary",
    "BlackButton": "Widget.Aqua.Button.Auth.Secondary",
    "WhiteButton": "Widget.Aqua.Button.Auth.Google",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_if_changed(path: Path, content: str) -> bool:
    old = read(path)
    if old == content:
        return False
    path.write_text(content, encoding="utf-8")
    return True


def sanitize(value: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9]+", "_", value).strip("_").lower()
    value = re.sub(r"_+", "_", value)
    if not value:
        value = "value"
    if value[0].isdigit():
        value = f"value_{value}"
    return value


def unique_name(base: str, used: set[str]) -> str:
    candidate = sanitize(base)
    suffix = 2
    while candidate in used:
        candidate = f"{sanitize(base)}_{suffix}"
        suffix += 1
    used.add(candidate)
    return candidate


def decode_android_xml_literal(value: str) -> str:
    return unescape(value).replace("\\n", "\n")


def encode_android_string(value: str) -> tuple[str, str]:
    value = value.replace("\\", "\\\\")
    value = value.replace("\n", "\\n")
    value = value.replace("'", "\\'")
    value = value.replace("%", "%%")
    return escape(value), ' formatted="false"' if "%%" in value else ""


def append_resources(path: Path, block: str) -> None:
    text = read(path)
    marker = "</resources>"
    if marker not in text:
        raise RuntimeError(f"Invalid Android values file: {path}")
    updated = text.replace(marker, f"\n{block.rstrip()}\n\n{marker}", 1)
    write_if_changed(path, updated)


def load_strings() -> tuple[dict[str, str], dict[str, str], set[str]]:
    text = read(STRINGS)
    by_value: dict[str, str] = {}
    by_name: dict[str, str] = {}
    used: set[str] = set()
    for match in re.finditer(
        r'<string\s+name="([^"]+)"(?:\s+[^>]*)?>(.*?)</string>',
        text,
        flags=re.DOTALL,
    ):
        name, raw_value = match.groups()
        value = decode_android_xml_literal(re.sub(r"<[^>]+>", "", raw_value))
        value = value.replace("%%", "%").replace("\\'", "'")
        used.add(name)
        by_name[name] = value
        by_value.setdefault(value, name)
    return by_value, by_name, used


def text_scope(path: Path, resource_id: str | None, attr: str) -> str:
    folder = path.parent.name
    stem = sanitize(path.stem)
    stem = re.sub(r"^(fragment|item|layout|dialog|bottom_sheet)_", "", stem)
    rid = sanitize(resource_id or attr)
    rid = re.sub(r"^(fragment|action|menu|nav)_", "", rid)
    suffix = {
        "contentDescription": "description",
        "hint": "hint",
        "label": "label",
        "summary": "summary",
        "title": "title",
        "text": "text",
    }[attr]
    if folder == "menu":
        return f"nav_{rid}_title"
    if folder == "navigation":
        return f"nav_{rid}_label"
    return f"{stem}_{rid}_{suffix}"


def migrate_xml_text() -> int:
    by_value, _, used = load_strings()
    additions: list[tuple[str, str, str]] = []
    changed_files = 0
    attr_pattern = re.compile(
        r'(?<!tools:)(?P<prefix>\b(?:android:|app:)?(?P<attr>'
        + "|".join(TEXT_ATTRS)
        + r')\s*=\s*")(?P<value>[^"]*)(?P<suffix>")'
    )
    tag_pattern = re.compile(r"<(?![!?/])[^>]+>", flags=re.DOTALL)
    id_pattern = re.compile(r'(?:android:)?id\s*=\s*"@\+?id/([^"]+)"')

    for path in sorted(RES.rglob("*.xml")):
        rel_parts = path.relative_to(RES).parts
        if any(part.startswith("values") for part in rel_parts):
            continue
        original = read(path)

        def replace_tag(tag_match: re.Match[str]) -> str:
            tag = tag_match.group(0)
            id_match = id_pattern.search(tag)
            resource_id = id_match.group(1) if id_match else None

            def replace_attr(match: re.Match[str]) -> str:
                raw = match.group("value").strip()
                if not raw or raw.startswith(("@", "?")):
                    return match.group(0)
                value = decode_android_xml_literal(raw)
                resource_name = by_value.get(value)
                if resource_name is None:
                    resource_name = unique_name(
                        text_scope(path, resource_id, match.group("attr")),
                        used,
                    )
                    encoded, formatted_attr = encode_android_string(value)
                    additions.append((resource_name, encoded, formatted_attr))
                    by_value[value] = resource_name
                return f'{match.group("prefix")}@string/{resource_name}{match.group("suffix")}'

            return attr_pattern.sub(replace_attr, tag)

        updated = tag_pattern.sub(replace_tag, original)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed_files += 1

    if additions:
        lines = ["    <!-- Stage 10: extracted user-visible XML text -->"]
        for name, value, formatted_attr in additions:
            lines.append(f'    <string name="{name}"{formatted_attr}>{value}</string>')
        append_resources(STRINGS, "\n".join(lines))
    return changed_files


def load_dimen_resources() -> tuple[dict[str, list[str]], set[str]]:
    by_value: dict[str, list[str]] = defaultdict(list)
    used: set[str] = set()
    for path in sorted(RES.glob("values*/*.xml")):
        for match in re.finditer(
            r'<dimen\s+name="([^"]+)">\s*([^<]+?)\s*</dimen>',
            read(path),
        ):
            name, value = match.groups()
            used.add(name)
            by_value[value].append(name)
    return by_value, used


def number_token(value: str) -> str:
    numeric = re.sub(r"(dp|sp)$", "", value)
    numeric = numeric.replace("-", "neg_").replace(".", "_")
    return sanitize(numeric)


def dimen_role(attr: str, value: str) -> str:
    attr_lower = attr.lower()
    if value.endswith("sp") or "textsize" in attr_lower:
        return "text_size"
    if "corner" in attr_lower or "radius" in attr_lower:
        return "radius"
    if "elevation" in attr_lower:
        return "elevation"
    if "stroke" in attr_lower:
        return "stroke"
    if any(token in attr_lower for token in ("padding", "margin", "spacing", "inset", "offset")):
        return "space"
    return "size"


def existing_dimen_for(role: str, value: str, by_value: dict[str, list[str]]) -> str | None:
    candidates = by_value.get(value, [])
    preferred_prefixes = {
        "text_size": ("aqua_text_size_", "text_size_"),
        "radius": ("radius_", "aqua_radius_"),
        "elevation": ("elevation_", "aqua_elevation_"),
        "stroke": ("stroke_", "aqua_stroke_"),
        "space": ("space_", "aqua_space_"),
        "size": ("size_", "aqua_size_", "min_touch_size", "form_factor_"),
    }[role]
    for prefix in preferred_prefixes:
        for name in candidates:
            if name.startswith(prefix):
                return name
    return None


def migrate_xml_dimensions() -> int:
    by_value, used = load_dimen_resources()
    additions: dict[str, str] = {}
    changed_files = 0

    attr_pattern = re.compile(
        r'(?<!tools:)(?P<prefix>\b(?:android:|app:)?(?P<attr>[A-Za-z0-9_]+)\s*=\s*")'
        r'(?P<value>-?\d+(?:\.\d+)?(?:dp|sp))(?P<suffix>")'
    )
    item_pattern = re.compile(
        r'(?P<prefix><item\s+name="(?P<attr>[^"]+)">\s*)'
        r'(?P<value>-?\d+(?:\.\d+)?(?:dp|sp))(?P<suffix>\s*</item>)'
    )

    def replacement(match: re.Match[str]) -> str:
        value = match.group("value")
        if value in {"0dp", "0sp"}:
            return match.group(0)
        role = dimen_role(match.group("attr"), value)
        name = existing_dimen_for(role, value, by_value)
        if name is None:
            base = f"aqua_{role}_{number_token(value)}"
            name = base if base not in used else unique_name(base, used)
            used.add(name)
            additions[name] = value
            by_value[value].append(name)
        return f'{match.group("prefix")}@dimen/{name}{match.group("suffix")}'

    for path in sorted(RES.rglob("*.xml")):
        rel = path.relative_to(RES)
        if path.name == "dimens.xml" and any(part.startswith("values") for part in rel.parts):
            continue
        original = read(path)
        updated = attr_pattern.sub(replacement, original)
        updated = item_pattern.sub(replacement, updated)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed_files += 1

    if additions:
        lines = ["    <!-- Stage 10: semantic layout and component tokens -->"]
        for name, value in sorted(additions.items()):
            lines.append(f'    <dimen name="{name}">{value}</dimen>')
        append_resources(DIMENS, "\n".join(lines))
    return changed_files


def load_color_names() -> set[str]:
    used: set[str] = set()
    for path in sorted(RES.glob("values*/colors.xml")):
        used.update(re.findall(r'<color\s+name="([^"]+)"', read(path)))
    return used


def color_name(hex_value: str) -> str:
    return f"aqua_palette_{hex_value.removeprefix('#').lower()}"


def migrate_xml_colors() -> int:
    used = load_color_names()
    additions: dict[str, str] = {}
    changed_files = 0
    hex_pattern = re.compile(r"(?<![A-Za-z0-9_])#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?(?![A-Za-z0-9_])")

    for path in sorted(RES.rglob("*.xml")):
        if path.name == "colors.xml" and any(
            part.startswith("values") for part in path.relative_to(RES).parts
        ):
            continue
        original = read(path)

        def replacement(match: re.Match[str]) -> str:
            value = match.group(0).upper()
            name = color_name(value)
            if name not in used:
                used.add(name)
                additions[name] = value
            return f"@color/{name}"

        updated = hex_pattern.sub(replacement, original)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed_files += 1

    if additions:
        lines = ["    <!-- Stage 10: centralized fixed palette values -->"]
        for name, value in sorted(additions.items()):
            lines.append(f'    <color name="{name}">{value}</color>')
        append_resources(COLORS, "\n".join(lines))

    # Collapse duplicate direct values behind one exact palette token while
    # keeping all existing semantic resource names and therefore every caller.
    color_files = sorted(RES.glob("values*/colors.xml"))
    groups: dict[str, list[tuple[Path, str]]] = defaultdict(list)
    entry_pattern = re.compile(
        r'(<color\s+name="(?P<name>[^"]+)">\s*)(?P<value>#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?)(\s*</color>)'
    )
    for path in color_files:
        for match in entry_pattern.finditer(read(path)):
            groups[match.group("value").upper()].append((path, match.group("name")))

    duplicate_tokens: dict[str, str] = {}
    for value, entries in groups.items():
        if len(entries) > 1:
            name = color_name(value)
            duplicate_tokens[value] = name
            if name not in used:
                used.add(name)
                additions[name] = value

    if duplicate_tokens:
        # Ensure tokens added after duplicate discovery are present once.
        current = read(COLORS)
        missing_lines = []
        for value, name in sorted(duplicate_tokens.items(), key=lambda item: item[1]):
            if f'name="{name}"' not in current:
                missing_lines.append(f'    <color name="{name}">{value}</color>')
        if missing_lines:
            append_resources(
                COLORS,
                "    <!-- Stage 10: canonical values for duplicate palette entries -->\n"
                + "\n".join(missing_lines),
            )

        for path in color_files:
            original = read(path)

            def alias_duplicate(match: re.Match[str]) -> str:
                value = match.group("value").upper()
                token = duplicate_tokens.get(value)
                if token is None or match.group("name") == token:
                    return match.group(0)
                return f'{match.group(1)}@color/{token}{match.group(4)}'

            updated = entry_pattern.sub(alias_duplicate, original)
            if updated != original:
                path.write_text(updated, encoding="utf-8")
                changed_files += 1

    return changed_files


def remove_legacy_style_aliases() -> int:
    changed_files = 0
    for path in sorted(ROOT.joinpath("app/src/main").rglob("*")):
        if path.suffix not in {".xml", ".kt", ".java"}:
            continue
        original = read(path)
        updated = original
        for legacy, semantic in LEGACY_STYLES.items():
            updated = updated.replace(f"@style/{legacy}", f"@style/{semantic}")
            updated = updated.replace(
                f"R.style.{legacy}",
                f"R.style.{semantic.replace('.', '_')}",
            )
            updated = re.sub(
                rf'^\s*<style\s+name="{re.escape(legacy)}"[^>]*/>\s*\n?',
                "",
                updated,
                flags=re.MULTILINE,
            )
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed_files += 1
    return changed_files


def main() -> None:
    results = {
        "xml_text_files": migrate_xml_text(),
        "xml_dimension_files": migrate_xml_dimensions(),
        "xml_color_files": migrate_xml_colors(),
        "legacy_style_files": remove_legacy_style_aliases(),
    }
    print("Stage 10 XML migration complete:")
    for key, value in results.items():
        print(f" - {key}: {value}")


if __name__ == "__main__":
    main()

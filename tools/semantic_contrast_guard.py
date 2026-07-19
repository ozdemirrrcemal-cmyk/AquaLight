#!/usr/bin/env python3
"""Usage-based WCAG contrast contract for AquaLight semantic roles."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
TEXT_MIN = 4.5
LARGE_OR_NON_TEXT_MIN = 3.0

EXEMPT = {
    "aqua_content_disabled",
    "aqua_button_disabled_content",
    "aqua_toolbar_icon_disabled",
}

IMAGE_BACKED_MANUAL = {
    "aqua_tv_aqua_light_content",
    "aqua_tv_nature_aquarium_content",
}


@dataclass(frozen=True)
class Pair:
    foreground: str
    background: str
    minimum: float
    label: str


class Failure(RuntimeError):
    pass


def fail(message: str) -> None:
    raise Failure(message)


def catalog(directory: Path, base: dict[str, str] | None = None) -> dict[str, str]:
    result = dict(base or {})
    if not directory.is_dir():
        return result
    for path in sorted(directory.glob("*.xml")):
        root = ET.parse(path).getroot()
        if root.tag != "resources":
            continue
        for color in root.findall("color"):
            name = color.attrib.get("name")
            value = "".join(color.itertext()).strip()
            if name and value:
                result[name] = value
    return result


def rgba(name: str, colors: dict[str, str], stack: tuple[str, ...] = ()) -> tuple[float, float, float, float]:
    if name in stack:
        fail("Circular color alias: " + " -> ".join((*stack, name)))
    raw = colors.get(name)
    if raw is None:
        fail(f"Missing color {name}")
    if raw.startswith("@color/"):
        return rgba(raw[7:], colors, (*stack, name))
    if raw.startswith("@android:color/"):
        raw = {
            "white": "#FFFFFF",
            "black": "#000000",
            "transparent": "#00000000",
        }.get(raw.removeprefix("@android:color/"), "")
    if not re.fullmatch(r"#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}", raw):
        fail(f"Unsupported color {name}={raw}")
    if len(raw) == 7:
        a = 255
        r, g, b = (int(raw[index:index + 2], 16) for index in (1, 3, 5))
    else:
        a, r, g, b = (int(raw[index:index + 2], 16) for index in (1, 3, 5, 7))
    return r / 255.0, g / 255.0, b / 255.0, a / 255.0


def composite(fg: tuple[float, float, float, float], bg: tuple[float, float, float, float]) -> tuple[float, float, float, float]:
    fr, fg_channel, fb, fa = fg
    br, bg_channel, bb, ba = bg
    out_a = fa + ba * (1.0 - fa)
    if out_a <= 0:
        return 0.0, 0.0, 0.0, 0.0
    return (
        (fr * fa + br * ba * (1.0 - fa)) / out_a,
        (fg_channel * fa + bg_channel * ba * (1.0 - fa)) / out_a,
        (fb * fa + bb * ba * (1.0 - fa)) / out_a,
        out_a,
    )


def opaque(color: tuple[float, float, float, float], colors: dict[str, str]) -> tuple[float, float, float, float]:
    if color[3] >= 0.999:
        return color
    underlay = rgba("background_color", colors)
    return composite(color, underlay)


def luminance(color: tuple[float, float, float, float]) -> float:
    def linear(value: float) -> float:
        return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4

    red, green, blue, _ = color
    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)


def ratio(foreground: str, background: str, colors: dict[str, str]) -> float:
    bg = opaque(rgba(background, colors), colors)
    fg = composite(rgba(foreground, colors), bg)
    high = max(luminance(fg), luminance(bg))
    low = min(luminance(fg), luminance(bg))
    return (high + 0.05) / (low + 0.05)


def add(pairs: list[Pair], names: set[str], foreground: str, background: str, minimum: float, label: str | None = None) -> None:
    if foreground in names and background in names:
        pairs.append(Pair(foreground, background, minimum, label or f"{foreground}-on-{background}"))


def build_pairs(names: set[str]) -> list[Pair]:
    pairs: list[Pair] = []

    # Actual Material-role usages in AquaLight.
    for scheme in ("light", "dark"):
        add(pairs, names, f"md_theme_{scheme}_onPrimary", f"md_theme_{scheme}_surface", LARGE_OR_NON_TEXT_MIN, f"{scheme}-splash-title")
        add(pairs, names, f"md_theme_{scheme}_onSurface", f"md_theme_{scheme}_surface", TEXT_MIN)
        if "bottom_nav_background" in names:
            add(pairs, names, f"md_theme_{scheme}_onSurfaceVariant", "bottom_nav_background", LARGE_OR_NON_TEXT_MIN, f"{scheme}-bottom-nav-inactive")
        for suffix in ("PrimaryContainer", "Secondary", "SecondaryContainer", "Error"):
            foreground = f"md_theme_{scheme}_on{suffix}"
            background = f"md_theme_{scheme}_{suffix[0].lower()}{suffix[1:]}"
            add(pairs, names, foreground, background, TEXT_MIN)

    # Global text roles on the two dominant dark surfaces.
    for foreground in (
        "aqua_content_primary",
        "aqua_content_primary_soft",
        "aqua_content_secondary",
        "aqua_content_tertiary",
        "aqua_content_muted",
        "aqua_content_placeholder",
        "aqua_content_on_dark",
        "aqua_content_on_dark_argb",
    ):
        add(pairs, names, foreground, "background_color", TEXT_MIN)
        add(pairs, names, foreground, "aqua_card_surface", TEXT_MIN)
    add(pairs, names, "aqua_content_warning", "aqua_bg_maintenance_profile_percent_warning_fill", TEXT_MIN)

    # Card and empty-state text roles.
    card_surfaces = tuple(
        name for name in (
            "aqua_card_surface",
            "aqua_card_device_surface",
            "aqua_card_device_media_surface",
            "aqua_card_device_section_surface",
            "aqua_card_device_highlight_surface",
            "aqua_card_metric_surface",
            "aqua_card_form_surface",
            "aqua_card_info_surface",
            "aqua_card_icon_accent_surface",
        ) if name in names
    )
    for foreground in sorted(name for name in names if name.startswith("aqua_card_text_")):
        for background in card_surfaces:
            add(pairs, names, foreground, background, TEXT_MIN)

    state_surfaces = sorted(name for name in names if name.startswith("aqua_state_") and name.endswith("_surface"))
    for foreground in sorted(name for name in names if name.startswith("aqua_state_text_")):
        for background in state_surfaces:
            add(pairs, names, foreground, background, TEXT_MIN)

    # Global buttons. Disabled states are explicitly exempt above.
    for stem in (
        "aqua_button_primary",
        "aqua_button_secondary",
        "aqua_button_neutral",
        "aqua_button_danger",
        "aqua_button_auth_primary",
        "aqua_button_auth_secondary",
        "aqua_button_auth_google",
    ):
        add(pairs, names, f"{stem}_content", f"{stem}_container", TEXT_MIN)
    add(pairs, names, "aqua_accent", "aqua_button_secondary_container", LARGE_OR_NON_TEXT_MIN, "icon-toggle-selected")
    add(pairs, names, "aqua_button_neutral_content", "aqua_button_neutral_container", LARGE_OR_NON_TEXT_MIN, "icon-toggle-default")

    # Screen-specific semantic roles and their verified rendered surfaces.
    explicit = (
        ("aqua_add_care_task_fragment_tv_aquarium_subtitle_content", "aqua_card_surface", TEXT_MIN),
        ("aqua_add_care_task_fragment_tv_task_type_subtitle_content", "aqua_card_surface", TEXT_MIN),
        ("aqua_aquarium_fragment_button_content", "aqua_aquarium_fragment_button_icon", TEXT_MIN),
        ("aqua_aquarium_maintenance_fragment_content", "aqua_aquarium_maintenance_fragment_color", TEXT_MIN),
        ("aqua_tv_care_info_content", "aqua_info_box_background", TEXT_MIN),
        ("aqua_tv_setup_date_content", "aqua_info_box_background", TEXT_MIN),
        ("aqua_tv_tank_day_content", "aqua_info_box_background", TEXT_MIN),
        ("aqua_tv_tank_name_content", "aqua_info_box_background", TEXT_MIN),
        ("aqua_tv_tank_size_content", "aqua_info_box_background", TEXT_MIN),
        ("aqua_tv_danger_zone_title_content", "background_color", TEXT_MIN),
        ("aqua_tv_next_care_task_title_content", "aqua_card_metric_surface", TEXT_MIN),
    )
    for foreground, background, minimum in explicit:
        add(pairs, names, foreground, background, minimum)

    # Icon/status roles use WCAG's non-text threshold.
    for foreground in sorted(name for name in names if name.startswith("aqua_toolbar_icon_") and name not in EXEMPT):
        add(pairs, names, foreground, "background_color", LARGE_OR_NON_TEXT_MIN)
    for foreground in (
        "aqua_status_success",
        "aqua_status_danger",
        "aqua_accent_positive",
        "aqua_accent_primary",
        "aqua_accent_aqua",
    ):
        add(pairs, names, foreground, "background_color", LARGE_OR_NON_TEXT_MIN)
        add(pairs, names, foreground, "aqua_card_surface", LARGE_OR_NON_TEXT_MIN)

    unique: dict[tuple[str, str, float], Pair] = {}
    for pair in pairs:
        unique[(pair.foreground, pair.background, pair.minimum)] = pair
    return list(unique.values())


def required_roles(names: set[str]) -> set[str]:
    required: set[str] = set()
    for name in names:
        if name in EXEMPT or name in IMAGE_BACKED_MANUAL:
            continue
        if re.match(r"^md_theme_(?:light|dark)_on", name):
            required.add(name)
        elif re.match(r"^aqua_(?:content|card_text|state_text|toolbar_icon)_", name):
            required.add(name)
        elif re.match(r"^aqua_(?:status|accent)_(?:success|danger|positive|primary|aqua)$", name):
            required.add(name)
        elif name.endswith("_content") and not name.endswith("_disabled_content"):
            required.add(name)
    return required


def main() -> int:
    try:
        base = catalog(RES / "values")
        night = catalog(RES / "values-night", base)
        names = set(base) | set(night)
        pairs = build_pairs(names)
        required = required_roles(names)
        covered = {pair.foreground for pair in pairs}
        uncovered = sorted(required - covered)
        if uncovered:
            fail("Uncovered semantic foreground roles: " + ", ".join(uncovered))

        violations: list[str] = []
        for theme, colors in (("base", base), ("night", night)):
            for pair in pairs:
                actual = ratio(pair.foreground, pair.background, colors)
                if actual + 1e-9 < pair.minimum:
                    violations.append(
                        f"{theme}/{pair.label}: {actual:.2f}:1 < {pair.minimum:.1f}:1"
                    )
        if violations:
            fail("WCAG contrast violations:\n- " + "\n- ".join(violations))

    except (Failure, ET.ParseError) as error:
        print(f"SEMANTIC_CONTRAST_GUARD_FAILED: {error}", file=sys.stderr)
        return 1

    print(
        "SEMANTIC_CONTRAST_GUARD_PASS "
        f"roles={len(required)} pairs={len(pairs)} themes=2 "
        f"image_backed_manual={','.join(sorted(IMAGE_BACKED_MANUAL))}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

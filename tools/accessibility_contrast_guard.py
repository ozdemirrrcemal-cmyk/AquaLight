#!/usr/bin/env python3
"""WCAG contrast gate for AquaLight semantic text, icon and status roles."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"

NORMAL_TEXT_MINIMUM = 4.5
NON_TEXT_MINIMUM = 3.0

EXEMPT_FOREGROUNDS = {
    # WCAG does not require contrast for unavailable/disabled controls.
    "aqua_content_disabled",
}

COMMON_DARK_SURFACES = (
    "background_color",
    "aqua_card_surface",
    "aqua_surface_deep",
)

CARD_SURFACES = (
    "aqua_card_surface",
    "aqua_card_surface_elevated",
    "aqua_card_surface_grouped",
    "aqua_card_device_surface",
    "aqua_card_device_media_surface",
    "aqua_card_device_section_surface",
    "aqua_card_device_highlight_surface",
    "aqua_card_metric_surface",
    "aqua_card_form_surface",
    "aqua_card_info_surface",
    "aqua_card_icon_accent_surface",
)


class GuardFailure(RuntimeError):
    pass


@dataclass(frozen=True)
class Pair:
    label: str
    foreground: str
    background: str
    minimum: float


def fail(message: str) -> None:
    raise GuardFailure(message)


def color_catalog(directory: Path, base: dict[str, str] | None = None) -> dict[str, str]:
    catalog = dict(base or {})
    if not directory.is_dir():
        return catalog
    for xml_path in sorted(directory.glob("*.xml")):
        root = ET.parse(xml_path).getroot()
        if root.tag != "resources":
            continue
        for color in root.findall("color"):
            name = color.attrib.get("name")
            value = "".join(color.itertext()).strip()
            if name and value:
                catalog[name] = value
    return catalog


def resolve_rgba(
    name: str,
    catalog: dict[str, str],
    stack: tuple[str, ...] = (),
) -> tuple[float, float, float, float]:
    if name in stack:
        fail("Circular color alias: " + " -> ".join((*stack, name)))
    raw = catalog.get(name)
    if raw is None:
        fail(f"Missing semantic color: {name}")
    if raw.startswith("@color/"):
        return resolve_rgba(raw.removeprefix("@color/"), catalog, (*stack, name))
    if raw.startswith("@android:color/"):
        android_name = raw.removeprefix("@android:color/")
        android_colors = {
            "white": "#FFFFFF",
            "black": "#000000",
            "transparent": "#00000000",
        }
        raw = android_colors.get(android_name, "")
    if not re.fullmatch(r"#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}", raw):
        fail(f"Unsupported color value {name}={raw}")

    if len(raw) == 7:
        alpha = 255
        red, green, blue = (int(raw[index:index + 2], 16) for index in (1, 3, 5))
    else:
        alpha, red, green, blue = (
            int(raw[index:index + 2], 16) for index in (1, 3, 5, 7)
        )
    return red / 255.0, green / 255.0, blue / 255.0, alpha / 255.0


def composite(
    foreground: tuple[float, float, float, float],
    background: tuple[float, float, float, float],
) -> tuple[float, float, float, float]:
    fr, fg, fb, fa = foreground
    br, bg, bb, ba = background
    out_alpha = fa + ba * (1.0 - fa)
    if out_alpha <= 0.0:
        return 0.0, 0.0, 0.0, 0.0
    return (
        (fr * fa + br * ba * (1.0 - fa)) / out_alpha,
        (fg * fa + bg * ba * (1.0 - fa)) / out_alpha,
        (fb * fa + bb * ba * (1.0 - fa)) / out_alpha,
        out_alpha,
    )


def opaque_on_app_background(
    color: tuple[float, float, float, float],
    catalog: dict[str, str],
) -> tuple[float, float, float, float]:
    if color[3] >= 0.999:
        return color
    underlay_name = "background_color" if "background_color" in catalog else "aqua_system_bar_surface"
    underlay = resolve_rgba(underlay_name, catalog)
    return composite(color, underlay)


def channel_luminance(value: float) -> float:
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def luminance(color: tuple[float, float, float, float]) -> float:
    red, green, blue, _ = color
    return (
        0.2126 * channel_luminance(red)
        + 0.7152 * channel_luminance(green)
        + 0.0722 * channel_luminance(blue)
    )


def contrast_ratio(
    foreground_name: str,
    background_name: str,
    catalog: dict[str, str],
) -> float:
    background = opaque_on_app_background(resolve_rgba(background_name, catalog), catalog)
    foreground = resolve_rgba(foreground_name, catalog)
    foreground = composite(foreground, background)
    lighter = max(luminance(foreground), luminance(background))
    darker = min(luminance(foreground), luminance(background))
    return (lighter + 0.05) / (darker + 0.05)


def material_pairs(names: set[str]) -> list[Pair]:
    pairs: list[Pair] = []
    suffixes = (
        "Primary",
        "PrimaryContainer",
        "Secondary",
        "SecondaryContainer",
        "Background",
        "Surface",
        "SurfaceVariant",
        "Error",
    )
    for scheme in ("light", "dark"):
        for suffix in suffixes:
            foreground = f"md_theme_{scheme}_on{suffix}"
            background = f"md_theme_{scheme}_{suffix[0].lower()}{suffix[1:]}"
            if foreground in names and background in names:
                pairs.append(
                    Pair(
                        label=f"material-{scheme}-{suffix}",
                        foreground=foreground,
                        background=background,
                        minimum=NORMAL_TEXT_MINIMUM,
                    )
                )
    return pairs


def semantic_foregrounds(names: set[str]) -> set[str]:
    required: set[str] = set()
    for name in names:
        if name in EXEMPT_FOREGROUNDS:
            continue
        if re.match(r"^md_theme_(?:light|dark)_on", name):
            required.add(name)
        elif re.match(r"^aqua_(?:content|card_text|state_text|toolbar_icon)_", name):
            required.add(name)
        elif re.match(r"^aqua_(?:status|accent)_(?:success|danger|positive|primary|aqua)$", name):
            required.add(name)
        elif name.endswith("_content"):
            required.add(name)
    return required


def inferred_content_background(foreground: str, names: set[str]) -> str | None:
    prefix = foreground.removesuffix("_content")
    candidates = (
        f"{prefix}_color",
        f"{prefix}_fill",
        f"{prefix}_surface",
        f"{prefix}_background",
    )
    return next((candidate for candidate in candidates if candidate in names), None)


def build_pairs(names: set[str]) -> list[Pair]:
    pairs = material_pairs(names)
    present_dark_surfaces = [name for name in COMMON_DARK_SURFACES if name in names]
    present_card_surfaces = [name for name in CARD_SURFACES if name in names]

    for foreground in sorted(names):
        if foreground in EXEMPT_FOREGROUNDS:
            continue

        if foreground.startswith("aqua_card_text_"):
            for background in present_card_surfaces:
                pairs.append(Pair(f"{foreground}-on-{background}", foreground, background, NORMAL_TEXT_MINIMUM))
        elif foreground.startswith("aqua_state_text_"):
            backgrounds = sorted(name for name in names if name.startswith("aqua_state_") and name.endswith("_surface"))
            if not backgrounds and "aqua_card_surface" in names:
                backgrounds = ["aqua_card_surface"]
            for background in backgrounds:
                pairs.append(Pair(f"{foreground}-on-{background}", foreground, background, NORMAL_TEXT_MINIMUM))
        elif foreground in {
            "aqua_content_primary",
            "aqua_content_primary_soft",
            "aqua_content_secondary",
            "aqua_content_tertiary",
            "aqua_content_muted",
            "aqua_content_placeholder",
            "aqua_content_on_dark",
            "aqua_content_on_dark_argb",
        }:
            for background in present_dark_surfaces:
                pairs.append(Pair(f"{foreground}-on-{background}", foreground, background, NORMAL_TEXT_MINIMUM))
        elif foreground == "aqua_content_warning":
            backgrounds = [name for name in ("aqua_bg_maintenance_profile_percent_warning_fill", "background_color") if name in names]
            for background in backgrounds:
                pairs.append(Pair(f"{foreground}-on-{background}", foreground, background, NORMAL_TEXT_MINIMUM))
        elif foreground.startswith("aqua_toolbar_icon_"):
            for background in present_dark_surfaces[:1]:
                pairs.append(Pair(f"{foreground}-on-{background}", foreground, background, NON_TEXT_MINIMUM))
        elif re.match(r"^aqua_(?:status|accent)_(?:success|danger|positive|primary|aqua)$", foreground):
            for background in present_dark_surfaces[:2]:
                pairs.append(Pair(f"{foreground}-on-{background}", foreground, background, NON_TEXT_MINIMUM))
        elif foreground.endswith("_content"):
            background = inferred_content_background(foreground, names)
            if background is None:
                background = "aqua_card_surface" if "aqua_card_surface" in names else "background_color"
            pairs.append(Pair(f"{foreground}-on-{background}", foreground, background, NORMAL_TEXT_MINIMUM))

    unique: dict[tuple[str, str, float], Pair] = {}
    for pair in pairs:
        unique[(pair.foreground, pair.background, pair.minimum)] = pair
    return list(unique.values())


def main() -> int:
    try:
        base = color_catalog(RES / "values")
        night = color_catalog(RES / "values-night", base)
        names = set(base) | set(night)
        required = semantic_foregrounds(names)
        pairs = build_pairs(names)
        covered = {pair.foreground for pair in pairs}
        missing = sorted(required - covered)
        if missing:
            fail("Semantic foreground roles without a contrast pair: " + ", ".join(missing))

        violations: list[str] = []
        for theme_name, catalog in (("base", base), ("night", night)):
            for pair in pairs:
                if pair.foreground not in catalog or pair.background not in catalog:
                    violations.append(
                        f"{theme_name}/{pair.label}: missing {pair.foreground} or {pair.background}"
                    )
                    continue
                ratio = contrast_ratio(pair.foreground, pair.background, catalog)
                if ratio + 1e-9 < pair.minimum:
                    violations.append(
                        f"{theme_name}/{pair.label}: {ratio:.2f}:1 < {pair.minimum:.1f}:1"
                    )

        if violations:
            fail("WCAG contrast violations:\n- " + "\n- ".join(violations))

    except (GuardFailure, ET.ParseError) as error:
        print(f"ACCESSIBILITY_CONTRAST_GUARD_FAILED: {error}", file=sys.stderr)
        return 1

    print(
        "ACCESSIBILITY_CONTRAST_GUARD_PASS "
        f"semantic_foregrounds={len(required)} pairs={len(pairs)} themes=2"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

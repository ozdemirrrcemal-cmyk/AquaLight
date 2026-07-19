#!/usr/bin/env python3
"""Validate AquaLight semantic Light/Dark color pairs against WCAG contrast thresholds."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
COLOR_REFERENCE = re.compile(r"@color/([A-Za-z0-9_]+)$")
HEX_COLOR = re.compile(r"#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")


@dataclass(frozen=True)
class ContrastContract:
    foreground: str
    background: str
    minimum_ratio: float
    purpose: str


CONTRACTS = (
    ContrastContract("aqua_content_primary", "background_color", 4.5, "primary body text"),
    ContrastContract("aqua_content_secondary", "background_color", 4.5, "secondary body text"),
    ContrastContract("aqua_content_tertiary", "background_color", 4.5, "tertiary body text"),
    ContrastContract("aqua_content_muted", "background_color", 4.5, "muted body text"),
    ContrastContract("md_theme_light_onSurface", "md_theme_light_surface", 4.5, "light theme surface text"),
    ContrastContract("md_theme_dark_onSurface", "md_theme_dark_surface", 4.5, "dark theme surface text"),
    ContrastContract("aqua_content_primary", "aqua_surface_deep", 4.5, "deep surface text"),
    ContrastContract("aqua_content_primary", "aqua_surface_action", 4.5, "action surface text"),
    ContrastContract("aqua_accent_positive", "aqua_surface_positive", 4.5, "positive state text"),
    ContrastContract("aqua_content_primary", "bottom_nav_background", 4.5, "bottom navigation text"),
    ContrastContract("aqua_content_on_dark", "aqua_system_bar_surface", 4.5, "system bar content"),
)


def load_color_layer(directory: Path) -> dict[str, str]:
    colors: dict[str, str] = {}
    if not directory.exists():
        return colors
    for path in sorted(directory.glob("*.xml")):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            raise ValueError(f"Malformed color resource file {path}: {error}") from error
        for element in root.findall("color"):
            name = element.attrib.get("name")
            value = "".join(element.itertext()).strip()
            if name and value:
                colors[name] = value
    return colors


def resolve_color(name: str, colors: dict[str, str], stack: tuple[str, ...] = ()) -> tuple[int, int, int, int]:
    if name in stack:
        raise ValueError(f"Color alias cycle: {' -> '.join((*stack, name))}")
    value = colors.get(name)
    if value is None:
        raise ValueError(f"Missing color resource: {name}")

    reference = COLOR_REFERENCE.fullmatch(value)
    if reference:
        return resolve_color(reference.group(1), colors, (*stack, name))

    match = HEX_COLOR.fullmatch(value)
    if match is None:
        raise ValueError(f"Unsupported color value for {name}: {value}")

    raw = match.group(1)
    if len(raw) == 6:
        alpha = 255
        red, green, blue = (int(raw[index:index + 2], 16) for index in (0, 2, 4))
    else:
        alpha = int(raw[0:2], 16)
        red, green, blue = (int(raw[index:index + 2], 16) for index in (2, 4, 6))
    return alpha, red, green, blue


def composite(foreground: tuple[int, int, int, int], background: tuple[int, int, int, int]) -> tuple[int, int, int]:
    fg_alpha, fg_red, fg_green, fg_blue = foreground
    bg_alpha, bg_red, bg_green, bg_blue = background
    if bg_alpha != 255:
        raise ValueError("Contrast contract backgrounds must resolve to opaque colors.")
    alpha = fg_alpha / 255.0
    return tuple(
        round(fg * alpha + bg * (1.0 - alpha))
        for fg, bg in ((fg_red, bg_red), (fg_green, bg_green), (fg_blue, bg_blue))
    )


def channel_luminance(channel: int) -> float:
    normalized = channel / 255.0
    if normalized <= 0.04045:
        return normalized / 12.92
    return ((normalized + 0.055) / 1.055) ** 2.4


def relative_luminance(rgb: tuple[int, int, int]) -> float:
    red, green, blue = (channel_luminance(channel) for channel in rgb)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue


def contrast_ratio(foreground: tuple[int, int, int], background: tuple[int, int, int]) -> float:
    foreground_luminance = relative_luminance(foreground)
    background_luminance = relative_luminance(background)
    lighter = max(foreground_luminance, background_luminance)
    darker = min(foreground_luminance, background_luminance)
    return (lighter + 0.05) / (darker + 0.05)


def validate_configuration(name: str, colors: dict[str, str]) -> list[str]:
    failures: list[str] = []
    for contract in CONTRACTS:
        try:
            background_argb = resolve_color(contract.background, colors)
            foreground_argb = resolve_color(contract.foreground, colors)
            background_rgb = composite(background_argb, (255, 255, 255, 255))
            foreground_rgb = composite(foreground_argb, background_argb)
            ratio = contrast_ratio(foreground_rgb, background_rgb)
        except ValueError as error:
            failures.append(f"{name}: {contract.purpose}: {error}")
            continue

        if ratio + 1e-9 < contract.minimum_ratio:
            failures.append(
                f"{name}: {contract.purpose}: {contract.foreground} on {contract.background} "
                f"is {ratio:.2f}:1, requires {contract.minimum_ratio:.1f}:1."
            )
        else:
            print(
                f"{name}: {contract.purpose}: {contract.foreground} on "
                f"{contract.background} = {ratio:.2f}:1"
            )
    return failures


def main() -> int:
    try:
        base_colors = load_color_layer(RES / "values")
        night_colors = dict(base_colors)
        night_colors.update(load_color_layer(RES / "values-night"))
    except ValueError as error:
        print(f"WCAG contrast guard failed:\n - {error}")
        return 1

    failures = [
        *validate_configuration("light", base_colors),
        *validate_configuration("dark", night_colors),
    ]
    if failures:
        print("WCAG contrast guard failed:")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("WCAG contrast guard passed for all declared Light/Dark semantic contracts.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

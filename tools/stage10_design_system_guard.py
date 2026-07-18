#!/usr/bin/env python3
"""Fail CI when Stage 10 design-system/resource contracts regress.

The guard is intentionally source-based so it can run before Gradle and report
all remaining migration work with stable file/line diagnostics.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
JAVA = MAIN / "java"
RES = MAIN / "res"

MAX_DIAGNOSTICS = 800


@dataclass(frozen=True)
class Violation:
    path: Path
    line: int
    rule: str
    detail: str

    def render(self) -> str:
        return f"{self.path.relative_to(ROOT)}:{self.line}: [{self.rule}] {self.detail}"


violations: list[Violation] = []


def add(path: Path, line: int, rule: str, detail: str) -> None:
    violations.append(Violation(path, line, rule, detail))


def source_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8", errors="ignore").splitlines()


# User-facing XML values must be resource references. tools:* values are preview-only.
USER_TEXT_ATTRIBUTE = re.compile(
    r"\b(?:android:|app:)?(text|hint|title|summary|contentDescription|label)\s*=\s*\"([^\"]*)\""
)
ALLOWED_XML_TEXT = {
    "",
    "@null",
}

for path in sorted(RES.rglob("*.xml")):
    if any(part.startswith("values") for part in path.relative_to(RES).parts):
        continue
    for line_number, line in enumerate(source_lines(path), start=1):
        if "tools:" in line and not any(
            token in line
            for token in (
                "android:text=",
                "android:hint=",
                "android:title=",
                "android:summary=",
                "android:contentDescription=",
            )
        ):
            continue
        for match in USER_TEXT_ATTRIBUTE.finditer(line):
            value = match.group(2).strip()
            if value in ALLOWED_XML_TEXT or value.startswith(("@", "?")):
                continue
            add(
                path,
                line_number,
                "RAW_XML_TEXT",
                f'{match.group(1)} uses literal "{value}"; move it to strings.xml',
            )


# Kotlin/Java UI boundaries must consume @StringRes or Context resources.
UI_LITERAL_PATTERNS = (
    re.compile(r"\bToast\.makeText\([^\n]*,\s*\"([^\"]+)\""),
    re.compile(r"\bSnackbar\.make\([^\n]*,\s*\"([^\"]+)\""),
    re.compile(r"\bset(?:Title|Message|Text|Hint|ContentDescription)\(\s*\"([^\"]+)\""),
    re.compile(r"\.(?:text|hint|contentDescription)\s*=\s*\"([^\"]+)\""),
    re.compile(r"\b(?:title|message|label|description)\s*=\s*\"([^\"]+)\""),
)

for path in sorted(JAVA.rglob("*")):
    if path.suffix not in {".kt", ".java"}:
        continue
    for line_number, line in enumerate(source_lines(path), start=1):
        stripped = line.strip()
        if stripped.startswith("//") or "@Suppress(\"HardcodedText\")" in line:
            continue
        for pattern in UI_LITERAL_PATTERNS:
            match = pattern.search(line)
            if match:
                add(
                    path,
                    line_number,
                    "RAW_UI_MESSAGE",
                    f'user-visible literal "{match.group(1)}" must be supplied by @StringRes',
                )
                break


# Palette literals belong only in values*/colors.xml.
HEX_COLOR = re.compile(r"(?<![A-Za-z0-9_])#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?(?![A-Za-z0-9_])")
for path in sorted(MAIN.rglob("*")):
    if path.suffix not in {".kt", ".java", ".xml"}:
        continue
    rel = path.relative_to(MAIN)
    if path.name == "colors.xml" and any(part.startswith("values") for part in rel.parts):
        continue
    for line_number, line in enumerate(source_lines(path), start=1):
        match = HEX_COLOR.search(line)
        if match:
            add(
                path,
                line_number,
                "RAW_COLOR",
                f"{match.group(0)} must reference a semantic color token",
            )


# Repeated spacing, shape and typography values are centralized in dimens.xml.
DIMENSION_ATTRIBUTE = re.compile(
    r"\b(?:android:|app:)?(padding(?:Start|End|Left|Right|Top|Bottom|Horizontal|Vertical)?|"
    r"layout_margin(?:Start|End|Left|Right|Top|Bottom|Horizontal|Vertical)?|"
    r"cornerSize(?:TopLeft|TopRight|BottomLeft|BottomRight)?|elevation|textSize|"
    r"minWidth|minHeight|maxWidth|maxHeight|strokeWidth)\s*=\s*\"(-?\d+(?:\.\d+)?(?:dp|sp))\""
)
STYLE_DIMENSION = re.compile(
    r"<item\s+name=\"(?:android:)?(?:width|height|paddingTop|paddingBottom|paddingLeft|paddingRight|"
    r"minWidth|minHeight|textSize|lineSpacingExtra|cornerSize(?:TopLeft|TopRight|BottomLeft|BottomRight)?)\""
    r">\s*(-?\d+(?:\.\d+)?(?:dp|sp))\s*</item>"
)

for path in sorted(RES.rglob("*.xml")):
    if path.name == "dimens.xml" and any(part.startswith("values") for part in path.relative_to(RES).parts):
        continue
    for line_number, line in enumerate(source_lines(path), start=1):
        match = DIMENSION_ATTRIBUTE.search(line) or STYLE_DIMENSION.search(line)
        if not match:
            continue
        value = match.group(match.lastindex or 1)
        if value in {"0dp", "0sp"}:
            continue
        add(
            path,
            line_number,
            "RAW_DIMENSION",
            f"{value} must reference a semantic dimen/typography token",
        )


# Explicit legacy names are forbidden after migration.
LEGACY_STYLE_NAMES = ("RedButton", "BlackButton", "WhiteButton")
for path in sorted(MAIN.rglob("*")):
    if path.suffix not in {".kt", ".java", ".xml"}:
        continue
    for line_number, line in enumerate(source_lines(path), start=1):
        for name in LEGACY_STYLE_NAMES:
            if re.search(rf"(?<![A-Za-z0-9_.]){re.escape(name)}(?![A-Za-z0-9_.])", line):
                add(
                    path,
                    line_number,
                    "LEGACY_STYLE",
                    f"{name} must be replaced by a semantic Widget.Aqua.* style",
                )


# Bottom navigation/menu titles must always be string resources.
for path in sorted(RES.rglob("*.xml")):
    if "menu" not in path.relative_to(RES).parts:
        continue
    for line_number, line in enumerate(source_lines(path), start=1):
        match = re.search(r"android:title\s*=\s*\"([^\"]+)\"", line)
        if match and not match.group(1).startswith("@string/"):
            add(
                path,
                line_number,
                "MENU_STRING_RESOURCE",
                f'menu title "{match.group(1)}" must use @string/...',
            )


# The product supports both light and dark modes. Their splash typography must share tokens.
for required in (
    RES / "values/themes.xml",
    RES / "values-night/themes.xml",
    RES / "values/colors.xml",
    RES / "values-night/colors.xml",
    RES / "values/dimens.xml",
    RES / "values/strings.xml",
    ROOT / "docs/stage10-design-system-contract.md",
):
    if not required.is_file():
        add(required, 1, "REQUIRED_RESOURCE", "required Stage 10 contract/resource is missing")

night_theme = RES / "values-night/themes.xml"
if night_theme.is_file():
    for line_number, line in enumerate(source_lines(night_theme), start=1):
        if re.search(r">\s*\d+(?:\.\d+)?sp\s*</item>", line):
            add(
                night_theme,
                line_number,
                "NIGHT_SPLASH_TYPOGRAPHY",
                "night theme typography must reference the shared @dimen token",
            )

base_theme = RES / "values/themes.xml"
if base_theme.is_file():
    theme_text = base_theme.read_text(encoding="utf-8", errors="ignore")
    if "<item name=\"android:windowLightStatusBar\">true</item>" in theme_text:
        add(
            base_theme,
            1,
            "SYSTEM_BAR_CONTRAST",
            "base palette is dark; light status-bar icons must not be requested",
        )
    if "<item name=\"android:windowLightNavigationBar\">true</item>" in theme_text:
        add(
            base_theme,
            1,
            "SYSTEM_BAR_CONTRAST",
            "dark navigation bar must not request dark navigation icons",
        )


if violations:
    print("Stage 10 design-system/resource guard failed:")
    for violation in violations[:MAX_DIAGNOSTICS]:
        print(f" - {violation.render()}")
    hidden = len(violations) - MAX_DIAGNOSTICS
    if hidden > 0:
        print(f" - ... {hidden} additional violations omitted")
    print(f"TOTAL STAGE 10 VIOLATIONS: {len(violations)}")
    sys.exit(1)

print("Stage 10 design-system/resource guard passed.")

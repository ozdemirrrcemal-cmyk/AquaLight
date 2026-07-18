#!/usr/bin/env python3
"""Centralize Kotlin palette literals without changing rendered ARGB values."""

from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
TOKEN_PATH = JAVA / "com/aqua/aqualight/designsystem/AquaColorTokens.kt"
TOKEN_FQN = "com.aqua.aqualight.designsystem.AquaColorTokens"
HEX = re.compile(r"#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?")
PARSE = re.compile(
    r"(?:android\.graphics\.)?Color\.parseColor\(\s*\"(?P<hex>#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?)\"\s*\)"
)
STRING = re.compile(r'\"(?P<hex>#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?)\"')


def token_suffix(value: str) -> str:
    return value.removeprefix("#").upper()


def main() -> None:
    kotlin_files = sorted(path for path in JAVA.rglob("*.kt") if path != TOKEN_PATH)
    values: set[str] = set()
    for path in kotlin_files:
        values.update(match.group(0).upper() for match in HEX.finditer(path.read_text(encoding="utf-8")))

    if not values:
        print("No Kotlin palette literals found.")
        return

    TOKEN_PATH.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "package com.aqua.aqualight.designsystem",
        "",
        "import android.graphics.Color",
        "import androidx.annotation.ColorInt",
        "",
        "/** Exact, centralized legacy palette values retained during the visual-neutral Stage 10 migration. */",
        "object AquaColorTokens {",
    ]
    for value in sorted(values):
        suffix = token_suffix(value)
        lines.append(f'    const val HEX_{suffix}: String = "{value}"')
        lines.append(f"    @ColorInt val COLOR_{suffix}: Int = Color.parseColor(HEX_{suffix})")
    lines.append("}")
    lines.append("")
    TOKEN_PATH.write_text("\n".join(lines), encoding="utf-8")

    changed = 0
    for path in kotlin_files:
        original = path.read_text(encoding="utf-8")

        def replace_parse(match: re.Match[str]) -> str:
            suffix = token_suffix(match.group("hex"))
            return f"{TOKEN_FQN}.COLOR_{suffix}"

        updated = PARSE.sub(replace_parse, original)

        def replace_string(match: re.Match[str]) -> str:
            suffix = token_suffix(match.group("hex"))
            return f"{TOKEN_FQN}.HEX_{suffix}"

        updated = STRING.sub(replace_string, updated)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed += 1

    remaining: list[str] = []
    for path in kotlin_files:
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if HEX.search(line):
                remaining.append(f"{path.relative_to(ROOT)}:{line_number}: {line.strip()}")
    if remaining:
        raise RuntimeError("Unmigrated Kotlin colors:\n" + "\n".join(remaining))

    print(f"Centralized {len(values)} exact colors across {changed} Kotlin files.")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Temporary Stage 11 semantic color usage inventory."""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
APP = ROOT / "app/src/main"

ROLE_PATTERN = re.compile(
    r"(?:@color/|R\.color\.)("
    r"(?:aqua_(?:content|card_text|state_text|toolbar_icon)_[A-Za-z0-9_]+)"
    r"|(?:aqua_(?:status|accent)_(?:success|danger|positive|primary|aqua))"
    r"|(?:aqua_[A-Za-z0-9_]+_content)"
    r"|(?:md_theme_(?:light|dark)_on[A-Za-z0-9_]+)"
    r")"
)


def main() -> int:
    usages: dict[str, list[str]] = {}
    for path in sorted(APP.rglob("*")):
        if not path.is_file() or path.suffix not in {".xml", ".kt", ".java"}:
            continue
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(lines, start=1):
            for match in ROLE_PATTERN.finditer(line):
                usages.setdefault(match.group(1), []).append(
                    f"{path.relative_to(ROOT)}:{number}: {line.strip()}"
                )

    print("STAGE11_CONTRAST_USAGE_INVENTORY")
    for role in sorted(usages):
        print(f"[{role}]")
        for usage in usages[role]:
            print(f"- {usage}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

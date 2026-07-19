#!/usr/bin/env python3
"""Reject user-facing formatting that bypasses AquaLight's application locale."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCAN_ROOTS = (
    ROOT / "app/src/main/java/com/aqua/aqualight/ui",
    ROOT / "app/src/main/java/com/aqua/aqualight/platform/text",
)
PATTERNS = {
    "Locale.getDefault()": re.compile(r"\bLocale\.getDefault\s*\("),
    "Locale.US": re.compile(r"\bLocale\.US\b"),
    "DecimalFormat": re.compile(r"\bDecimalFormat\s*\("),
    "String.format": re.compile(r"\bString\.format\s*\("),
    "locale-less SimpleDateFormat": re.compile(
        r"\bSimpleDateFormat\s*\(\s*(?:\"[^\"]*\"|[A-Z_][A-Z0-9_]*)\s*\)"
    ),
}


def main() -> int:
    violations: list[str] = []
    for scan_root in SCAN_ROOTS:
        for path in sorted(scan_root.rglob("*.kt")):
            source = path.read_text(encoding="utf-8")
            for label, pattern in PATTERNS.items():
                for match in pattern.finditer(source):
                    line = source.count("\n", 0, match.start()) + 1
                    violations.append(
                        f"{path.relative_to(ROOT)}:{line} bypasses app locale via {label}"
                    )

    if violations:
        print("UI_LOCALE_BOUNDARY_GUARD_FAILED", file=sys.stderr)
        for violation in violations:
            print(f"- {violation}", file=sys.stderr)
        return 1

    print("UI_LOCALE_BOUNDARY_GUARD_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Enforce zero direct dependencies between AquaLight UI and data layers.

UI may consume application/common contracts, but it must not reference concrete
`com.aqua.aqualight.data.*` types. Data must remain presentation-agnostic and
must not reference `com.aqua.aqualight.ui.*` types.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = Path("app/src/main/java/com/aqua/aqualight")
UI_ROOT = SOURCE_ROOT / "ui"
DATA_ROOT = SOURCE_ROOT / "data"

NON_CODE = re.compile(
    r'""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/',
    re.DOTALL,
)
UI_REFERENCE = re.compile(r"\bcom\.aqua\.aqualight\.ui(?:\.[A-Za-z_][A-Za-z0-9_]*)*")
DATA_REFERENCE = re.compile(r"\bcom\.aqua\.aqualight\.data(?:\.[A-Za-z_][A-Za-z0-9_]*)*")


def _strip_comments_and_literals(source: str) -> str:
    def replace(match: re.Match[str]) -> str:
        value = match.group(0)
        return "\n" * value.count("\n") if "\n" in value else " "

    return NON_CODE.sub(replace, source)


def _references(source: str, pattern: re.Pattern[str]) -> list[str]:
    code = _strip_comments_and_literals(source)
    return sorted(set(match.group(0) for match in pattern.finditer(code)))


def _scan_direction(
    repository_root: Path,
    source_root: Path,
    forbidden_pattern: re.Pattern[str],
    source_label: str,
    forbidden_label: str,
) -> list[str]:
    errors: list[str] = []
    root = repository_root / source_root
    if not root.is_dir():
        return errors

    for path in sorted(root.rglob("*.kt")):
        source = path.read_text(encoding="utf-8", errors="ignore")
        references = _references(source, forbidden_pattern)
        for reference in references:
            errors.append(
                f"{path.relative_to(repository_root).as_posix()}: {source_label} layer must not "
                f"depend on {forbidden_label} layer: {reference}"
            )
    return errors


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    errors.extend(
        _scan_direction(
            repository_root=repository_root,
            source_root=UI_ROOT,
            forbidden_pattern=DATA_REFERENCE,
            source_label="UI",
            forbidden_label="data",
        )
    )
    errors.extend(
        _scan_direction(
            repository_root=repository_root,
            source_root=DATA_ROOT,
            forbidden_pattern=UI_REFERENCE,
            source_label="data",
            forbidden_label="UI",
        )
    )
    return errors


def main() -> int:
    errors = validate_repository()
    if errors:
        print("UI/data layer isolation guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("UI/data layer isolation guard passed: direct dependencies are forbidden both ways.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Enforce a zero-tolerance boundary between AquaLight UI and data layers."""
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
# The negative lookahead after the package segment is intentional. Without it,
# `com.aqua.aqualight.databinding.*` is incorrectly matched as
# `com.aqua.aqualight.data`, which turns every ViewBinding import into a false
# architecture violation.
UI_REFERENCE = re.compile(
    r"\bcom\.aqua\.aqualight\.ui(?![A-Za-z0-9_])(?:\.[A-Za-z_][A-Za-z0-9_]*)*"
)
DATA_REFERENCE = re.compile(
    r"\bcom\.aqua\.aqualight\.data(?![A-Za-z0-9_])(?:\.[A-Za-z_][A-Za-z0-9_]*)*"
)


def _strip_comments_and_literals(source: str) -> str:
    def replace(match: re.Match[str]) -> str:
        value = match.group(0)
        return "\n" * value.count("\n") if "\n" in value else " "

    return NON_CODE.sub(replace, source)


def _references(source: str, pattern: re.Pattern[str]) -> set[str]:
    code = _strip_comments_and_literals(source)
    return {match.group(0) for match in pattern.finditer(code)}


def _collect_edges(
    repository_root: Path,
    source_root: Path,
    forbidden_pattern: re.Pattern[str],
) -> dict[str, set[str]]:
    edges: dict[str, set[str]] = {}
    root = repository_root / source_root
    if not root.is_dir():
        return edges

    for path in sorted(root.rglob("*.kt")):
        source = path.read_text(encoding="utf-8", errors="ignore")
        references = _references(source, forbidden_pattern)
        if references:
            edges[path.relative_to(repository_root).as_posix()] = references
    return edges


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    errors: list[str] = []

    current_ui_to_data = _collect_edges(
        repository_root=repository_root,
        source_root=UI_ROOT,
        forbidden_pattern=DATA_REFERENCE,
    )
    for source_path, references in sorted(current_ui_to_data.items()):
        for reference in sorted(references):
            errors.append(
                f"{source_path}: UI layer must not depend on data layer: {reference}"
            )

    current_data_to_ui = _collect_edges(
        repository_root=repository_root,
        source_root=DATA_ROOT,
        forbidden_pattern=UI_REFERENCE,
    )
    for source_path, references in sorted(current_data_to_ui.items()):
        for reference in sorted(references):
            errors.append(
                f"{source_path}: data layer must not depend on UI layer: {reference}"
            )

    return errors


def main() -> int:
    errors = validate_repository()
    if errors:
        print("UI/data layer isolation guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print(
        "UI/data layer isolation guard passed: UI and data have no direct dependencies."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Prevent new direct dependencies between AquaLight UI and data layers.

Data -> UI is zero-tolerance.
UI -> data is also forbidden for all new dependency edges. A small, explicit
baseline freezes legacy UI -> data edges that pre-date this guard; the baseline
may shrink as debt is removed but must not be used for new dependencies.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = Path("app/src/main/java/com/aqua/aqualight")
UI_ROOT = SOURCE_ROOT / "ui"
DATA_ROOT = SOURCE_ROOT / "data"
BASELINE_PATH = Path("config/architecture/ui-data-isolation-baseline.json")

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


def _load_ui_to_data_baseline(repository_root: Path) -> dict[str, set[str]]:
    path = repository_root / BASELINE_PATH
    if not path.is_file():
        return {}

    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read UI/data isolation baseline: {exc}") from exc

    if payload.get("schemaVersion") != 1:
        raise ValueError("UI/data isolation baseline schemaVersion must be 1")

    raw_edges = payload.get("uiToData")
    if not isinstance(raw_edges, dict):
        raise ValueError("UI/data isolation baseline uiToData must be an object")

    baseline: dict[str, set[str]] = {}
    for source_path, references in raw_edges.items():
        if not isinstance(source_path, str) or not isinstance(references, list):
            raise ValueError("UI/data isolation baseline entries must map paths to lists")
        if not source_path.startswith(UI_ROOT.as_posix() + "/"):
            raise ValueError(f"baseline path is outside UI layer: {source_path}")
        values = set()
        for reference in references:
            if not isinstance(reference, str) or not DATA_REFERENCE.fullmatch(reference):
                raise ValueError(
                    f"invalid UI -> data baseline reference for {source_path}: {reference!r}"
                )
            values.add(reference)
        if values:
            baseline[source_path] = values
    return baseline


def _edge_set(edges: dict[str, set[str]]) -> set[tuple[str, str]]:
    return {
        (source_path, reference)
        for source_path, references in edges.items()
        for reference in references
    }


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    errors: list[str] = []

    try:
        legacy_ui_to_data = _load_ui_to_data_baseline(repository_root)
    except ValueError as exc:
        return [str(exc)]

    current_ui_to_data = _collect_edges(
        repository_root=repository_root,
        source_root=UI_ROOT,
        forbidden_pattern=DATA_REFERENCE,
    )
    current_ui_edges = _edge_set(current_ui_to_data)
    baseline_ui_edges = _edge_set(legacy_ui_to_data)

    for source_path, reference in sorted(current_ui_edges - baseline_ui_edges):
        errors.append(
            f"{source_path}: UI layer must not add a dependency on data layer: {reference}"
        )

    # Baseline is debt, not an allowlist for eternity. When an existing edge is
    # removed, require the baseline to shrink in the same change.
    for source_path, reference in sorted(baseline_ui_edges - current_ui_edges):
        errors.append(
            f"{source_path}: stale UI -> data baseline edge must be removed: {reference}"
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
        "UI/data layer isolation guard passed: data -> UI is zero-tolerance and "
        "no new UI -> data dependency edges were introduced."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Enforce strict implementation isolation between AquaLight device families.

Family-owned code may depend on shared device/application/UI contracts, but one
family must never depend on another family's implementation or family-specific
symbols. Shared composition code outside family-owned packages may select or
wire multiple families; dependency between family implementations is forbidden.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = Path("app/src/main/java/com/aqua/aqualight")

LAYER_ROOTS = {
    "application": JAVA_ROOT / "application/devices",
    "data": JAVA_ROOT / "data/devices",
    "ui": JAVA_ROOT / "ui/tabs/devices/detail",
}
FAMILIES = ("dosing", "cooling")

PACKAGE_REFERENCE = re.compile(
    r"\bcom\.aqua\.aqualight(?:\.[A-Za-z_][A-Za-z0-9_]*)+"
)
NON_CODE = re.compile(
    r'""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/',
    re.DOTALL,
)
FAMILY_IDENTIFIER = {
    "dosing": re.compile(
        r"\b(?:[A-Za-z_][A-Za-z0-9_]*Dosing[A-Za-z0-9_]*|DOSING(?:_[A-Z0-9_]+)?)\b"
    ),
    "cooling": re.compile(
        r"\b(?:[A-Za-z_][A-Za-z0-9_]*Cooling[A-Za-z0-9_]*|COOLING(?:_[A-Z0-9_]+)?)\b"
    ),
}


def _relative(path: Path, repository_root: Path) -> str:
    return path.relative_to(repository_root).as_posix()


def _strip_comments_and_literals(source: str) -> str:
    def replace(match: re.Match[str]) -> str:
        value = match.group(0)
        return "\n" * value.count("\n") if "\n" in value else " "

    return NON_CODE.sub(replace, source)


def _owned_family(
    path: Path,
    repository_root: Path,
) -> tuple[str, str] | None:
    for layer, relative_root in LAYER_ROOTS.items():
        layer_root = repository_root / relative_root
        try:
            owned_path = path.relative_to(layer_root)
        except ValueError:
            continue

        markers = {
            part.lower()
            for part in owned_path.parts[:-1]
            if part.lower() in FAMILIES
        }
        if len(markers) == 1:
            return layer, next(iter(markers))
        if len(markers) > 1:
            return layer, "mixed"
    return None


def _cross_family_package_references(source: str, other_family: str) -> list[str]:
    references: list[str] = []
    for match in PACKAGE_REFERENCE.finditer(source):
        reference = match.group(0)
        if other_family in {part.lower() for part in reference.split(".")}:
            references.append(reference)
    return sorted(set(references))


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    errors: list[str] = []

    for relative_root in LAYER_ROOTS.values():
        layer_root = repository_root / relative_root
        if not layer_root.is_dir():
            continue

        for path in sorted(layer_root.rglob("*.kt")):
            ownership = _owned_family(path, repository_root)
            if ownership is None:
                continue

            layer, family = ownership
            relative_path = _relative(path, repository_root)
            if family == "mixed":
                errors.append(
                    f"{relative_path}: one source path cannot be owned by multiple device families"
                )
                continue

            other_family = "cooling" if family == "dosing" else "dosing"
            source = path.read_text(encoding="utf-8", errors="ignore")

            for reference in _cross_family_package_references(source, other_family):
                errors.append(
                    f"{relative_path}: {family} {layer} implementation must not depend on "
                    f"{other_family} implementation package: {reference}"
                )

            code = _strip_comments_and_literals(source)
            match = FAMILY_IDENTIFIER[other_family].search(code)
            if match is not None:
                errors.append(
                    f"{relative_path}: {family} {layer} implementation must not reference "
                    f"{other_family}-specific symbol: {match.group(0)}"
                )

    return errors


def main() -> int:
    errors = validate_repository()
    if errors:
        print("Device family isolation guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print(
        "Device family isolation guard passed: Dosing and Cooling implementations are isolated."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

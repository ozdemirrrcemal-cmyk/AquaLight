#!/usr/bin/env python3
"""Fail when a GitHub workflow or composite action uses a mutable remote ref."""

from __future__ import annotations

import re
import sys
from pathlib import Path

USES_PATTERN = re.compile(r"^\s*-?\s*uses:\s*['\"]?([^'\"\s#]+)", re.MULTILINE)
FULL_COMMIT_SHA = re.compile(r"^[0-9a-fA-F]{40}$")


def iter_action_files(root: Path):
    for pattern in (
        ".github/workflows/**/*.yml",
        ".github/workflows/**/*.yaml",
        ".github/actions/**/*.yml",
        ".github/actions/**/*.yaml",
    ):
        yield from root.glob(pattern)


def validate(root: Path) -> list[str]:
    failures: list[str] = []
    for path in sorted(set(iter_action_files(root))):
        text = path.read_text(encoding="utf-8")
        for match in USES_PATTERN.finditer(text):
            target = match.group(1)
            if target.startswith("./"):
                continue
            if target.startswith("docker://"):
                if "@sha256:" not in target:
                    line = text.count("\n", 0, match.start()) + 1
                    failures.append(f"{path}:{line}: Docker action is not digest-pinned: {target}")
                continue
            if "@" not in target:
                line = text.count("\n", 0, match.start()) + 1
                failures.append(f"{path}:{line}: action has no ref: {target}")
                continue
            reference = target.rsplit("@", 1)[1]
            if not FULL_COMMIT_SHA.fullmatch(reference):
                line = text.count("\n", 0, match.start()) + 1
                failures.append(f"{path}:{line}: action is not pinned to a full commit SHA: {target}")
    return failures


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    failures = validate(root)
    if failures:
        print("Mutable GitHub Action references detected:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print("All remote GitHub Actions are pinned to full commit SHAs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

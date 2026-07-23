#!/usr/bin/env python3
"""Fail when an external GitHub Action is not pinned to a full commit SHA."""

from __future__ import annotations

import re
import sys
from pathlib import Path

USES_PATTERN = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)")
FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")


def validate(workflow_dir: Path) -> list[str]:
    errors: list[str] = []
    for path in sorted(workflow_dir.glob("*.y*ml")):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            match = USES_PATTERN.match(line)
            if not match:
                continue
            reference = match.group(1)
            if reference.startswith("./") or reference.startswith("docker://"):
                continue
            if "@" not in reference:
                errors.append(f"{path}:{line_number}: action has no @ref: {reference}")
                continue
            _, ref = reference.rsplit("@", 1)
            if not FULL_SHA_PATTERN.fullmatch(ref):
                errors.append(
                    f"{path}:{line_number}: action must use a 40-character commit SHA: {reference}"
                )
    return errors


def main() -> int:
    errors = validate(Path(".github/workflows"))
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("All external GitHub Actions are pinned to immutable commit SHAs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

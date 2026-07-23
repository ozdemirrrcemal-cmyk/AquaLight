#!/usr/bin/env python3
"""Fail when a third-party GitHub Action is not pinned to an immutable commit SHA."""

from __future__ import annotations

import re
import sys
from pathlib import Path

USES_PATTERN = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)")
FULL_SHA_PATTERN = re.compile(r"^[^@]+@[0-9a-fA-F]{40}$")


def workflow_files(root: Path) -> list[Path]:
    paths = list((root / ".github" / "workflows").glob("*.yml"))
    paths.extend((root / ".github" / "workflows").glob("*.yaml"))
    paths.extend((root / ".github" / "actions").glob("**/action.yml"))
    paths.extend((root / ".github" / "actions").glob("**/action.yaml"))
    return sorted(set(paths))


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    violations: list[str] = []
    for path in workflow_files(root):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            match = USES_PATTERN.match(line)
            if not match:
                continue
            target = match.group(1).strip('"\'')
            if target.startswith("./") or target.startswith("docker://"):
                continue
            if not FULL_SHA_PATTERN.fullmatch(target):
                violations.append(f"{path.relative_to(root)}:{line_number}: {target}")

    if violations:
        print("GitHub Actions must be pinned to full commit SHAs:", file=sys.stderr)
        for violation in violations:
            print(f"  {violation}", file=sys.stderr)
        return 1
    print("GitHub Action pinning policy passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

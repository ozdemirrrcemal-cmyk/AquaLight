#!/usr/bin/env python3
"""Validate release-time supply-chain controls for AquaLight.

The script is intentionally dependency-free so it can run before Gradle resolves
any third-party artifact. It checks immutable GitHub Action references and, when
requested, verifies that Gradle dependency lock/verification state is committed.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

USES_PATTERN = re.compile(r"^\s*-?\s*uses:\s*([^\s#]+)", re.MULTILINE)
FULL_SHA_PATTERN = re.compile(r"^[0-9a-fA-F]{40}$")


def mutable_action_references(workflow_directory: Path) -> list[str]:
    failures: list[str] = []
    for workflow in sorted(workflow_directory.glob("*.y*ml")):
        text = workflow.read_text(encoding="utf-8")
        for match in USES_PATTERN.finditer(text):
            reference = match.group(1)
            if reference.startswith("./"):
                continue
            if "@" not in reference:
                failures.append(f"{workflow}: action reference has no ref: {reference}")
                continue
            action, ref = reference.rsplit("@", 1)
            if not FULL_SHA_PATTERN.fullmatch(ref):
                failures.append(
                    f"{workflow}: {action}@{ref} is mutable; pin a full 40-character commit SHA"
                )
    return failures


def dependency_state_failures(repository_root: Path) -> list[str]:
    failures: list[str] = []
    metadata = repository_root / "gradle" / "verification-metadata.xml"
    if not metadata.is_file() or metadata.stat().st_size == 0:
        failures.append(
            "gradle/verification-metadata.xml is missing; generate SHA-256 verification metadata"
        )

    lockfiles = sorted(
        path
        for path in repository_root.rglob("*.lockfile")
        if ".gradle" not in path.parts and "build" not in path.parts
    )
    if not lockfiles:
        failures.append(
            "No Gradle dependency lockfile is committed; generate locks with --write-locks"
        )
    return failures


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    parser.add_argument(
        "--strict-dependency-state",
        action="store_true",
        help="Fail when Gradle lockfiles or verification metadata are not committed",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.repository_root.resolve()
    failures = mutable_action_references(root / ".github" / "workflows")
    if args.strict_dependency_state:
        failures.extend(dependency_state_failures(root))

    if failures:
        print("Supply-chain gate failed:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    print("Supply-chain gate passed: immutable action refs and requested dependency state verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

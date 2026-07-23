#!/usr/bin/env python3
"""Fail when a workflow uses a mutable third-party GitHub Action reference."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

USES_PATTERN = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)")
FULL_COMMIT_SHA_PATTERN = re.compile(r"^[0-9a-fA-F]{40}$")
DOCKER_DIGEST_PATTERN = re.compile(r"^docker://[^@\s]+@sha256:[0-9a-fA-F]{64}$")


@dataclass(frozen=True)
class PinViolation:
    path: Path
    line_number: int
    reference: str
    reason: str

    def render(self) -> str:
        return f"{self.path}:{self.line_number}: {self.reason}: {self.reference}"


def normalize_reference(raw_reference: str) -> str:
    return raw_reference.strip().strip('"\'')


def validate_reference(path: Path, line_number: int, reference: str) -> PinViolation | None:
    normalized = normalize_reference(reference)

    if normalized.startswith("./"):
        return None

    if normalized.startswith("docker://"):
        if DOCKER_DIGEST_PATTERN.fullmatch(normalized):
            return None
        return PinViolation(
            path,
            line_number,
            normalized,
            "container actions must be pinned to a sha256 digest",
        )

    if "@" not in normalized:
        return PinViolation(
            path,
            line_number,
            normalized,
            "third-party actions must include an immutable commit reference",
        )

    action_name, revision = normalized.rsplit("@", 1)
    if not action_name or not FULL_COMMIT_SHA_PATTERN.fullmatch(revision):
        return PinViolation(
            path,
            line_number,
            normalized,
            "third-party actions must be pinned to a full 40-character commit SHA",
        )

    return None


def validate_workflow_text(path: Path, content: str) -> list[PinViolation]:
    violations: list[PinViolation] = []
    for line_number, line in enumerate(content.splitlines(), start=1):
        match = USES_PATTERN.match(line)
        if match is None:
            continue
        violation = validate_reference(path, line_number, match.group(1))
        if violation is not None:
            violations.append(violation)
    return violations


def discover_workflows(workflow_root: Path) -> list[Path]:
    return sorted(
        path
        for pattern in ("*.yml", "*.yaml")
        for path in workflow_root.glob(pattern)
        if path.is_file()
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Verify that every third-party GitHub Action is commit-SHA pinned."
    )
    parser.add_argument(
        "--workflow-root",
        type=Path,
        default=Path(".github/workflows"),
        help="Directory containing GitHub Actions workflow files",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    workflow_root = args.workflow_root

    if not workflow_root.is_dir():
        print(f"workflow directory not found: {workflow_root}", file=sys.stderr)
        return 2

    workflows = discover_workflows(workflow_root)
    if not workflows:
        print(f"no workflow files found under {workflow_root}", file=sys.stderr)
        return 2

    violations = [
        violation
        for workflow in workflows
        for violation in validate_workflow_text(
            workflow,
            workflow.read_text(encoding="utf-8"),
        )
    ]

    if violations:
        print("Mutable GitHub Action reference(s) detected:", file=sys.stderr)
        for violation in violations:
            print(f"- {violation.render()}", file=sys.stderr)
        return 1

    print(f"GitHub Action pin guard passed for {len(workflows)} workflow file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

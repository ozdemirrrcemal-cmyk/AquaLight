#!/usr/bin/env python3
"""Reject mutable GitHub Action and reusable-workflow references."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

USES_PATTERN = re.compile(r"(?m)^\s*(?:-\s*)?uses:\s*([^\s#]+)")
FULL_SHA = re.compile(r"^[0-9a-fA-F]{40}$")
DOCKER_DIGEST = re.compile(r"^docker://[^\s@]+@sha256:[0-9a-fA-F]{64}$")


class PinningFailure(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workflows", type=Path, default=Path(".github/workflows"))
    parser.add_argument("--summary", type=Path, required=True)
    return parser.parse_args()


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def validate_reference(reference: str) -> str | None:
    if reference.startswith("./"):
        return None
    if reference.startswith("docker://"):
        return None if DOCKER_DIGEST.fullmatch(reference) else "Docker image is not pinned by sha256 digest"
    repository, separator, revision = reference.rpartition("@")
    if not separator or not repository or not revision:
        return "Action reference must use owner/repository@full-commit-sha"
    if not FULL_SHA.fullmatch(revision):
        return "Action reference is not pinned to a full 40-character commit SHA"
    return None


def main() -> int:
    args = parse_args()
    try:
        if not args.workflows.is_dir():
            raise PinningFailure(f"Workflow directory is missing: {args.workflows}")
        workflow_paths = sorted(
            path
            for pattern in ("*.yml", "*.yaml")
            for path in args.workflows.rglob(pattern)
            if path.is_file()
        )
        if not workflow_paths:
            raise PinningFailure(f"No workflow files found under {args.workflows}.")

        references: list[dict[str, object]] = []
        failures: list[dict[str, object]] = []
        for path in workflow_paths:
            text = path.read_text(encoding="utf-8")
            for match in USES_PATTERN.finditer(text):
                reference = match.group(1).strip("'\"")
                line = text.count("\n", 0, match.start()) + 1
                reason = validate_reference(reference)
                record = {
                    "workflow": path.as_posix(),
                    "line": line,
                    "reference": reference,
                    "local": reference.startswith("./"),
                    "approved": reason is None,
                }
                references.append(record)
                if reason is not None:
                    failures.append({**record, "reason": reason})

        if not references:
            raise PinningFailure("No uses: references were found in workflow files.")

        summary = {
            "schemaVersion": 1,
            "approved": not failures,
            "workflowCount": len(workflow_paths),
            "referenceCount": len(references),
            "externalReferenceCount": sum(not bool(item["local"]) for item in references),
            "localReferenceCount": sum(bool(item["local"]) for item in references),
            "failures": failures,
            "references": references,
        }
        write_json(args.summary, summary)
        if failures:
            raise PinningFailure(
                "Mutable workflow references found: "
                + "; ".join(
                    f"{item['workflow']}:{item['line']} {item['reference']}"
                    for item in failures
                )
            )
        print(
            f"GitHub Action pinning approved: {len(workflow_paths)} workflows, "
            f"{summary['externalReferenceCount']} external references."
        )
        return 0
    except (OSError, PinningFailure) as error:
        if not args.summary.exists():
            write_json(
                args.summary,
                {"schemaVersion": 1, "approved": False, "failure": str(error)},
            )
        print(f"GitHub Action pinning verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

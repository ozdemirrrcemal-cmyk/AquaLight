#!/usr/bin/env python3
"""Create a strict, machine-readable Trust Refresh provenance artifact."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

SHA_RE = re.compile(r"^[0-9a-f]{40}$")
HEAD_REF_RE = re.compile(r"^dependabot/gradle/[A-Za-z0-9._/-]+$")


def positive_integer(value: str) -> int:
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError("value must be a positive integer")
    return parsed


def pinned_sha(value: str) -> str:
    if not SHA_RE.fullmatch(value):
        raise argparse.ArgumentTypeError("value must be a lowercase 40-character SHA")
    return value


def dependabot_head_ref(value: str) -> str:
    if (
        not HEAD_REF_RE.fullmatch(value)
        or ".." in value
        or value.endswith("/")
    ):
        raise argparse.ArgumentTypeError("value must be a canonical Dependabot Gradle ref")
    return value


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pull-request", required=True, type=positive_integer)
    parser.add_argument("--head-ref", required=True, type=dependabot_head_ref)
    parser.add_argument("--head-sha", required=True, type=pinned_sha)
    parser.add_argument("--base-sha", required=True, type=pinned_sha)
    parser.add_argument("--source-run-id", required=True, type=positive_integer)
    parser.add_argument("--dependency-names", required=True)
    parser.add_argument(
        "--dependency-type",
        required=True,
        choices=("direct:development", "direct:production", "indirect"),
    )
    parser.add_argument(
        "--update-type",
        required=True,
        choices=(
            "version-update:semver-major",
            "version-update:semver-minor",
            "version-update:semver-patch",
        ),
    )
    parser.add_argument("--package-ecosystem", required=True, choices=("gradle",))
    parser.add_argument(
        "--maintainer-changes",
        required=True,
        choices=("true", "false"),
    )
    parser.add_argument("--pr-files", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def normalized_unique_strings(values: list[str], description: str) -> list[str]:
    normalized = sorted({value.strip() for value in values if value.strip()})
    if not normalized:
        raise ValueError(f"{description} must contain at least one value")
    return normalized


def create_metadata(args: argparse.Namespace) -> dict[str, object]:
    if args.maintainer_changes not in {"true", "false"}:
        raise ValueError("maintainer changes must be exactly true or false")
    dependency_names = normalized_unique_strings(
        args.dependency_names.split(","),
        "dependency names",
    )
    try:
        pr_lines = args.pr_files.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise ValueError(f"cannot read PR file list {args.pr_files}: {error}") from error
    initial_pr_files = normalized_unique_strings(pr_lines, "initial PR files")
    return {
        "schemaVersion": 1,
        "pull_request": args.pull_request,
        "head_ref": args.head_ref,
        "head_sha": args.head_sha,
        "base_sha": args.base_sha,
        "source_run_id": args.source_run_id,
        "dependency_names": dependency_names,
        "dependency_type": args.dependency_type,
        "update_type": args.update_type,
        "package_ecosystem": args.package_ecosystem,
        "maintainer_changes": args.maintainer_changes == "true",
        "initial_pr_files": initial_pr_files,
    }


def main() -> int:
    args = parse_args()
    try:
        metadata = create_metadata(args)
    except ValueError as error:
        raise SystemExit(f"Cannot create Dependabot trust metadata: {error}") from error
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(metadata, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

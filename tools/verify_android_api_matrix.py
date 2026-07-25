#!/usr/bin/env python3
"""Keep commercial emulator coverage aligned with Android minSdk and targetSdk."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


class MatrixFailure(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gradle", type=Path, default=Path("app/build.gradle"))
    parser.add_argument(
        "--release-workflow",
        type=Path,
        default=Path(".github/workflows/android_release.yml"),
    )
    parser.add_argument(
        "--emulator-workflow",
        type=Path,
        default=Path(".github/workflows/android_emulator_tests.yml"),
    )
    parser.add_argument("--summary", type=Path, required=True)
    return parser.parse_args()


def read(path: Path) -> str:
    if not path.is_file():
        raise MatrixFailure(f"Required API matrix source is missing: {path}")
    return path.read_text(encoding="utf-8")


def unique_sdk(text: str, key: str, path: Path) -> int:
    matches = re.findall(rf"(?m)^\s*{re.escape(key)}\s+(\d+)\s*$", text)
    if len(matches) != 1:
        raise MatrixFailure(f"{path} must declare exactly one {key}, found {matches}.")
    return int(matches[0])


def numeric_api_levels(text: str) -> list[int]:
    return [
        int(value)
        for value in re.findall(r"(?m)^\s*api-level:\s*([0-9]+)\s*$", text)
    ]


def matrix_api_levels(text: str, path: Path) -> list[int]:
    match = re.search(r"(?m)^\s*api-level:\s*\[([^\]]+)\]\s*$", text)
    if match is None:
        raise MatrixFailure(f"{path} must declare an inline api-level matrix.")
    values = []
    for token in match.group(1).split(","):
        normalized = token.strip().strip("'\"")
        if not normalized.isdigit():
            raise MatrixFailure(f"{path} has a non-numeric API matrix entry: {token}")
        values.append(int(normalized))
    return values


def release_smoke_levels(text: str) -> list[int]:
    return [
        int(value)
        for value in re.findall(r"tools/run_release_smoke\.sh\s+([0-9]+)", text)
    ]


def write_summary(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    try:
        gradle_text = read(args.gradle)
        release_text = read(args.release_workflow)
        emulator_text = read(args.emulator_workflow)

        min_sdk = unique_sdk(gradle_text, "minSdk", args.gradle)
        target_sdk = unique_sdk(gradle_text, "targetSdk", args.gradle)
        if min_sdk > target_sdk:
            raise MatrixFailure(f"minSdk {min_sdk} cannot exceed targetSdk {target_sdk}.")
        expected = [min_sdk, target_sdk]
        if len(set(expected)) != 2:
            raise MatrixFailure("Commercial API matrix requires distinct minSdk and targetSdk.")

        release_levels = numeric_api_levels(release_text)
        emulator_levels = matrix_api_levels(emulator_text, args.emulator_workflow)
        smoke_levels = release_smoke_levels(release_text)

        for label, actual in (
            ("release workflow emulator jobs", release_levels),
            ("release workflow smoke commands", smoke_levels),
            ("pull-request emulator matrix", emulator_levels),
        ):
            if actual != expected:
                raise MatrixFailure(f"{label} must be exactly {expected}, got {actual}.")

        summary = {
            "schemaVersion": 1,
            "approved": True,
            "minSdk": min_sdk,
            "targetSdk": target_sdk,
            "expectedApiLevels": expected,
            "releaseWorkflowApiLevels": release_levels,
            "releaseSmokeApiLevels": smoke_levels,
            "pullRequestApiLevels": emulator_levels,
        }
        write_summary(args.summary, summary)
        print(f"Android API matrix approved: minSdk={min_sdk}, targetSdk={target_sdk}.")
        return 0
    except (OSError, MatrixFailure) as error:
        write_summary(
            args.summary,
            {"schemaVersion": 1, "approved": False, "failure": str(error)},
        )
        print(f"Android API matrix verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Create a deterministic, reviewable Detekt advisory debt inventory."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

from verify_detekt_policy import (
    DETEKT_VERSION,
    POLICY_ID,
    DetektPolicyFailure,
    finding_rows,
    parse_sarif,
)

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sarif", required=True, type=Path)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def build_baseline(sarif: Path, source_commit: str) -> dict[str, object]:
    if not COMMIT_PATTERN.fullmatch(source_commit):
        raise DetektPolicyFailure(
            "source commit must be a lowercase 40-character Git SHA"
        )
    findings, _ = parse_sarif(sarif)
    if not findings:
        raise DetektPolicyFailure("refusing to create an empty advisory baseline")
    return {
        "schemaVersion": 1,
        "policyId": POLICY_ID,
        "detektVersion": DETEKT_VERSION,
        "sourceCommit": source_commit,
        "totalFindings": sum(findings.values()),
        "fingerprints": finding_rows(findings),
    }


def main() -> int:
    args = parse_args()
    try:
        baseline = build_baseline(args.sarif, args.source_commit)
    except DetektPolicyFailure as error:
        print(f"Detekt baseline generation failed: {error}", file=sys.stderr)
        return 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(baseline, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    print(
        f"Wrote {baseline['totalFindings']} advisory findings to {args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

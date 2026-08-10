#!/usr/bin/env python3
"""Validate Stage 14 Android Lint XML reports without a baseline."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Iterable

ALL_VARIANTS = ("debug", "staging", "releaseSmoke", "release")
EVIDENCE_SEVERITIES = (
    "Fatal",
    "Error",
    "Warning",
    "Information",
    "Hint",
    "Ignore",
)
ALLOWED_SEVERITIES = set(EVIDENCE_SEVERITIES)
BLOCKER_SEVERITIES = {"Fatal", "Error"}
REPORT_NAME = re.compile(r"^lint-results-(debug|staging|releaseSmoke|release)\.xml$")


class LintFailure(ValueError):
    """Raised when lint evidence is missing, malformed or contains blockers."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", action="append", required=True, type=Path)
    parser.add_argument(
        "--required-variant",
        action="append",
        choices=ALL_VARIANTS,
        dest="required_variants",
    )
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def parse_report(path: Path) -> tuple[str, dict[str, object]]:
    match = REPORT_NAME.fullmatch(path.name)
    if match is None:
        raise LintFailure(
            f"report filename must identify a Stage 14 variant: {path.name}"
        )
    variant = match.group(1)
    try:
        raw = path.read_bytes()
        root = ET.fromstring(raw)
    except (OSError, ET.ParseError) as error:
        raise LintFailure(f"cannot read Android Lint report {path}: {error}") from error
    if root.tag != "issues":
        raise LintFailure(f"unexpected Android Lint root in {path}: {root.tag!r}")

    counts: Counter[str] = Counter()
    blockers: list[dict[str, object]] = []
    for index, issue in enumerate(root.findall("issue")):
        severity = issue.get("severity")
        issue_id = issue.get("id")
        message = issue.get("message")
        if severity not in ALLOWED_SEVERITIES:
            raise LintFailure(
                f"{path} issue {index} has unsupported severity {severity!r}"
            )
        if not issue_id or not message:
            raise LintFailure(f"{path} issue {index} has no id or message")
        counts[severity] += 1
        if severity in BLOCKER_SEVERITIES:
            location = issue.find("location")
            blockers.append(
                {
                    "id": issue_id,
                    "severity": severity,
                    "message": message,
                    "file": location.get("file") if location is not None else None,
                    "line": (
                        int(location.get("line"))
                        if location is not None
                        and (location.get("line") or "").isdigit()
                        else None
                    ),
                }
            )

    return variant, {
        "variant": variant,
        "report": str(path),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "counts": {
            severity: counts[severity]
            for severity in EVIDENCE_SEVERITIES
        },
        "blockers": blockers,
    }


def validate_reports(
    paths: Iterable[Path],
    required_variants: Iterable[str] = ALL_VARIANTS,
) -> dict[str, object]:
    required = tuple(required_variants)
    if not required or len(set(required)) != len(required):
        raise LintFailure("required variants must be non-empty and unique")

    reports: dict[str, dict[str, object]] = {}
    for path in paths:
        variant, report = parse_report(path)
        if variant in reports:
            raise LintFailure(f"duplicate Android Lint report for {variant}")
        reports[variant] = report

    missing = sorted(set(required) - reports.keys())
    unexpected = sorted(reports.keys() - set(required))
    if missing or unexpected:
        details = []
        if missing:
            details.append("missing " + ", ".join(missing))
        if unexpected:
            details.append("unexpected " + ", ".join(unexpected))
        raise LintFailure("Android Lint report set is invalid: " + "; ".join(details))

    ordered = [reports[variant] for variant in required]
    totals = {
        severity: sum(report["counts"][severity] for report in ordered)
        for severity in EVIDENCE_SEVERITIES
    }
    blockers = [
        {"variant": report["variant"], **blocker}
        for report in ordered
        for blocker in report["blockers"]
    ]
    return {
        "schemaVersion": 1,
        "passed": not blockers,
        "baselineApplied": False,
        "requiredVariants": list(required),
        "thresholds": {"fatal": 0, "error": 0},
        "totals": totals,
        "reports": ordered,
        "blockers": blockers,
    }


def write_summary(path: Path, summary: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    required = args.required_variants or list(ALL_VARIANTS)
    try:
        summary = validate_reports(args.report, required)
    except LintFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "passed": False,
                "baselineApplied": False,
                "requiredVariants": required,
                "failure": str(error),
            },
        )
        print(f"Android Lint evidence failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    if not summary["passed"]:
        print(
            "Android Lint blocker gate failed: "
            f"{summary['totals']['Fatal']} Fatal, "
            f"{summary['totals']['Error']} Error.",
            file=sys.stderr,
        )
        return 1

    print(
        "Android Lint blocker gate passed: "
        f"{len(summary['reports'])} variants, "
        f"{summary['totals']['Warning']} warnings retained as evidence."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

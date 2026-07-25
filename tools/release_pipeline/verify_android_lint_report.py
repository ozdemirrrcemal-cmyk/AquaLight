#!/usr/bin/env python3
"""Verify an Android Lint XML/HTML report as a fail-closed commercial gate."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

BLOCKING_SEVERITIES = {"Fatal", "Error"}
KNOWN_NON_BLOCKING_SEVERITIES = {"Warning", "Information", "Ignore"}
MAX_REPORTED_ISSUES = 100


class LintReportFailure(RuntimeError):
    """Raised when lint evidence is missing, malformed, or contains blockers."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--xml", type=Path, required=True)
    parser.add_argument("--html", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--variant", default="release")
    return parser.parse_args()


def require_non_empty(path: Path, label: str) -> None:
    if not path.is_file() or path.stat().st_size == 0:
        raise LintReportFailure(f"{label} is missing or empty: {path}")


def normalized_location(issue: ET.Element) -> dict[str, object] | None:
    location = issue.find("location")
    if location is None:
        return None
    result: dict[str, object] = {"file": location.get("file", "")}
    for key in ("line", "column"):
        raw = location.get(key)
        if raw and raw.isdigit():
            result[key] = int(raw)
    return result


def is_blocking_severity(severity: str) -> bool:
    if severity in BLOCKING_SEVERITIES:
        return True
    if severity in KNOWN_NON_BLOCKING_SEVERITIES:
        return False
    return True


def verify(xml_path: Path, html_path: Path, variant: str) -> dict[str, object]:
    require_non_empty(xml_path, "Android Lint XML report")
    require_non_empty(html_path, "Android Lint HTML report")

    try:
        root = ET.parse(xml_path).getroot()
    except (ET.ParseError, OSError) as error:
        raise LintReportFailure(f"Android Lint XML could not be parsed: {error}") from error

    if root.tag != "issues":
        raise LintReportFailure(f"Expected <issues> root, found <{root.tag}>.")

    severity_counts: Counter[str] = Counter()
    issue_id_counts: Counter[str] = Counter()
    blocking_issues: list[dict[str, object]] = []
    all_issue_count = 0

    for issue in root.findall("issue"):
        all_issue_count += 1
        severity = issue.get("severity", "Unknown").strip() or "Unknown"
        issue_id = issue.get("id", "Unknown").strip() or "Unknown"
        severity_counts[severity] += 1
        issue_id_counts[issue_id] += 1

        if is_blocking_severity(severity):
            if len(blocking_issues) < MAX_REPORTED_ISSUES:
                blocking_issues.append(
                    {
                        "id": issue_id,
                        "severity": severity,
                        "message": issue.get("message", ""),
                        "location": normalized_location(issue),
                    }
                )

    blocking_count = sum(
        count
        for severity, count in severity_counts.items()
        if is_blocking_severity(severity)
    )
    summary: dict[str, object] = {
        "schemaVersion": 1,
        "variant": variant,
        "xmlReport": str(xml_path),
        "htmlReport": str(html_path),
        "reportGenerator": root.get("by", ""),
        "reportFormat": root.get("format", ""),
        "issueCount": all_issue_count,
        "blockingIssueCount": blocking_count,
        "warningCount": severity_counts.get("Warning", 0),
        "severityCounts": dict(sorted(severity_counts.items())),
        "issueIdCounts": dict(sorted(issue_id_counts.items())),
        "blockingIssues": blocking_issues,
        "approved": blocking_count == 0,
    }
    return summary


def main() -> int:
    args = parse_args()
    try:
        summary = verify(args.xml, args.html, args.variant)
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        args.summary.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        print(json.dumps(summary, indent=2, sort_keys=True))
        if not summary["approved"]:
            raise LintReportFailure(
                f"Android Lint contains {summary['blockingIssueCount']} blocking issue(s)."
            )
        return 0
    except LintReportFailure as error:
        print(f"Strict Android Lint verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

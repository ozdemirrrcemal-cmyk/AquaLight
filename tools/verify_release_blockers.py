#!/usr/bin/env python3
"""Validate the complete open GitHub issue inventory for a commercial release."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
REPOSITORY_PATTERN = re.compile(
    r"^[A-Za-z0-9](?:[A-Za-z0-9_.-]*[A-Za-z0-9])?/"
    r"[A-Za-z0-9_.-]+$"
)
SEVERITY_LABELS = (
    "severity:critical",
    "severity:high",
    "severity:medium",
    "severity:low",
)
RELEASE_BLOCKER_LABEL = "release:blocker"


class ReleaseBlockerFailure(ValueError):
    """Raised when the release issue inventory is malformed or blocking."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--issues", required=True, type=Path)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def require_string(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ReleaseBlockerFailure(f"{path} must be a non-empty string")
    return value.strip()


def parse_labels(value: Any, path: str) -> set[str]:
    if not isinstance(value, list):
        raise ReleaseBlockerFailure(f"{path} must be an array")
    names: list[str] = []
    for index, raw in enumerate(value):
        if not isinstance(raw, dict):
            raise ReleaseBlockerFailure(f"{path}[{index}] must be an object")
        name = require_string(raw.get("name"), f"{path}[{index}].name").lower()
        names.append(name)
    if len(names) != len(set(names)):
        raise ReleaseBlockerFailure(f"{path} contains duplicate labels")
    return set(names)


def parse_issue(
    raw: Any,
    path: str,
    repository: str,
) -> tuple[dict[str, Any], bool]:
    if not isinstance(raw, dict):
        raise ReleaseBlockerFailure(f"{path} must be an object")
    number = raw.get("number")
    if isinstance(number, bool) or not isinstance(number, int) or number <= 0:
        raise ReleaseBlockerFailure(f"{path}.number must be a positive integer")
    if raw.get("state") != "open":
        raise ReleaseBlockerFailure(f"{path}.state must be open")

    title = require_string(raw.get("title"), f"{path}.title")
    url = require_string(raw.get("html_url"), f"{path}.html_url")
    expected_url_prefix = f"https://github.com/{repository}/issues/"
    if not url.startswith(expected_url_prefix):
        raise ReleaseBlockerFailure(
            f"{path}.html_url does not belong to {repository}"
        )
    repository_url = require_string(
        raw.get("repository_url"),
        f"{path}.repository_url",
    )
    if repository_url != f"https://api.github.com/repos/{repository}":
        raise ReleaseBlockerFailure(
            f"{path}.repository_url does not belong to {repository}"
        )

    is_pull_request = "pull_request" in raw
    if is_pull_request:
        return {
            "number": number,
            "title": title,
            "url": url,
        }, True

    labels = parse_labels(raw.get("labels"), f"{path}.labels")
    severities = sorted(labels.intersection(SEVERITY_LABELS))
    severity = severities[0].removeprefix("severity:") if len(severities) == 1 else None
    return {
        "number": number,
        "title": title,
        "url": url,
        "severity": severity,
        "severityLabels": severities,
        "releaseBlocker": RELEASE_BLOCKER_LABEL in labels,
    }, False


def validate(
    raw: bytes,
    repository: str,
    commit: str,
) -> dict[str, Any]:
    if not REPOSITORY_PATTERN.fullmatch(repository):
        raise ReleaseBlockerFailure(
            "repository must use the canonical owner/name form"
        )
    if not COMMIT_PATTERN.fullmatch(commit):
        raise ReleaseBlockerFailure(
            "commit must be a lowercase 40-character Git SHA"
        )
    try:
        document = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ReleaseBlockerFailure(f"issues JSON is invalid: {error}") from error
    if not isinstance(document, list) or not document:
        raise ReleaseBlockerFailure(
            "issues JSON must be the non-empty page array emitted by gh --paginate --slurp"
        )

    issues: list[dict[str, Any]] = []
    pull_request_count = 0
    seen_numbers: set[int] = set()
    for page_index, page in enumerate(document):
        if not isinstance(page, list):
            raise ReleaseBlockerFailure(f"issues[{page_index}] must be an array")
        for issue_index, raw_issue in enumerate(page):
            path = f"issues[{page_index}][{issue_index}]"
            issue, is_pull_request = parse_issue(raw_issue, path, repository)
            number = issue["number"]
            if number in seen_numbers:
                raise ReleaseBlockerFailure(
                    f"issue or pull request number {number} appears more than once"
                )
            seen_numbers.add(number)
            if is_pull_request:
                pull_request_count += 1
            else:
                issues.append(issue)

    issues.sort(key=lambda issue: issue["number"])
    counts = {
        "openIssues": len(issues),
        "openPullRequestsExcluded": pull_request_count,
        "critical": sum(issue["severity"] == "critical" for issue in issues),
        "high": sum(issue["severity"] == "high" for issue in issues),
        "medium": sum(issue["severity"] == "medium" for issue in issues),
        "low": sum(issue["severity"] == "low" for issue in issues),
        "releaseBlocker": sum(issue["releaseBlocker"] for issue in issues),
        "untriaged": sum(not issue["severityLabels"] for issue in issues),
        "ambiguousSeverity": sum(
            len(issue["severityLabels"]) > 1 for issue in issues
        ),
    }
    violations = []
    for issue in issues:
        if not issue["severityLabels"]:
            violations.append(
                {
                    "number": issue["number"],
                    "reason": "missing-severity",
                }
            )
        elif len(issue["severityLabels"]) > 1:
            violations.append(
                {
                    "number": issue["number"],
                    "reason": "ambiguous-severity",
                }
            )
        elif issue["severity"] in {"critical", "high"}:
            violations.append(
                {
                    "number": issue["number"],
                    "reason": f"severity:{issue['severity']}",
                }
            )
        if issue["releaseBlocker"]:
            violations.append(
                {
                    "number": issue["number"],
                    "reason": RELEASE_BLOCKER_LABEL,
                }
            )

    passed = not violations
    summary = {
        "schemaVersion": 1,
        "passed": passed,
        "suite": "release-blocker-inventory",
        "repository": repository,
        "releaseCommit": commit,
        "sourceSha256": hashlib.sha256(raw).hexdigest(),
        "policy": {
            "requiredSeverityLabels": list(SEVERITY_LABELS),
            "releaseBlockerLabel": RELEASE_BLOCKER_LABEL,
            "maximumCritical": 0,
            "maximumHigh": 0,
            "maximumReleaseBlocker": 0,
            "untriagedIssuesBlockRelease": True,
        },
        "counts": counts,
        "issues": issues,
        "violations": violations,
    }
    if not passed:
        raise ReleaseBlockerFailure(
            "release blocker inventory contains blocking or untriaged issues",
            summary,
        )
    return summary


def write_summary(path: Path, summary: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    try:
        raw = args.issues.read_bytes()
        summary = validate(raw, args.repository, args.commit)
    except (OSError, ReleaseBlockerFailure) as error:
        details = (
            error.args[1]
            if isinstance(error, ReleaseBlockerFailure) and len(error.args) > 1
            else {
                "schemaVersion": 1,
                "passed": False,
                "suite": "release-blocker-inventory",
                "repository": args.repository,
                "releaseCommit": args.commit,
                "failure": str(error),
            }
        )
        write_summary(args.summary, details)
        print(f"Release blocker gate failed: {error.args[0]}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    print(
        "Release blocker gate passed: "
        f"{summary['counts']['openIssues']} open issue(s), "
        "zero critical/high/release blockers."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

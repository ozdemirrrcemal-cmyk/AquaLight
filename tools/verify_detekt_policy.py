#!/usr/bin/env python3
"""Enforce AquaLight's zero-new-debt Detekt policy."""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from typing import Any, Iterable

BASELINE_KEYS = {
    "schemaVersion",
    "policyId",
    "detektVersion",
    "sourceCommit",
    "totalFindings",
    "fingerprints",
}
FINGERPRINT_KEYS = {"ruleId", "path", "message", "count"}
POLICY_ID = "aqualight-detekt-advisory-debt"
DETEKT_VERSION = "1.23.8"
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SUPPORTED_LEVELS = {"none", "note", "warning", "error"}

Fingerprint = tuple[str, str, str]


class DetektPolicyFailure(ValueError):
    """Raised when Detekt evidence cannot satisfy the commercial gate."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--blocker-sarif", required=True, type=Path)
    parser.add_argument("--advisory-sarif", required=True, type=Path)
    parser.add_argument("--commit")
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise DetektPolicyFailure(f"{path} must be an object")
    return value


def require_exact_keys(value: dict[str, Any], expected: set[str], path: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unknown = sorted(actual - expected)
        raise DetektPolicyFailure(
            f"{path} schema mismatch; missing={missing}, unknown={unknown}"
        )


def read_json(path: Path, label: str) -> tuple[dict[str, Any], str]:
    try:
        raw = path.read_bytes()
        document = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise DetektPolicyFailure(f"cannot read {label} {path}: {error}") from error
    return require_object(document, str(path)), hashlib.sha256(raw).hexdigest()


def normalize_path(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value:
        raise DetektPolicyFailure(f"{path} must be a non-empty relative path")
    normalized = PurePosixPath(value.replace("\\", "/"))
    if normalized.is_absolute() or ".." in normalized.parts:
        raise DetektPolicyFailure(f"{path} must not escape the repository")
    rendered = normalized.as_posix()
    if rendered in {"", "."}:
        raise DetektPolicyFailure(f"{path} must identify a source file")
    return rendered


def parse_sarif(path: Path) -> tuple[Counter[Fingerprint], dict[str, Any]]:
    root, digest = read_json(path, "Detekt SARIF")
    if root.get("version") != "2.1.0":
        raise DetektPolicyFailure(f"{path} must use SARIF 2.1.0")
    runs = root.get("runs")
    if not isinstance(runs, list) or len(runs) != 1:
        raise DetektPolicyFailure(f"{path} must contain exactly one Detekt run")
    run = require_object(runs[0], f"{path}.runs[0]")
    tool = require_object(run.get("tool"), f"{path}.runs[0].tool")
    driver = require_object(tool.get("driver"), f"{path}.runs[0].tool.driver")
    if driver.get("name") != "detekt":
        raise DetektPolicyFailure(f"{path} must be produced by detekt")
    results = run.get("results", [])
    if not isinstance(results, list):
        raise DetektPolicyFailure(f"{path}.runs[0].results must be an array")

    findings: Counter[Fingerprint] = Counter()
    for index, raw_result in enumerate(results):
        result_path = f"{path}.runs[0].results[{index}]"
        result = require_object(raw_result, result_path)
        rule_id = result.get("ruleId")
        if not isinstance(rule_id, str) or not rule_id.startswith("detekt."):
            raise DetektPolicyFailure(f"{result_path}.ruleId is not a Detekt rule")
        level = result.get("level", "warning")
        if level not in SUPPORTED_LEVELS:
            raise DetektPolicyFailure(f"{result_path}.level is unsupported: {level!r}")
        message = require_object(result.get("message"), f"{result_path}.message").get(
            "text"
        )
        if not isinstance(message, str) or not message.strip():
            raise DetektPolicyFailure(f"{result_path}.message.text must not be empty")
        locations = result.get("locations")
        if not isinstance(locations, list) or len(locations) != 1:
            raise DetektPolicyFailure(f"{result_path} must have exactly one location")
        location = require_object(locations[0], f"{result_path}.locations[0]")
        physical = require_object(
            location.get("physicalLocation"),
            f"{result_path}.locations[0].physicalLocation",
        )
        artifact = require_object(
            physical.get("artifactLocation"),
            f"{result_path}.locations[0].physicalLocation.artifactLocation",
        )
        source_path = normalize_path(
            artifact.get("uri"),
            f"{result_path}.locations[0].physicalLocation.artifactLocation.uri",
        )
        findings[(rule_id, source_path, message)] += 1

    return findings, {
        "path": str(path),
        "sha256": digest,
        "findingCount": sum(findings.values()),
        "fingerprintCount": len(findings),
    }


def load_baseline(path: Path) -> tuple[Counter[Fingerprint], dict[str, Any]]:
    root, digest = read_json(path, "Detekt debt baseline")
    require_exact_keys(root, BASELINE_KEYS, str(path))
    if root["schemaVersion"] != 1:
        raise DetektPolicyFailure(f"{path}.schemaVersion must be 1")
    if root["policyId"] != POLICY_ID:
        raise DetektPolicyFailure(f"{path}.policyId must be {POLICY_ID!r}")
    if root["detektVersion"] != DETEKT_VERSION:
        raise DetektPolicyFailure(
            f"{path}.detektVersion must be {DETEKT_VERSION!r}"
        )
    source_commit = root["sourceCommit"]
    if not isinstance(source_commit, str) or not COMMIT_PATTERN.fullmatch(source_commit):
        raise DetektPolicyFailure(
            f"{path}.sourceCommit must be a lowercase 40-character Git SHA"
        )
    fingerprints = root["fingerprints"]
    if not isinstance(fingerprints, list) or not fingerprints:
        raise DetektPolicyFailure(f"{path}.fingerprints must be a non-empty array")

    baseline: Counter[Fingerprint] = Counter()
    previous_sort_key: Fingerprint | None = None
    for index, raw_fingerprint in enumerate(fingerprints):
        item_path = f"{path}.fingerprints[{index}]"
        item = require_object(raw_fingerprint, item_path)
        require_exact_keys(item, FINGERPRINT_KEYS, item_path)
        rule_id = item["ruleId"]
        if not isinstance(rule_id, str) or not rule_id.startswith("detekt."):
            raise DetektPolicyFailure(f"{item_path}.ruleId is not a Detekt rule")
        source_path = normalize_path(item["path"], f"{item_path}.path")
        message = item["message"]
        if not isinstance(message, str) or not message.strip():
            raise DetektPolicyFailure(f"{item_path}.message must not be empty")
        count = item["count"]
        if isinstance(count, bool) or not isinstance(count, int) or count < 1:
            raise DetektPolicyFailure(f"{item_path}.count must be a positive integer")
        key = (rule_id, source_path, message)
        if previous_sort_key is not None and key <= previous_sort_key:
            raise DetektPolicyFailure(
                f"{path}.fingerprints must be unique and canonically sorted"
            )
        previous_sort_key = key
        baseline[key] = count

    total = root["totalFindings"]
    if isinstance(total, bool) or not isinstance(total, int) or total < 1:
        raise DetektPolicyFailure(f"{path}.totalFindings must be a positive integer")
    if total != sum(baseline.values()):
        raise DetektPolicyFailure(
            f"{path}.totalFindings does not match fingerprint counts"
        )
    return baseline, {
        "path": str(path),
        "sha256": digest,
        "sourceCommit": source_commit,
        "detektVersion": root["detektVersion"],
        "findingCount": total,
        "fingerprintCount": len(baseline),
    }


def finding_rows(
    counts: Counter[Fingerprint],
    limit: int | None = None,
) -> list[dict[str, Any]]:
    rows = [
        {
            "ruleId": key[0],
            "path": key[1],
            "message": key[2],
            "count": count,
        }
        for key, count in sorted(counts.items())
    ]
    return rows if limit is None else rows[:limit]


def resolve_commit(explicit: str | None) -> str:
    candidates: Iterable[str | None] = (
        explicit,
        os.environ.get("GITHUB_SHA"),
    )
    for candidate in candidates:
        if candidate:
            if COMMIT_PATTERN.fullmatch(candidate):
                return candidate
            raise DetektPolicyFailure(
                "commit must be a lowercase 40-character Git SHA"
            )
    try:
        commit = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError) as error:
        raise DetektPolicyFailure("cannot resolve the current Git commit") from error
    if not COMMIT_PATTERN.fullmatch(commit):
        raise DetektPolicyFailure("git rev-parse returned a noncanonical commit")
    return commit


def validate_policy(
    baseline_path: Path,
    blocker_sarif: Path,
    advisory_sarif: Path,
    commit: str,
) -> dict[str, Any]:
    if not COMMIT_PATTERN.fullmatch(commit):
        raise DetektPolicyFailure(
            "commit must be a lowercase 40-character Git SHA"
        )
    baseline, baseline_report = load_baseline(baseline_path)
    blocker, blocker_report = parse_sarif(blocker_sarif)
    advisory, advisory_report = parse_sarif(advisory_sarif)

    new_debt: Counter[Fingerprint] = Counter()
    for key, count in advisory.items():
        excess = count - baseline.get(key, 0)
        if excess > 0:
            new_debt[key] = excess
    resolved = baseline - advisory
    passed = not blocker and not new_debt

    return {
        "schemaVersion": 1,
        "policyId": POLICY_ID,
        "passed": passed,
        "commit": commit,
        "thresholds": {
            "blockerFindings": 0,
            "newAdvisoryDebt": 0,
        },
        "counts": {
            "blockerFindings": sum(blocker.values()),
            "baselineAdvisoryFindings": sum(baseline.values()),
            "remainingAdvisoryFindings": sum(advisory.values()),
            "resolvedAdvisoryFindings": sum(resolved.values()),
            "newAdvisoryDebt": sum(new_debt.values()),
        },
        "baseline": baseline_report,
        "reports": {
            "blocker": blocker_report,
            "advisory": advisory_report,
        },
        "blockerFindings": finding_rows(blocker, limit=50),
        "newAdvisoryDebt": finding_rows(new_debt, limit=50),
    }


def write_summary(path: Path, summary: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    commit = args.commit or os.environ.get("GITHUB_SHA") or ""
    try:
        commit = resolve_commit(args.commit)
        summary = validate_policy(
            args.baseline,
            args.blocker_sarif,
            args.advisory_sarif,
            commit,
        )
    except DetektPolicyFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "policyId": POLICY_ID,
                "passed": False,
                "commit": commit,
                "failure": str(error),
            },
        )
        print(f"Detekt policy failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    if not summary["passed"]:
        print(
            "Detekt policy failed: "
            f"{summary['counts']['blockerFindings']} blocker finding(s), "
            f"{summary['counts']['newAdvisoryDebt']} new advisory finding(s)",
            file=sys.stderr,
        )
        return 1
    print(
        "Detekt policy passed: "
        f"{summary['counts']['remainingAdvisoryFindings']} existing advisory "
        "finding(s), zero new debt"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

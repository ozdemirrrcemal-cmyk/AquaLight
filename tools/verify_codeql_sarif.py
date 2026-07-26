#!/usr/bin/env python3
"""Enforce AquaLight's zero critical/high CodeQL SARIF policy."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
HIGH_MINIMUM = 7.0
CRITICAL_MINIMUM = 9.0


class CodeQlFailure(ValueError):
    """Raised when CodeQL evidence cannot satisfy the commercial gate."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sarif-dir", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise CodeQlFailure(f"{path} must be an object")
    return value


def parse_security_severity(value: Any, path: str) -> float | None:
    if value is None:
        return None
    try:
        severity = float(value)
    except (TypeError, ValueError) as error:
        raise CodeQlFailure(f"{path} has invalid security-severity {value!r}") from error
    if not 0.0 <= severity <= 10.0:
        raise CodeQlFailure(f"{path} security-severity must be between 0 and 10")
    return severity


def parse_rule_component(
    raw_component: Any,
    path: str,
) -> dict[str, Any]:
    component = require_object(raw_component, path)
    raw_rules = component.get("rules", [])
    if not isinstance(raw_rules, list):
        raise CodeQlFailure(f"{path}.rules must be an array")
    rules = [
        require_object(rule, f"{path}.rules[{index}]")
        for index, rule in enumerate(raw_rules)
    ]
    rules_by_id: dict[str, dict[str, Any]] = {}
    for rule_index, rule in enumerate(rules):
        rule_id = rule.get("id")
        if not isinstance(rule_id, str) or not rule_id:
            raise CodeQlFailure(f"{path}.rules[{rule_index}] has no id")
        if rule_id in rules_by_id:
            raise CodeQlFailure(f"{path} contains duplicate rule {rule_id}")
        rules_by_id[rule_id] = rule
    return {
        "path": path,
        "raw": component,
        "rules": rules,
        "rulesById": rules_by_id,
    }


def resolve_rule_component(
    rule_reference: dict[str, Any],
    driver: dict[str, Any],
    extensions: list[dict[str, Any]],
    path: str,
) -> dict[str, Any]:
    raw_tool_component = rule_reference.get("toolComponent")
    if raw_tool_component is None:
        return driver
    tool_component = require_object(
        raw_tool_component,
        f"{path}.rule.toolComponent",
    )
    raw_index = tool_component.get("index")
    if raw_index is not None:
        if (
            not isinstance(raw_index, int)
            or isinstance(raw_index, bool)
            or raw_index < 0
            or raw_index >= len(extensions)
        ):
            raise CodeQlFailure(
                f"{path}.rule.toolComponent has invalid extension index {raw_index!r}"
            )
        component = extensions[raw_index]
    else:
        identifiers = {
            key: tool_component[key]
            for key in ("name", "guid")
            if isinstance(tool_component.get(key), str) and tool_component[key]
        }
        if not identifiers:
            raise CodeQlFailure(
                f"{path}.rule.toolComponent has no index, name or guid"
            )
        candidates = [
            component
            for component in [driver, *extensions]
            if all(
                component["raw"].get(key) == value
                for key, value in identifiers.items()
            )
        ]
        if len(candidates) != 1:
            raise CodeQlFailure(
                f"{path}.rule.toolComponent does not identify exactly one component"
            )
        component = candidates[0]

    for key in ("name", "guid"):
        expected = tool_component.get(key)
        if (
            expected is not None
            and component["raw"].get(key) != expected
        ):
            raise CodeQlFailure(
                f"{path}.rule.toolComponent {key} does not match its component"
            )
    return component


def resolve_rule(
    result: dict[str, Any],
    driver: dict[str, Any],
    extensions: list[dict[str, Any]],
    path: str,
) -> tuple[str, dict[str, Any]]:
    raw_rule_reference = result.get("rule")
    if raw_rule_reference is None:
        rule_reference: dict[str, Any] = {}
    else:
        rule_reference = require_object(raw_rule_reference, f"{path}.rule")

    nested_rule_id = rule_reference.get("id")
    legacy_rule_id = result.get("ruleId")
    for value, value_path in (
        (nested_rule_id, f"{path}.rule.id"),
        (legacy_rule_id, f"{path}.ruleId"),
    ):
        if value is not None and (not isinstance(value, str) or not value):
            raise CodeQlFailure(f"{value_path} must be a non-empty string")
    if (
        nested_rule_id is not None
        and legacy_rule_id is not None
        and nested_rule_id != legacy_rule_id
    ):
        raise CodeQlFailure(f"{path} has inconsistent rule identifiers")
    rule_id = (
        nested_rule_id
        if nested_rule_id is not None
        else legacy_rule_id
    )

    nested_rule_index = rule_reference.get("index")
    legacy_rule_index = result.get("ruleIndex")
    for value, value_path in (
        (nested_rule_index, f"{path}.rule.index"),
        (legacy_rule_index, f"{path}.ruleIndex"),
    ):
        if value is not None and (
            not isinstance(value, int)
            or isinstance(value, bool)
            or value < 0
        ):
            raise CodeQlFailure(f"{value_path} must be a non-negative integer")
    if (
        nested_rule_index is not None
        and legacy_rule_index is not None
        and nested_rule_index != legacy_rule_index
    ):
        raise CodeQlFailure(f"{path} has inconsistent rule indexes")
    rule_index = (
        nested_rule_index
        if nested_rule_index is not None
        else legacy_rule_index
    )

    component = resolve_rule_component(
        rule_reference,
        driver,
        extensions,
        path,
    )
    rules = component["rules"]
    if rule_index is not None:
        if rule_index >= len(rules):
            raise CodeQlFailure(
                f"{path} has invalid rule index {rule_index} "
                f"for {component['path']}"
            )
        rule = rules[rule_index]
        indexed_id = rule.get("id")
        if not isinstance(indexed_id, str) or not indexed_id:
            raise CodeQlFailure(f"{path} resolved rule has no id")
        if rule_id is not None and indexed_id != rule_id:
            raise CodeQlFailure(
                f"{path} rule id {rule_id!r} does not match "
                f"indexed rule {indexed_id!r}"
            )
        return indexed_id, rule

    if rule_id is not None:
        rule = component["rulesById"].get(rule_id)
        if rule is None:
            raise CodeQlFailure(
                f"{path} references unknown ruleId {rule_id!r} "
                f"in {component['path']}"
            )
        return rule_id, rule
    raise CodeQlFailure(f"{path} has no rule id or index")


def finding_location(result: dict[str, Any]) -> dict[str, Any]:
    locations = result.get("locations")
    if not isinstance(locations, list) or not locations:
        return {"file": None, "line": None}
    physical = (
        require_object(locations[0], "result.locations[0]")
        .get("physicalLocation")
    )
    if not isinstance(physical, dict):
        return {"file": None, "line": None}
    artifact = physical.get("artifactLocation")
    region = physical.get("region")
    return {
        "file": artifact.get("uri") if isinstance(artifact, dict) else None,
        "line": region.get("startLine") if isinstance(region, dict) else None,
    }


def parse_sarif(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
        document = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise CodeQlFailure(f"cannot read CodeQL SARIF {path}: {error}") from error
    root = require_object(document, str(path))
    if root.get("version") != "2.1.0":
        raise CodeQlFailure(f"{path} must use SARIF 2.1.0")
    runs = root.get("runs")
    if not isinstance(runs, list) or not runs:
        raise CodeQlFailure(f"{path} must contain at least one SARIF run")

    findings: list[dict[str, Any]] = []
    run_summaries: list[dict[str, Any]] = []
    for run_index, raw_run in enumerate(runs):
        run_path = f"{path}.runs[{run_index}]"
        run = require_object(raw_run, run_path)
        tool = require_object(run.get("tool"), f"{run_path}.tool")
        driver = parse_rule_component(
            tool.get("driver"),
            f"{run_path}.tool.driver",
        )
        raw_extensions = tool.get("extensions", [])
        if not isinstance(raw_extensions, list):
            raise CodeQlFailure(f"{run_path}.tool.extensions must be an array")
        extensions = [
            parse_rule_component(
                extension,
                f"{run_path}.tool.extensions[{index}]",
            )
            for index, extension in enumerate(raw_extensions)
        ]

        raw_results = run.get("results", [])
        if not isinstance(raw_results, list):
            raise CodeQlFailure(f"{run_path}.results must be an array")
        security_results = 0
        for result_index, raw_result in enumerate(raw_results):
            result_path = f"{run_path}.results[{result_index}]"
            result = require_object(raw_result, result_path)
            rule_id, rule = resolve_rule(
                result,
                driver,
                extensions,
                result_path,
            )
            result_properties = result.get("properties")
            rule_properties = rule.get("properties")
            inline_severity = (
                result_properties.get("security-severity")
                if isinstance(result_properties, dict)
                else None
            )
            rule_severity = (
                rule_properties.get("security-severity")
                if isinstance(rule_properties, dict)
                else None
            )
            severity = parse_security_severity(
                inline_severity if inline_severity is not None else rule_severity,
                result_path,
            )
            if severity is None:
                continue
            security_results += 1
            classification = (
                "critical"
                if severity >= CRITICAL_MINIMUM
                else "high"
                if severity >= HIGH_MINIMUM
                else "below-high"
            )
            message = result.get("message")
            message_text = (
                message.get("text")
                if isinstance(message, dict)
                and isinstance(message.get("text"), str)
                else ""
            )
            findings.append(
                {
                    "ruleId": rule_id,
                    "securitySeverity": severity,
                    "classification": classification,
                    "message": message_text,
                    **finding_location(result),
                }
            )

        run_summaries.append(
            {
                "tool": driver["raw"].get("name"),
                "ruleCount": len(driver["rules"])
                + sum(len(extension["rules"]) for extension in extensions),
                "resultCount": len(raw_results),
                "securityResultCount": security_results,
            }
        )

    return {
        "path": str(path),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "runs": run_summaries,
        "findings": findings,
    }


def validate_sarif_directory(directory: Path, commit: str) -> dict[str, Any]:
    if not COMMIT_PATTERN.fullmatch(commit):
        raise CodeQlFailure("commit must be a lowercase 40-character Git SHA")
    if not directory.is_dir():
        raise CodeQlFailure(f"CodeQL SARIF directory is missing: {directory}")
    paths = sorted(directory.rglob("*.sarif"))
    if not paths:
        raise CodeQlFailure(f"no CodeQL SARIF files found under {directory}")

    reports = [parse_sarif(path) for path in paths]
    findings = [
        finding
        for report in reports
        for finding in report["findings"]
    ]
    counts = {
        "critical": sum(
            finding["classification"] == "critical" for finding in findings
        ),
        "high": sum(finding["classification"] == "high" for finding in findings),
        "belowHigh": sum(
            finding["classification"] == "below-high" for finding in findings
        ),
    }
    return {
        "schemaVersion": 1,
        "passed": counts["critical"] == 0 and counts["high"] == 0,
        "releaseCommit": commit,
        "thresholds": {"critical": 0, "high": 0},
        "severityBoundaries": {
            "highMinimum": HIGH_MINIMUM,
            "criticalMinimum": CRITICAL_MINIMUM,
        },
        "counts": counts,
        "reports": reports,
        "blockingFindings": [
            finding
            for finding in findings
            if finding["classification"] in {"critical", "high"}
        ],
    }


def write_summary(path: Path, summary: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    try:
        summary = validate_sarif_directory(args.sarif_dir, args.commit)
    except CodeQlFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "passed": False,
                "releaseCommit": args.commit,
                "failure": str(error),
            },
        )
        print(f"CodeQL evidence failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    if not summary["passed"]:
        print(
            "CodeQL commercial gate failed: "
            f"{summary['counts']['critical']} critical, "
            f"{summary['counts']['high']} high.",
            file=sys.stderr,
        )
        return 1
    print(
        "CodeQL commercial gate passed: "
        f"{len(summary['reports'])} SARIF files, "
        "zero critical/high findings."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

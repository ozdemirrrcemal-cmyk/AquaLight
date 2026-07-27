#!/usr/bin/env python3
"""Enforce AquaLight's commercial CodeQL SARIF policy.

Pull requests are evaluated only against CodeQL's diff-informed ranges. Push,
scheduled, workflow-dispatch and release scans remain full-repository gates.
If pull-request diff evidence is unavailable or malformed, the verifier falls
back to the full-repository policy instead of weakening the gate.
"""

from __future__ import annotations

import argparse
import ast
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
HIGH_MINIMUM = 7.0
CRITICAL_MINIMUM = 9.0
SUPPORTED_MODES = {"auto", "full", "pr-diff"}
DEFAULT_DIFF_RANGE_RELATIVE_PATH = Path("pr-diff-range/pr-diff-range.yml")


class CodeQlFailure(ValueError):
    """Raised when CodeQL evidence cannot satisfy the commercial gate."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sarif-dir", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    parser.add_argument(
        "--mode",
        choices=sorted(SUPPORTED_MODES),
        default="auto",
        help="auto uses PR diff ranges only for pull_request events; all other events are full.",
    )
    parser.add_argument(
        "--diff-ranges",
        type=Path,
        help="CodeQL pr-diff-range.yml. Defaults to RUNNER_TEMP/pr-diff-range/pr-diff-range.yml.",
    )
    parser.add_argument(
        "--workspace",
        type=Path,
        default=Path(os.environ.get("GITHUB_WORKSPACE", Path.cwd())),
        help="Repository workspace used to resolve SARIF and diff-range paths.",
    )
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
        if expected is not None and component["raw"].get(key) != expected:
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
    rule_id = nested_rule_id if nested_rule_id is not None else legacy_rule_id

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
        nested_rule_index if nested_rule_index is not None else legacy_rule_index
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
                f"{path} has invalid rule index {rule_index} for {component['path']}"
            )
        rule = rules[rule_index]
        indexed_id = rule.get("id")
        if not isinstance(indexed_id, str) or not indexed_id:
            raise CodeQlFailure(f"{path} resolved rule has no id")
        if rule_id is not None and indexed_id != rule_id:
            raise CodeQlFailure(
                f"{path} rule id {rule_id!r} does not match indexed rule {indexed_id!r}"
            )
        return indexed_id, rule

    if rule_id is not None:
        rule = component["rulesById"].get(rule_id)
        if rule is None:
            raise CodeQlFailure(
                f"{path} references unknown ruleId {rule_id!r} in {component['path']}"
            )
        return rule_id, rule
    raise CodeQlFailure(f"{path} has no rule id or index")


def normalize_path(raw_path: str, workspace: Path) -> str:
    parsed = urlparse(raw_path)
    path_text = unquote(parsed.path if parsed.scheme == "file" else raw_path)
    path_text = path_text.replace("\\", "/")
    workspace_text = workspace.resolve().as_posix().rstrip("/")
    if path_text.startswith(workspace_text + "/"):
        path_text = path_text[len(workspace_text) + 1 :]
    while path_text.startswith("./"):
        path_text = path_text[2:]
    return path_text.lstrip("/")


def parse_region(raw_region: Any, path: str) -> tuple[int | None, int | None]:
    if raw_region is None:
        return None, None
    region = require_object(raw_region, path)
    start = region.get("startLine")
    end = region.get("endLine", start)
    for value, value_path in ((start, f"{path}.startLine"), (end, f"{path}.endLine")):
        if value is not None and (
            not isinstance(value, int)
            or isinstance(value, bool)
            or value <= 0
        ):
            raise CodeQlFailure(f"{value_path} must be a positive integer")
    if start is not None and end is not None and end < start:
        raise CodeQlFailure(f"{path}.endLine must not precede startLine")
    return start, end


def extract_physical_location(
    raw_location: Any,
    path: str,
    workspace: Path,
    kind: str,
) -> dict[str, Any] | None:
    location = require_object(raw_location, path)
    physical = location.get("physicalLocation")
    if not isinstance(physical, dict):
        return None
    artifact = physical.get("artifactLocation")
    region = physical.get("region")
    file_path: str | None = None
    if isinstance(artifact, dict):
        uri = artifact.get("uri")
        if isinstance(uri, str) and uri:
            file_path = normalize_path(uri, workspace)
    start, end = parse_region(region, f"{path}.physicalLocation.region")
    return {
        "kind": kind,
        "file": file_path,
        "startLine": start,
        "endLine": end,
    }


def finding_locations(
    result: dict[str, Any],
    path: str,
    workspace: Path,
) -> list[dict[str, Any]]:
    locations: list[dict[str, Any]] = []
    for collection_name, kind in (
        ("locations", "primary"),
        ("relatedLocations", "related"),
    ):
        raw_locations = result.get(collection_name, [])
        if raw_locations is None:
            continue
        if not isinstance(raw_locations, list):
            raise CodeQlFailure(f"{path}.{collection_name} must be an array")
        for index, raw_location in enumerate(raw_locations):
            location = extract_physical_location(
                raw_location,
                f"{path}.{collection_name}[{index}]",
                workspace,
                kind,
            )
            if location is not None:
                locations.append(location)
    return locations


def parse_sarif(path: Path, workspace: Path) -> dict[str, Any]:
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
            locations = finding_locations(result, result_path, workspace)
            primary = next(
                (location for location in locations if location["kind"] == "primary"),
                None,
            )
            findings.append(
                {
                    "ruleId": rule_id,
                    "securitySeverity": severity,
                    "classification": classification,
                    "message": message_text,
                    "file": primary["file"] if primary else None,
                    "line": primary["startLine"] if primary else None,
                    "locations": locations,
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


def parse_diff_ranges(path: Path, workspace: Path) -> dict[str, list[tuple[int, int]]]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeDecodeError) as error:
        raise CodeQlFailure(f"cannot read CodeQL diff ranges {path}: {error}") from error

    ranges: dict[str, list[tuple[int, int]]] = {}
    row_count = 0
    for line_number, raw_line in enumerate(lines, start=1):
        stripped = raw_line.strip()
        if not stripped.startswith("- ["):
            continue
        try:
            row = ast.literal_eval(stripped[2:].strip())
        except (SyntaxError, ValueError) as error:
            raise CodeQlFailure(
                f"{path}:{line_number} has malformed diff-range data"
            ) from error
        if (
            not isinstance(row, list)
            or len(row) != 3
            or not isinstance(row[0], str)
            or not isinstance(row[1], int)
            or isinstance(row[1], bool)
            or not isinstance(row[2], int)
            or isinstance(row[2], bool)
        ):
            raise CodeQlFailure(
                f"{path}:{line_number} diff range must be [path, startLine, endLine]"
            )
        raw_file, start, end = row
        if start < 0 or end < 0 or end < start:
            raise CodeQlFailure(f"{path}:{line_number} has invalid line bounds")
        row_count += 1
        if not raw_file and start == 0 and end == 0:
            continue
        normalized = normalize_path(raw_file, workspace)
        if not normalized:
            raise CodeQlFailure(f"{path}:{line_number} has an empty file path")
        ranges.setdefault(normalized, []).append((start, end))

    if row_count == 0:
        raise CodeQlFailure(f"{path} contains no diff-range rows")
    return ranges


def default_diff_range_path() -> Path | None:
    runner_temp = os.environ.get("RUNNER_TEMP")
    if not runner_temp:
        return None
    return Path(runner_temp) / DEFAULT_DIFF_RANGE_RELATIVE_PATH


def resolve_gate_mode(
    requested_mode: str,
    event_name: str,
    diff_range_path: Path | None,
    workspace: Path,
) -> tuple[str, dict[str, list[tuple[int, int]]] | None, str | None]:
    if requested_mode not in SUPPORTED_MODES:
        raise CodeQlFailure(f"unsupported CodeQL gate mode {requested_mode!r}")
    wants_pr_diff = requested_mode == "pr-diff" or (
        requested_mode == "auto" and event_name == "pull_request"
    )
    if not wants_pr_diff:
        return "full", None, None

    if diff_range_path is None:
        return "full", None, "pull-request diff-range path is unavailable"
    try:
        ranges = parse_diff_ranges(diff_range_path, workspace)
    except CodeQlFailure as error:
        return "full", None, str(error)
    return "pr-diff", ranges, None


def location_intersects_ranges(
    location: dict[str, Any],
    diff_ranges: dict[str, list[tuple[int, int]]],
) -> bool:
    file_path = location.get("file")
    start = location.get("startLine")
    end = location.get("endLine")
    if not isinstance(file_path, str) or not file_path:
        return False
    file_ranges = diff_ranges.get(file_path)
    if not file_ranges:
        return False
    if not isinstance(start, int) or not isinstance(end, int):
        return True
    return any(
        (range_start == 0 and range_end == 0)
        or (range_start <= end and start <= range_end)
        for range_start, range_end in file_ranges
    )


def finding_blocks(
    finding: dict[str, Any],
    effective_mode: str,
    diff_ranges: dict[str, list[tuple[int, int]]] | None,
) -> tuple[bool, str]:
    if finding["classification"] not in {"critical", "high"}:
        return False, "below-high"
    if effective_mode == "full":
        return True, "full-scan"
    if diff_ranges is None:
        return True, "missing-diff-evidence"
    locations = finding.get("locations", [])
    if not locations:
        return True, "unmapped-location"
    if any(location_intersects_ranges(location, diff_ranges) for location in locations):
        return True, "intersects-pr-diff"
    return False, "outside-pr-diff"


def count_findings(findings: list[dict[str, Any]]) -> dict[str, int]:
    return {
        "critical": sum(
            finding["classification"] == "critical" for finding in findings
        ),
        "high": sum(finding["classification"] == "high" for finding in findings),
        "belowHigh": sum(
            finding["classification"] == "below-high" for finding in findings
        ),
    }


def validate_sarif_directory(
    directory: Path,
    commit: str,
    *,
    requested_mode: str = "full",
    effective_mode: str = "full",
    diff_ranges: dict[str, list[tuple[int, int]]] | None = None,
    diff_fallback_reason: str | None = None,
    workspace: Path | None = None,
) -> dict[str, Any]:
    if not COMMIT_PATTERN.fullmatch(commit):
        raise CodeQlFailure("commit must be a lowercase 40-character Git SHA")
    if requested_mode not in SUPPORTED_MODES:
        raise CodeQlFailure(f"unsupported CodeQL gate mode {requested_mode!r}")
    if effective_mode not in {"full", "pr-diff"}:
        raise CodeQlFailure(f"unsupported effective CodeQL gate mode {effective_mode!r}")
    if effective_mode == "pr-diff" and diff_ranges is None:
        raise CodeQlFailure("pr-diff mode requires parsed diff ranges")
    if not directory.is_dir():
        raise CodeQlFailure(f"CodeQL SARIF directory is missing: {directory}")
    paths = sorted(directory.rglob("*.sarif"))
    if not paths:
        raise CodeQlFailure(f"no CodeQL SARIF files found under {directory}")

    resolved_workspace = (workspace or Path.cwd()).resolve()
    reports = [parse_sarif(path, resolved_workspace) for path in paths]
    findings = [
        finding
        for report in reports
        for finding in report["findings"]
    ]
    blocking_findings: list[dict[str, Any]] = []
    ignored_findings: list[dict[str, Any]] = []
    for finding in findings:
        blocks, reason = finding_blocks(finding, effective_mode, diff_ranges)
        annotated = {**finding, "gateReason": reason}
        if blocks:
            blocking_findings.append(annotated)
        elif finding["classification"] in {"critical", "high"}:
            ignored_findings.append(annotated)

    counts = count_findings(blocking_findings)
    observed_counts = count_findings(findings)
    return {
        "schemaVersion": 2,
        "passed": counts["critical"] == 0 and counts["high"] == 0,
        "releaseCommit": commit,
        "requestedMode": requested_mode,
        "effectiveMode": effective_mode,
        "diffFallbackReason": diff_fallback_reason,
        "changedFileCount": len(diff_ranges or {}),
        "changedRangeCount": sum(
            len(file_ranges) for file_ranges in (diff_ranges or {}).values()
        ),
        "thresholds": {"critical": 0, "high": 0},
        "severityBoundaries": {
            "highMinimum": HIGH_MINIMUM,
            "criticalMinimum": CRITICAL_MINIMUM,
        },
        "counts": counts,
        "observedCounts": observed_counts,
        "reports": reports,
        "blockingFindings": blocking_findings,
        "ignoredFindings": ignored_findings,
    }


def write_summary(path: Path, summary: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    workspace = args.workspace.resolve()
    event_name = os.environ.get("GITHUB_EVENT_NAME", "")
    diff_range_path = args.diff_ranges or default_diff_range_path()
    try:
        effective_mode, diff_ranges, fallback_reason = resolve_gate_mode(
            args.mode,
            event_name,
            diff_range_path,
            workspace,
        )
        summary = validate_sarif_directory(
            args.sarif_dir,
            args.commit,
            requested_mode=args.mode,
            effective_mode=effective_mode,
            diff_ranges=diff_ranges,
            diff_fallback_reason=fallback_reason,
            workspace=workspace,
        )
    except CodeQlFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 2,
                "passed": False,
                "releaseCommit": args.commit,
                "requestedMode": args.mode,
                "failure": str(error),
            },
        )
        print(f"CodeQL evidence failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    if not summary["passed"]:
        print(
            "CodeQL commercial gate failed "
            f"({summary['effectiveMode']}): "
            f"{summary['counts']['critical']} critical, "
            f"{summary['counts']['high']} high.",
            file=sys.stderr,
        )
        return 1
    print(
        "CodeQL commercial gate passed "
        f"({summary['effectiveMode']}): "
        f"{len(summary['reports'])} SARIF files, "
        "zero blocking critical/high findings."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

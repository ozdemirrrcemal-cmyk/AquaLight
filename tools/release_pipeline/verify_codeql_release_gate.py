#!/usr/bin/env python3
"""Fail-closed CodeQL release gate for an exact AquaLight release ref and commit."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

API_VERSION = "2022-11-28"
BLOCKING_SECURITY_SEVERITIES = {"critical", "high"}
MAX_PAGES = 20


class GateFailure(RuntimeError):
    """Raised when release security evidence is missing, stale, or blocking."""


@dataclass(frozen=True)
class GateConfig:
    repository: str
    commit: str
    ref: str
    category: str
    sarif_directory: Path
    analysis_output: Path
    alerts_output: Path
    summary_output: Path
    api_url: str
    token: str


def parse_args() -> GateConfig:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--category", required=True)
    parser.add_argument("--sarif-directory", type=Path, required=True)
    parser.add_argument("--analysis-output", type=Path, required=True)
    parser.add_argument("--alerts-output", type=Path, required=True)
    parser.add_argument("--summary-output", type=Path, required=True)
    parser.add_argument(
        "--api-url",
        default=os.environ.get("GITHUB_API_URL", "https://api.github.com"),
    )
    args = parser.parse_args()

    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        raise GateFailure("GITHUB_TOKEN is required for the CodeQL release gate.")
    if not _is_sha(args.commit):
        raise GateFailure(f"Expected a full 40-character commit SHA, got: {args.commit}")
    if not args.ref.startswith("refs/"):
        raise GateFailure(f"Expected a fully qualified Git ref, got: {args.ref}")
    if "/" not in args.repository:
        raise GateFailure(f"Expected owner/repository, got: {args.repository}")

    return GateConfig(
        repository=args.repository,
        commit=args.commit.lower(),
        ref=args.ref,
        category=args.category,
        sarif_directory=args.sarif_directory,
        analysis_output=args.analysis_output,
        alerts_output=args.alerts_output,
        summary_output=args.summary_output,
        api_url=args.api_url.rstrip("/"),
        token=token,
    )


def _is_sha(value: str) -> bool:
    if len(value) != 40:
        return False
    return all(character in "0123456789abcdefABCDEF" for character in value)


def _write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def _request_json(config: GateConfig, url: str) -> tuple[Any, str | None]:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {config.token}",
            "X-GitHub-Api-Version": API_VERSION,
            "User-Agent": "AquaLight-CodeQL-Release-Gate",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = json.loads(response.read().decode("utf-8"))
            return body, response.headers.get("Link")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise GateFailure(
            f"GitHub code-scanning API returned HTTP {error.code} for {url}: {detail}"
        ) from error
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        raise GateFailure(f"GitHub code-scanning API request failed for {url}: {error}") from error


def _next_link(link_header: str | None) -> str | None:
    if not link_header:
        return None
    for item in link_header.split(","):
        segments = [segment.strip() for segment in item.split(";")]
        if len(segments) < 2 or 'rel="next"' not in segments[1:]:
            continue
        target = segments[0]
        if target.startswith("<") and target.endswith(">"):
            return target[1:-1]
    return None


def _paginated_list(config: GateConfig, initial_url: str) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    url: str | None = initial_url
    for _ in range(MAX_PAGES):
        if url is None:
            return results
        payload, link_header = _request_json(config, url)
        if not isinstance(payload, list):
            raise GateFailure(f"Expected a JSON list from {url}.")
        results.extend(item for item in payload if isinstance(item, dict))
        url = _next_link(link_header)
    if url is not None:
        raise GateFailure(f"Code-scanning API pagination exceeded {MAX_PAGES} pages.")
    return results


def _load_sarif_results(directory: Path) -> tuple[list[str], int]:
    if not directory.is_dir():
        raise GateFailure(f"CodeQL SARIF directory is missing: {directory}")

    sarif_paths = sorted(
        path for path in directory.rglob("*.sarif") if path.is_file() and path.stat().st_size > 0
    )
    if not sarif_paths:
        raise GateFailure(f"No non-empty SARIF file was produced under: {directory}")

    result_count = 0
    relative_paths: list[str] = []
    for path in sarif_paths:
        try:
            document = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise GateFailure(f"Invalid SARIF JSON: {path}: {error}") from error
        runs = document.get("runs")
        if not isinstance(runs, list) or not runs:
            raise GateFailure(f"SARIF file has no runs: {path}")
        for run in runs:
            if isinstance(run, dict):
                results = run.get("results", [])
                if isinstance(results, list):
                    result_count += len(results)
        relative_paths.append(path.relative_to(directory).as_posix())
    return relative_paths, result_count


def _find_exact_analysis(
    analyses: Iterable[dict[str, Any]], config: GateConfig
) -> dict[str, Any]:
    candidates = []
    for analysis in analyses:
        tool = analysis.get("tool") or {}
        if str(tool.get("name", "")).lower() != "codeql":
            continue
        if str(analysis.get("commit_sha", "")).lower() != config.commit:
            continue
        if analysis.get("ref") != config.ref:
            continue
        if config.category not in str(analysis.get("category", "")):
            continue
        candidates.append(analysis)

    if not candidates:
        raise GateFailure(
            "GitHub has no processed CodeQL analysis for the exact release "
            f"ref/commit/category: {config.ref} {config.commit} {config.category}"
        )

    candidates.sort(key=lambda item: str(item.get("created_at", "")), reverse=True)
    analysis = candidates[0]
    if str(analysis.get("error", "")).strip():
        raise GateFailure(f"Exact CodeQL analysis reports an error: {analysis['error']}")
    return analysis


def _blocking_alerts(
    alerts: Iterable[dict[str, Any]], config: GateConfig
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    normalized: list[dict[str, Any]] = []
    inconsistent: list[dict[str, Any]] = []

    for alert in alerts:
        rule = alert.get("rule") or {}
        instance = alert.get("most_recent_instance") or {}
        severity = str(rule.get("security_severity_level") or "none").lower()
        item = {
            "number": alert.get("number"),
            "state": alert.get("state"),
            "securitySeverity": severity,
            "ruleId": rule.get("id"),
            "ruleName": rule.get("name"),
            "htmlUrl": alert.get("html_url"),
            "ref": instance.get("ref"),
            "commitSha": instance.get("commit_sha"),
            "path": (instance.get("location") or {}).get("path"),
            "startLine": (instance.get("location") or {}).get("start_line"),
        }
        normalized.append(item)
        if item["ref"] != config.ref or str(item["commitSha"] or "").lower() != config.commit:
            inconsistent.append(item)

    if inconsistent:
        raise GateFailure(
            "Code-scanning returned stale or mismatched open alerts for the release ref: "
            + json.dumps(inconsistent, ensure_ascii=False)
        )

    blocking = [
        item for item in normalized if item["securitySeverity"] in BLOCKING_SECURITY_SEVERITIES
    ]
    return normalized, blocking


def run(config: GateConfig) -> int:
    sarif_files, sarif_result_count = _load_sarif_results(config.sarif_directory)

    analyses_url = (
        f"{config.api_url}/repos/{config.repository}/code-scanning/analyses?"
        + urllib.parse.urlencode({"tool_name": "CodeQL", "per_page": 100})
    )
    analyses = _paginated_list(config, analyses_url)
    exact_analysis = _find_exact_analysis(analyses, config)

    alerts_url = (
        f"{config.api_url}/repos/{config.repository}/code-scanning/alerts?"
        + urllib.parse.urlencode(
            {"state": "open", "ref": config.ref, "per_page": 100}
        )
    )
    raw_alerts = _paginated_list(config, alerts_url)
    normalized_alerts, blocking_alerts = _blocking_alerts(raw_alerts, config)

    analysis_evidence = {
        key: exact_analysis.get(key)
        for key in (
            "id",
            "ref",
            "commit_sha",
            "analysis_key",
            "category",
            "environment",
            "error",
            "created_at",
            "results_count",
            "rules_count",
            "sarif_id",
            "url",
            "warning",
        )
    }
    _write_json(config.analysis_output, analysis_evidence)
    _write_json(config.alerts_output, normalized_alerts)

    summary = {
        "schemaVersion": 1,
        "approved": not blocking_alerts,
        "repository": config.repository,
        "releaseRef": config.ref,
        "releaseCommit": config.commit,
        "analysisCategory": config.category,
        "analysisId": exact_analysis.get("id"),
        "analysisCreatedAt": exact_analysis.get("created_at"),
        "analysisResultsCount": exact_analysis.get("results_count"),
        "analysisRulesCount": exact_analysis.get("rules_count"),
        "sarifFiles": sarif_files,
        "sarifResultCount": sarif_result_count,
        "openAlertCount": len(normalized_alerts),
        "blockingSeverities": sorted(BLOCKING_SECURITY_SEVERITIES),
        "blockingAlertCount": len(blocking_alerts),
        "blockingAlerts": blocking_alerts,
    }
    _write_json(config.summary_output, summary)

    if blocking_alerts:
        raise GateFailure(
            "CodeQL release gate blocked publication because open critical/high alerts exist: "
            + json.dumps(blocking_alerts, ensure_ascii=False)
        )

    print(
        "CodeQL release gate approved "
        f"{config.ref} at {config.commit}: "
        f"{len(normalized_alerts)} open alerts, 0 critical/high."
    )
    return 0


def main() -> int:
    config: GateConfig | None = None
    try:
        config = parse_args()
        return run(config)
    except GateFailure as error:
        print(f"CodeQL release gate failed: {error}", file=sys.stderr)
        if config is not None:
            _write_json(
                config.summary_output,
                {
                    "schemaVersion": 1,
                    "approved": False,
                    "repository": config.repository,
                    "releaseRef": config.ref,
                    "releaseCommit": config.commit,
                    "failure": str(error),
                },
            )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Validate Stage 14 force-stop and account-deletion recovery evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
PACKAGE_NAME = "com.aqua.aqualight.smoke"
SUPPORTED_API_LEVELS = (27, 36)
SCENARIOS = (
    ("started", "STARTED"),
    ("cloud-cleared", "CLOUD_CLEARED"),
    ("auth-delete-requested", "AUTH_DELETE_REQUESTED"),
    ("auth-confirmed-before-checkpoint", "AUTH_DELETE_REQUESTED"),
    ("account-deleted", "ACCOUNT_DELETED"),
)
PREPARED_MARKER = "ACCOUNT_DELETION_PROCESS_DEATH_PREPARED"
PASS_MARKER = "ACCOUNT_DELETION_PROCESS_DEATH_PASS"


class ForceStopEvidenceFailure(ValueError):
    """Raised when force-stop evidence is incomplete or unsafe."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prefix", required=True, type=Path)
    parser.add_argument("--api-level", required=True, type=int)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def read_bytes(path: Path, label: str) -> bytes:
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise ForceStopEvidenceFailure(f"cannot read {label} {path}: {error}") from error
    if not raw:
        raise ForceStopEvidenceFailure(f"{label} is empty: {path}")
    return raw


def read_text(path: Path, label: str) -> tuple[str, str]:
    raw = read_bytes(path, label)
    return raw.decode("utf-8", errors="replace"), hashlib.sha256(raw).hexdigest()


def validate_launch(path: Path, label: str) -> str:
    text, digest = read_text(path, f"{label} launch log")
    if not re.search(r"(?m)^Status:\s*ok\s*$", text):
        raise ForceStopEvidenceFailure(f"{label} Activity launch was not successful")
    if "AccountDeletionProcessDeathSmokeActivity" not in text:
        raise ForceStopEvidenceFailure(f"{label} launch did not identify the smoke Activity")
    return digest


def validate_logcat(path: Path, scenario: str) -> str:
    text, digest = read_text(path, f"{scenario} logcat")
    if f"ANR in {PACKAGE_NAME}" in text:
        raise ForceStopEvidenceFailure(f"{scenario} produced an ANR")
    if "FATAL EXCEPTION" in text and f"Process: {PACKAGE_NAME}" in text:
        raise ForceStopEvidenceFailure(
            f"{scenario} produced an AndroidRuntime crash"
        )
    return digest


def validate_scenario(prefix: Path, scenario: str, stage: str) -> dict[str, Any]:
    scenario_prefix = Path(f"{prefix}-{scenario}")
    prepare_launch = Path(f"{scenario_prefix}-prepare-start.txt")
    prepare_window = Path(f"{scenario_prefix}-prepare-window.xml")
    resume_launch = Path(f"{scenario_prefix}-resume-start.txt")
    resume_window = Path(f"{scenario_prefix}-resume-window.xml")
    logcat = Path(f"{scenario_prefix}-logcat.txt")

    prepare_text, prepare_window_sha = read_text(
        prepare_window,
        f"{scenario} prepare window",
    )
    expected_prepared = f"{PREPARED_MARKER}:{scenario}:{stage}"
    if expected_prepared not in prepare_text:
        raise ForceStopEvidenceFailure(
            f"{scenario} prepare window does not contain {expected_prepared}"
        )

    resume_text, resume_window_sha = read_text(
        resume_window,
        f"{scenario} resume window",
    )
    process_pattern = re.compile(
        rf"{re.escape(PASS_MARKER)}:{re.escape(scenario)}:"
        r"pid-(\d+)-to-(\d+)"
    )
    process_pairs = {
        (int(before), int(after))
        for before, after in process_pattern.findall(resume_text)
    }
    if len(process_pairs) != 1:
        raise ForceStopEvidenceFailure(
            f"{scenario} resume window must contain one process transition"
        )
    prepare_pid, resume_pid = process_pairs.pop()
    if prepare_pid < 1 or resume_pid < 1 or prepare_pid == resume_pid:
        raise ForceStopEvidenceFailure(
            f"{scenario} did not prove a new process after force-stop"
        )

    return {
        "id": scenario,
        "checkpointStage": stage,
        "prepareProcessId": prepare_pid,
        "resumeProcessId": resume_pid,
        "checks": {
            "prepareLaunchSucceeded": True,
            "checkpointPersisted": True,
            "forceStopCreatedNewProcess": True,
            "recoveryCompleted": True,
            "noCrashOrAnr": True,
        },
        "evidence": {
            "prepareLaunchSha256": validate_launch(
                prepare_launch,
                f"{scenario} prepare",
            ),
            "prepareWindowSha256": prepare_window_sha,
            "resumeLaunchSha256": validate_launch(
                resume_launch,
                f"{scenario} resume",
            ),
            "resumeWindowSha256": resume_window_sha,
            "logcatSha256": validate_logcat(logcat, scenario),
        },
    }


def validate(prefix: Path, api_level: int, commit: str) -> dict[str, Any]:
    if api_level not in SUPPORTED_API_LEVELS:
        raise ForceStopEvidenceFailure(
            f"api-level must be one of {SUPPORTED_API_LEVELS}, got {api_level}"
        )
    if not COMMIT_PATTERN.fullmatch(commit):
        raise ForceStopEvidenceFailure(
            "commit must be a lowercase 40-character Git SHA"
        )
    scenario_evidence = [
        validate_scenario(prefix, scenario, stage)
        for scenario, stage in SCENARIOS
    ]
    return {
        "schemaVersion": 1,
        "passed": True,
        "suite": "process-recreation-rotation-force-stop",
        "evidenceSet": "account-deletion-force-stop",
        "releaseCommit": commit,
        "apiLevel": api_level,
        "packageName": PACKAGE_NAME,
        "scenarioCount": len(scenario_evidence),
        "scenarios": scenario_evidence,
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
        summary = validate(args.prefix, args.api_level, args.commit)
    except ForceStopEvidenceFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "passed": False,
                "suite": "process-recreation-rotation-force-stop",
                "evidenceSet": "account-deletion-force-stop",
                "releaseCommit": args.commit,
                "apiLevel": args.api_level,
                "failure": str(error),
            },
        )
        print(f"Force-stop evidence failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    print(
        "Force-stop commercial gate passed: "
        f"API {args.api_level}, {summary['scenarioCount']} recovery scenarios."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

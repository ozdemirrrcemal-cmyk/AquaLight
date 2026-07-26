#!/usr/bin/env python3
"""Validate non-debuggable clean-install evidence for a Stage 14 candidate."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
PACKAGE_NAME = "com.aqua.aqualight.smoke"
SUPPORTED_API_LEVELS = (27, 37)
REQUIRED_CHECKS = {
    "nonDebuggable",
    "backupDisabled",
    "firebaseSignedOut",
    "userSessionEmpty",
    "userPrivateProjectionEmpty",
    "profileCacheEmpty",
    "knownDevicesEmpty",
    "tanksEmpty",
    "assignmentsEmpty",
    "careTasksEmpty",
    "notificationPreferencesEmpty",
    "notificationSchedulesEmpty",
    "encryptedOwnerStateEmpty",
    "tankCareIntegrityJournalEmpty",
    "recoveryMarkersEmpty",
    "ownerMediaEmpty",
}
REQUIRED_COUNTS = {
    "activeProfileCaches",
    "userPrivateProjectionFields",
    "knownDevices",
    "ignoredDevices",
    "tanks",
    "assignments",
    "careTasks",
    "notificationPreferences",
    "notificationSchedules",
    "encryptedOwnerEntries",
    "tankCareIntegrityEntries",
    "recoveryMarkers",
    "ownerMediaFiles",
}


class CleanInstallFailure(ValueError):
    """Raised when clean-install evidence is incomplete or unsafe."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--activity-evidence", required=True, type=Path)
    parser.add_argument("--install-log", required=True, type=Path)
    parser.add_argument("--launch-log", required=True, type=Path)
    parser.add_argument("--window-dump", required=True, type=Path)
    parser.add_argument("--logcat", required=True, type=Path)
    parser.add_argument("--api-level", required=True, type=int)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def read_bytes(path: Path, label: str) -> bytes:
    try:
        value = path.read_bytes()
    except OSError as error:
        raise CleanInstallFailure(f"cannot read {label} {path}: {error}") from error
    if not value:
        raise CleanInstallFailure(f"{label} is empty: {path}")
    return value


def require_exact_keys(value: dict[str, Any], expected: set[str], path: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unknown = sorted(actual - expected)
        raise CleanInstallFailure(
            f"{path} fields are invalid; missing={missing}, unknown={unknown}"
        )


def validate_activity_evidence(
    path: Path,
    api_level: int,
) -> tuple[dict[str, Any], str]:
    raw = read_bytes(path, "activity evidence")
    try:
        evidence = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise CleanInstallFailure(f"activity evidence is not UTF-8 JSON: {error}") from error
    if not isinstance(evidence, dict):
        raise CleanInstallFailure("activity evidence root must be an object")
    require_exact_keys(
        evidence,
        {
            "schemaVersion",
            "passed",
            "packageName",
            "versionName",
            "versionCode",
            "apiLevel",
            "checks",
            "counts",
        },
        "activity evidence",
    )
    if evidence["schemaVersion"] != 1 or evidence["passed"] is not True:
        raise CleanInstallFailure("activity evidence did not pass schema version 1")
    if evidence["packageName"] != PACKAGE_NAME:
        raise CleanInstallFailure("activity evidence package is not releaseSmoke")
    if evidence["apiLevel"] != api_level:
        raise CleanInstallFailure("activity evidence API level does not match the runner")
    if (
        not isinstance(evidence["versionName"], str)
        or not evidence["versionName"].endswith("-smoke")
    ):
        raise CleanInstallFailure("candidate versionName must identify releaseSmoke")
    if (
        isinstance(evidence["versionCode"], bool)
        or not isinstance(evidence["versionCode"], int)
        or evidence["versionCode"] < 1
    ):
        raise CleanInstallFailure("candidate versionCode must be a positive integer")

    checks = evidence["checks"]
    counts = evidence["counts"]
    if not isinstance(checks, dict) or not isinstance(counts, dict):
        raise CleanInstallFailure("activity checks and counts must be objects")
    require_exact_keys(checks, REQUIRED_CHECKS, "activity checks")
    require_exact_keys(counts, REQUIRED_COUNTS, "activity counts")
    failed_checks = sorted(name for name, passed in checks.items() if passed is not True)
    if failed_checks:
        raise CleanInstallFailure(
            "clean-install activity checks failed: " + ", ".join(failed_checks)
        )
    invalid_counts = sorted(
        name
        for name, count in counts.items()
        if isinstance(count, bool) or not isinstance(count, int) or count != 0
    )
    if invalid_counts:
        raise CleanInstallFailure(
            "clean-install private-state counts are non-zero: "
            + ", ".join(invalid_counts)
        )
    return evidence, hashlib.sha256(raw).hexdigest()


def validate_text_evidence(
    install_path: Path,
    launch_path: Path,
    window_path: Path,
    logcat_path: Path,
) -> dict[str, str]:
    install_raw = read_bytes(install_path, "install log")
    launch_raw = read_bytes(launch_path, "launch log")
    window_raw = read_bytes(window_path, "window dump")
    logcat_raw = read_bytes(logcat_path, "logcat")
    install = install_raw.decode("utf-8", errors="replace")
    launch = launch_raw.decode("utf-8", errors="replace")
    window = window_raw.decode("utf-8", errors="replace")
    logcat = logcat_raw.decode("utf-8", errors="replace")
    if not re.search(r"(?m)^Success\s*$", install):
        raise CleanInstallFailure("candidate was not installed successfully without replacement")
    if not re.search(r"(?m)^Status:\s*ok\s*$", launch):
        raise CleanInstallFailure("candidate first launch did not return Activity status ok")
    if "CLEAN_INSTALL_PASS" not in window:
        raise CleanInstallFailure("candidate first launch did not expose the pass marker")
    if f"ANR in {PACKAGE_NAME}" in logcat:
        raise CleanInstallFailure("candidate first launch produced an ANR")
    if "FATAL EXCEPTION" in logcat and f"Process: {PACKAGE_NAME}" in logcat:
        raise CleanInstallFailure("candidate first launch produced an AndroidRuntime crash")
    return {
        "installLogSha256": hashlib.sha256(install_raw).hexdigest(),
        "launchLogSha256": hashlib.sha256(launch_raw).hexdigest(),
        "windowDumpSha256": hashlib.sha256(window_raw).hexdigest(),
        "logcatSha256": hashlib.sha256(logcat_raw).hexdigest(),
    }


def validate(
    activity_path: Path,
    install_path: Path,
    launch_path: Path,
    window_path: Path,
    logcat_path: Path,
    api_level: int,
    commit: str,
) -> dict[str, Any]:
    if api_level not in SUPPORTED_API_LEVELS:
        raise CleanInstallFailure(
            f"api-level must be one of {SUPPORTED_API_LEVELS}, got {api_level}"
        )
    if not COMMIT_PATTERN.fullmatch(commit):
        raise CleanInstallFailure("commit must be a lowercase 40-character Git SHA")
    activity, activity_sha = validate_activity_evidence(activity_path, api_level)
    source_hashes = validate_text_evidence(
        install_path,
        launch_path,
        window_path,
        logcat_path,
    )
    return {
        "schemaVersion": 1,
        "passed": True,
        "suite": "clean-install",
        "releaseCommit": commit,
        "apiLevel": api_level,
        "packageName": PACKAGE_NAME,
        "candidate": {
            "versionName": activity["versionName"],
            "versionCode": activity["versionCode"],
            "debuggable": False,
            "backupEnabled": False,
        },
        "checks": activity["checks"],
        "counts": activity["counts"],
        "evidence": {
            "activityEvidenceSha256": activity_sha,
            **source_hashes,
        },
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
        summary = validate(
            args.activity_evidence,
            args.install_log,
            args.launch_log,
            args.window_dump,
            args.logcat,
            args.api_level,
            args.commit,
        )
    except CleanInstallFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "passed": False,
                "suite": "clean-install",
                "releaseCommit": args.commit,
                "apiLevel": args.api_level,
                "failure": str(error),
            },
        )
        print(f"Clean-install evidence failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    print(
        "Clean-install commercial gate passed: "
        f"API {args.api_level}, {summary['candidate']['versionName']} "
        f"({summary['candidate']['versionCode']})."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

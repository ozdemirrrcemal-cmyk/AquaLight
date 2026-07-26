#!/usr/bin/env python3
"""Validate the Stage 14 same-signer Android over-install evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SIGNER_PATTERN = re.compile(r"^[0-9a-f]{64}$")
PROCESS_NONCE_PATTERN = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-"
    r"[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
)
PACKAGE_NAME = "com.aqua.aqualight.smoke"
SUPPORTED_API_LEVELS = (27, 36)
BASELINE_MODE = "same-commit-lower-version-code"
REQUIRED_CHECKS = {
    "versionCodeIncreased",
    "signerUnchanged",
    "processRecreated",
    "preferenceMarkerPreserved",
    "fileMarkerPreserved",
    "appearancePreserved",
    "committedCredentialPreservedBeforeCleanup",
    "stagedCredentialPresentBeforeCleanup",
    "stagedCredentialDiscarded",
    "orphanCredentialRemoved",
    "credentialAbsentAfterCleanup",
}


class UpgradeInstallFailure(ValueError):
    """Raised when upgrade/over-install evidence is incomplete or unsafe."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline-evidence", required=True, type=Path)
    parser.add_argument("--candidate-evidence", required=True, type=Path)
    parser.add_argument("--baseline-apk", required=True, type=Path)
    parser.add_argument("--candidate-apk", required=True, type=Path)
    parser.add_argument("--baseline-install-log", required=True, type=Path)
    parser.add_argument("--candidate-install-log", required=True, type=Path)
    parser.add_argument("--baseline-launch-log", required=True, type=Path)
    parser.add_argument("--candidate-launch-log", required=True, type=Path)
    parser.add_argument("--baseline-window", required=True, type=Path)
    parser.add_argument("--candidate-window", required=True, type=Path)
    parser.add_argument("--baseline-package-dump", required=True, type=Path)
    parser.add_argument("--candidate-package-dump", required=True, type=Path)
    parser.add_argument("--baseline-logcat", required=True, type=Path)
    parser.add_argument("--candidate-logcat", required=True, type=Path)
    parser.add_argument("--api-level", required=True, type=int)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise UpgradeInstallFailure(f"{path} must be an object")
    return value


def require_exact_keys(value: dict[str, Any], expected: set[str], path: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unknown = sorted(actual - expected)
        raise UpgradeInstallFailure(
            f"{path} fields are invalid; missing={missing}, unknown={unknown}"
        )


def read_bytes(path: Path, label: str) -> bytes:
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise UpgradeInstallFailure(f"cannot read {label} {path}: {error}") from error
    if not raw:
        raise UpgradeInstallFailure(f"{label} is empty: {path}")
    return raw


def read_json(path: Path, label: str) -> tuple[dict[str, Any], str]:
    raw = read_bytes(path, label)
    try:
        document = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise UpgradeInstallFailure(f"{label} is not UTF-8 JSON: {error}") from error
    return require_object(document, label), hashlib.sha256(raw).hexdigest()


def validate_identity(value: Any, path: str) -> dict[str, Any]:
    identity = require_object(value, path)
    require_exact_keys(
        identity,
        {
            "versionName",
            "versionCode",
            "processId",
            "processNonce",
            "signerSha256",
        },
        path,
    )
    if (
        not isinstance(identity["versionName"], str)
        or not identity["versionName"].endswith("-smoke")
    ):
        raise UpgradeInstallFailure(f"{path}.versionName must identify releaseSmoke")
    for key in ("versionCode", "processId"):
        number = identity[key]
        if isinstance(number, bool) or not isinstance(number, int) or number < 1:
            raise UpgradeInstallFailure(f"{path}.{key} must be a positive integer")
    signer = identity["signerSha256"]
    if not isinstance(signer, str) or not SIGNER_PATTERN.fullmatch(signer):
        raise UpgradeInstallFailure(f"{path}.signerSha256 must be lowercase SHA-256")
    process_nonce = identity["processNonce"]
    if (
        not isinstance(process_nonce, str)
        or not PROCESS_NONCE_PATTERN.fullmatch(process_nonce)
    ):
        raise UpgradeInstallFailure(f"{path}.processNonce must be a lowercase UUIDv4")
    return identity


def validate_baseline(path: Path) -> tuple[dict[str, Any], str]:
    baseline, digest = read_json(path, "baseline activity evidence")
    require_exact_keys(
        baseline,
        {
            "schemaVersion",
            "passed",
            "phase",
            "baselineMode",
            "packageName",
            "versionName",
            "versionCode",
            "processId",
            "processNonce",
            "signerSha256",
        },
        "baseline activity evidence",
    )
    if baseline["schemaVersion"] != 1 or baseline["passed"] is not True:
        raise UpgradeInstallFailure("baseline activity evidence did not pass schema 1")
    if baseline["phase"] != "baseline-seed":
        raise UpgradeInstallFailure("baseline activity phase is invalid")
    if baseline["baselineMode"] != BASELINE_MODE:
        raise UpgradeInstallFailure("baseline mode is not the reviewed pre-release mode")
    if baseline["packageName"] != PACKAGE_NAME:
        raise UpgradeInstallFailure("baseline package is not releaseSmoke")
    identity = validate_identity(
        {
            "versionName": baseline["versionName"],
            "versionCode": baseline["versionCode"],
            "processId": baseline["processId"],
            "processNonce": baseline["processNonce"],
            "signerSha256": baseline["signerSha256"],
        },
        "baseline identity",
    )
    return identity, digest


def validate_candidate(
    path: Path,
    baseline: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any], str]:
    candidate_evidence, digest = read_json(path, "candidate activity evidence")
    require_exact_keys(
        candidate_evidence,
        {
            "schemaVersion",
            "passed",
            "phase",
            "baselineMode",
            "packageName",
            "baseline",
            "candidate",
            "checks",
            "credentialCleanup",
        },
        "candidate activity evidence",
    )
    if candidate_evidence["schemaVersion"] != 1 or candidate_evidence["passed"] is not True:
        raise UpgradeInstallFailure("candidate activity evidence did not pass schema 1")
    if candidate_evidence["phase"] != "candidate-verify":
        raise UpgradeInstallFailure("candidate activity phase is invalid")
    if candidate_evidence["baselineMode"] != BASELINE_MODE:
        raise UpgradeInstallFailure("candidate baseline mode is invalid")
    if candidate_evidence["packageName"] != PACKAGE_NAME:
        raise UpgradeInstallFailure("candidate package is not releaseSmoke")

    embedded_baseline = validate_identity(
        candidate_evidence["baseline"],
        "candidate activity evidence.baseline",
    )
    if embedded_baseline != baseline:
        raise UpgradeInstallFailure("candidate did not observe the seeded baseline identity")
    candidate = validate_identity(
        candidate_evidence["candidate"],
        "candidate activity evidence.candidate",
    )
    if candidate["versionCode"] != baseline["versionCode"] + 1:
        raise UpgradeInstallFailure("candidate versionCode must be exactly baseline + 1")
    if candidate["versionName"] != baseline["versionName"]:
        raise UpgradeInstallFailure("baseline and candidate versionName must match")
    if candidate["processNonce"] == baseline["processNonce"]:
        raise UpgradeInstallFailure("candidate did not start with a new process nonce")
    if candidate["signerSha256"] != baseline["signerSha256"]:
        raise UpgradeInstallFailure("baseline and candidate APK signer differ")

    checks = require_object(candidate_evidence["checks"], "candidate checks")
    require_exact_keys(checks, REQUIRED_CHECKS, "candidate checks")
    failed = sorted(name for name, passed in checks.items() if passed is not True)
    if failed:
        raise UpgradeInstallFailure("candidate checks failed: " + ", ".join(failed))
    cleanup = require_object(
        candidate_evidence["credentialCleanup"],
        "credential cleanup",
    )
    require_exact_keys(
        cleanup,
        {"discardedStagedCount", "removedOrphanCount"},
        "credential cleanup",
    )
    if cleanup != {"discardedStagedCount": 1, "removedOrphanCount": 1}:
        raise UpgradeInstallFailure("credential cleanup counts must both equal one")
    return candidate, cleanup, digest


def validate_install_log(path: Path, label: str) -> str:
    raw = read_bytes(path, label)
    text = raw.decode("utf-8", errors="replace")
    if not re.search(r"(?m)^Success\s*$", text):
        raise UpgradeInstallFailure(f"{label} did not report Success")
    return hashlib.sha256(raw).hexdigest()


def validate_launch_and_window(
    launch_path: Path,
    window_path: Path,
    marker: str,
    label: str,
) -> dict[str, str]:
    launch = read_bytes(launch_path, f"{label} launch log")
    window = read_bytes(window_path, f"{label} window dump")
    if not re.search(r"(?m)^Status:\s*ok\s*$", launch.decode(errors="replace")):
        raise UpgradeInstallFailure(f"{label} Activity launch did not return status ok")
    if marker not in window.decode(errors="replace"):
        raise UpgradeInstallFailure(f"{label} window did not expose {marker}")
    return {
        f"{label}LaunchSha256": hashlib.sha256(launch).hexdigest(),
        f"{label}WindowSha256": hashlib.sha256(window).hexdigest(),
    }


def validate_package_dump(path: Path, expected_version: int, label: str) -> str:
    raw = read_bytes(path, f"{label} package dump")
    text = raw.decode("utf-8", errors="replace")
    versions = {int(match) for match in re.findall(r"\bversionCode=(\d+)\b", text)}
    if expected_version not in versions:
        raise UpgradeInstallFailure(
            f"{label} package dump does not contain versionCode {expected_version}"
        )
    if PACKAGE_NAME not in text:
        raise UpgradeInstallFailure(f"{label} package dump does not identify releaseSmoke")
    return hashlib.sha256(raw).hexdigest()


def validate_logcat(path: Path, label: str) -> str:
    raw = read_bytes(path, f"{label} logcat")
    text = raw.decode("utf-8", errors="replace")
    if f"ANR in {PACKAGE_NAME}" in text:
        raise UpgradeInstallFailure(f"upgrade {label} produced an ANR")
    if "FATAL EXCEPTION" in text and f"Process: {PACKAGE_NAME}" in text:
        raise UpgradeInstallFailure(
            f"upgrade {label} produced an AndroidRuntime crash"
        )
    return hashlib.sha256(raw).hexdigest()


def validate(
    *,
    baseline_evidence: Path,
    candidate_evidence: Path,
    baseline_apk: Path,
    candidate_apk: Path,
    baseline_install_log: Path,
    candidate_install_log: Path,
    baseline_launch_log: Path,
    candidate_launch_log: Path,
    baseline_window: Path,
    candidate_window: Path,
    baseline_package_dump: Path,
    candidate_package_dump: Path,
    baseline_logcat: Path,
    candidate_logcat: Path,
    api_level: int,
    commit: str,
) -> dict[str, Any]:
    if api_level not in SUPPORTED_API_LEVELS:
        raise UpgradeInstallFailure(
            f"api-level must be one of {SUPPORTED_API_LEVELS}, got {api_level}"
        )
    if not COMMIT_PATTERN.fullmatch(commit):
        raise UpgradeInstallFailure("commit must be a lowercase 40-character Git SHA")

    baseline, baseline_evidence_sha = validate_baseline(baseline_evidence)
    candidate, cleanup, candidate_evidence_sha = validate_candidate(
        candidate_evidence,
        baseline,
    )
    baseline_apk_raw = read_bytes(baseline_apk, "baseline APK")
    candidate_apk_raw = read_bytes(candidate_apk, "candidate APK")
    baseline_apk_sha = hashlib.sha256(baseline_apk_raw).hexdigest()
    candidate_apk_sha = hashlib.sha256(candidate_apk_raw).hexdigest()
    if baseline_apk_sha == candidate_apk_sha:
        raise UpgradeInstallFailure("baseline and candidate APKs are byte-identical")

    evidence_hashes = {
        "baselineActivitySha256": baseline_evidence_sha,
        "candidateActivitySha256": candidate_evidence_sha,
        "baselineApkSha256": baseline_apk_sha,
        "candidateApkSha256": candidate_apk_sha,
        "baselineInstallSha256": validate_install_log(
            baseline_install_log,
            "baseline install log",
        ),
        "candidateInstallSha256": validate_install_log(
            candidate_install_log,
            "candidate update log",
        ),
        "baselinePackageDumpSha256": validate_package_dump(
            baseline_package_dump,
            baseline["versionCode"],
            "baseline",
        ),
        "candidatePackageDumpSha256": validate_package_dump(
            candidate_package_dump,
            candidate["versionCode"],
            "candidate",
        ),
        "baselineLogcatSha256": validate_logcat(
            baseline_logcat,
            "baseline",
        ),
        "candidateLogcatSha256": validate_logcat(
            candidate_logcat,
            "candidate",
        ),
    }
    evidence_hashes.update(
        validate_launch_and_window(
            baseline_launch_log,
            baseline_window,
            "UPGRADE_INSTALL_BASELINE_PASS",
            "baseline",
        )
    )
    evidence_hashes.update(
        validate_launch_and_window(
            candidate_launch_log,
            candidate_window,
            "UPGRADE_INSTALL_CANDIDATE_PASS",
            "candidate",
        )
    )
    return {
        "schemaVersion": 1,
        "passed": True,
        "suite": "upgrade-install",
        "releaseCommit": commit,
        "apiLevel": api_level,
        "packageName": PACKAGE_NAME,
        "baselineMode": BASELINE_MODE,
        "baseline": baseline,
        "candidate": candidate,
        "credentialCleanup": cleanup,
        "evidence": evidence_hashes,
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
            baseline_evidence=args.baseline_evidence,
            candidate_evidence=args.candidate_evidence,
            baseline_apk=args.baseline_apk,
            candidate_apk=args.candidate_apk,
            baseline_install_log=args.baseline_install_log,
            candidate_install_log=args.candidate_install_log,
            baseline_launch_log=args.baseline_launch_log,
            candidate_launch_log=args.candidate_launch_log,
            baseline_window=args.baseline_window,
            candidate_window=args.candidate_window,
            baseline_package_dump=args.baseline_package_dump,
            candidate_package_dump=args.candidate_package_dump,
            baseline_logcat=args.baseline_logcat,
            candidate_logcat=args.candidate_logcat,
            api_level=args.api_level,
            commit=args.commit,
        )
    except UpgradeInstallFailure as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "passed": False,
                "suite": "upgrade-install",
                "releaseCommit": args.commit,
                "apiLevel": args.api_level,
                "failure": str(error),
            },
        )
        print(f"Upgrade-install evidence failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    print(
        "Upgrade-install commercial gate passed: "
        f"API {args.api_level}, versionCode "
        f"{summary['baseline']['versionCode']} -> "
        f"{summary['candidate']['versionCode']}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

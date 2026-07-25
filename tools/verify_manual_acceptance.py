#!/usr/bin/env python3
"""Validate protected, release-specific Stage 14 manual acceptance evidence."""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any

COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
TAG_PATTERN = re.compile(
    r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
)
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
GITHUB_LOGIN_PATTERN = re.compile(
    r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$"
)
UTC_TIMESTAMP_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"
)
EVIDENCE_URI_PATTERN = re.compile(
    r"^(?:https://[^\s]+|urn:aqualight:manual-evidence:[A-Za-z0-9._:/-]+)$"
)
MANUAL_GATES = (
    "physical-phone-reboot",
    "physical-permission-permanent-denial",
    "physical-network-power-interruption",
    "talkback",
    "privacy-terms-approval",
    "physical-device-release-candidate",
)
ALLOWED_GATE_ROLES = {
    "qa-engineer",
    "release-manager",
    "accessibility-reviewer",
    "legal-approver",
}
ROOT_KEYS = {
    "schemaVersion",
    "releaseTag",
    "releaseCommit",
    "packageApproval",
    "gates",
}
PACKAGE_APPROVAL_KEYS = {
    "approvedBy",
    "role",
    "approvedAt",
    "source",
}
GATE_KEYS = {
    "id",
    "approved",
    "executedAt",
    "approvedBy",
    "approverRole",
    "subject",
    "evidenceUri",
    "evidenceSha256",
}


class ManualAcceptanceFailure(ValueError):
    """Raised when protected manual release evidence is incomplete."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--acceptance", required=True, type=Path)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ManualAcceptanceFailure(f"{path} must be an object")
    return value


def require_exact_keys(
    value: dict[str, Any],
    expected: set[str],
    path: str,
) -> None:
    actual = set(value)
    if actual != expected:
        raise ManualAcceptanceFailure(
            f"{path} keys mismatch; missing={sorted(expected - actual)}, "
            f"unknown={sorted(actual - expected)}"
        )


def require_string(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ManualAcceptanceFailure(f"{path} must be a non-empty string")
    if value != value.strip():
        raise ManualAcceptanceFailure(f"{path} must not contain edge whitespace")
    return value


def parse_timestamp(value: Any, path: str) -> datetime:
    raw = require_string(value, path)
    if not UTC_TIMESTAMP_PATTERN.fullmatch(raw):
        raise ManualAcceptanceFailure(
            f"{path} must use canonical UTC YYYY-MM-DDTHH:MM:SSZ"
        )
    try:
        parsed = datetime.strptime(raw, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=timezone.utc
        )
    except ValueError as error:
        raise ManualAcceptanceFailure(f"{path} is not a valid timestamp") from error
    return parsed


def require_login(value: Any, path: str) -> str:
    login = require_string(value, path)
    if not GITHUB_LOGIN_PATTERN.fullmatch(login):
        raise ManualAcceptanceFailure(f"{path} must be a canonical GitHub login")
    return login


def validate(
    raw: bytes,
    expected_tag: str,
    expected_commit: str,
) -> dict[str, Any]:
    if not TAG_PATTERN.fullmatch(expected_tag):
        raise ManualAcceptanceFailure(
            "release-tag must use canonical vMAJOR.MINOR.PATCH format"
        )
    if not COMMIT_PATTERN.fullmatch(expected_commit):
        raise ManualAcceptanceFailure(
            "commit must be a lowercase 40-character Git SHA"
        )
    try:
        document = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ManualAcceptanceFailure(
            f"manual acceptance JSON is invalid: {error}"
        ) from error

    root = require_object(document, "acceptance")
    require_exact_keys(root, ROOT_KEYS, "acceptance")
    if root["schemaVersion"] != 1:
        raise ManualAcceptanceFailure("acceptance.schemaVersion must equal 1")
    if root["releaseTag"] != expected_tag:
        raise ManualAcceptanceFailure(
            "acceptance.releaseTag does not match the controlled release tag"
        )
    if root["releaseCommit"] != expected_commit:
        raise ManualAcceptanceFailure(
            "acceptance.releaseCommit does not match the controlled release commit"
        )

    package_approval = require_object(
        root["packageApproval"],
        "acceptance.packageApproval",
    )
    require_exact_keys(
        package_approval,
        PACKAGE_APPROVAL_KEYS,
        "acceptance.packageApproval",
    )
    require_login(
        package_approval["approvedBy"],
        "acceptance.packageApproval.approvedBy",
    )
    if package_approval["role"] != "release-manager":
        raise ManualAcceptanceFailure(
            "acceptance.packageApproval.role must equal release-manager"
        )
    if package_approval["source"] != "production-release-environment-secret":
        raise ManualAcceptanceFailure(
            "acceptance.packageApproval.source must identify the protected "
            "production release environment"
        )
    package_approved_at = parse_timestamp(
        package_approval["approvedAt"],
        "acceptance.packageApproval.approvedAt",
    )
    if package_approved_at > datetime.now(timezone.utc) + timedelta(minutes=5):
        raise ManualAcceptanceFailure(
            "acceptance.packageApproval.approvedAt cannot be in the future"
        )

    raw_gates = root["gates"]
    if not isinstance(raw_gates, list):
        raise ManualAcceptanceFailure("acceptance.gates must be an array")
    gate_ids = [
        gate.get("id") if isinstance(gate, dict) else None
        for gate in raw_gates
    ]
    if gate_ids != list(MANUAL_GATES):
        raise ManualAcceptanceFailure(
            "acceptance.gates must contain the complete ordered manual gate contract"
        )

    gates: list[dict[str, Any]] = []
    for index, raw_gate in enumerate(raw_gates):
        path = f"acceptance.gates[{index}]"
        gate = require_object(raw_gate, path)
        require_exact_keys(gate, GATE_KEYS, path)
        gate_id = gate["id"]
        if gate["approved"] is not True:
            raise ManualAcceptanceFailure(f"{path}.approved must equal true")
        executed_at = parse_timestamp(gate["executedAt"], f"{path}.executedAt")
        if executed_at > package_approved_at:
            raise ManualAcceptanceFailure(
                f"{path}.executedAt is later than package approval"
            )
        require_login(gate["approvedBy"], f"{path}.approvedBy")
        role = require_string(gate["approverRole"], f"{path}.approverRole")
        if role not in ALLOWED_GATE_ROLES:
            raise ManualAcceptanceFailure(
                f"{path}.approverRole is not an approved commercial role"
            )
        if gate_id == "privacy-terms-approval" and role != "legal-approver":
            raise ManualAcceptanceFailure(
                f"{path}.approverRole must equal legal-approver"
            )
        if gate_id == "talkback" and role not in {
            "accessibility-reviewer",
            "qa-engineer",
        }:
            raise ManualAcceptanceFailure(
                f"{path}.approverRole must be accessibility-reviewer or qa-engineer"
            )
        require_string(gate["subject"], f"{path}.subject")
        evidence_uri = require_string(
            gate["evidenceUri"],
            f"{path}.evidenceUri",
        )
        if not EVIDENCE_URI_PATTERN.fullmatch(evidence_uri):
            raise ManualAcceptanceFailure(
                f"{path}.evidenceUri must be an HTTPS URL or AquaLight evidence URN"
            )
        evidence_sha = require_string(
            gate["evidenceSha256"],
            f"{path}.evidenceSha256",
        )
        if not SHA256_PATTERN.fullmatch(evidence_sha):
            raise ManualAcceptanceFailure(
                f"{path}.evidenceSha256 must be a lowercase SHA-256 digest"
            )
        gates.append(gate)

    return {
        "schemaVersion": 1,
        "passed": True,
        "suite": "manual-acceptance",
        "releaseTag": expected_tag,
        "releaseCommit": expected_commit,
        "sourceSha256": hashlib.sha256(raw).hexdigest(),
        "packageApproval": package_approval,
        "gates": gates,
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
        raw = args.acceptance.read_bytes()
        summary = validate(raw, args.release_tag, args.commit)
    except (OSError, ManualAcceptanceFailure) as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": 1,
                "passed": False,
                "suite": "manual-acceptance",
                "releaseTag": args.release_tag,
                "releaseCommit": args.commit,
                "failure": str(error),
            },
        )
        print(f"Manual acceptance gate failed: {error}", file=sys.stderr)
        return 1

    write_summary(args.summary, summary)
    print(
        "Manual acceptance gate passed: "
        f"{len(summary['gates'])} protected approvals for {args.release_tag}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

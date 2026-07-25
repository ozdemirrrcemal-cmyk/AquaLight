from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_manual_acceptance import (
    MANUAL_GATES,
    ManualAcceptanceFailure,
    validate,
)

TAG = "v1.2.3"
COMMIT = "b" * 40


def acceptance() -> dict[str, object]:
    gates = []
    for index, gate_id in enumerate(MANUAL_GATES):
        role = "qa-engineer"
        if gate_id == "privacy-terms-approval":
            role = "legal-approver"
        elif gate_id == "talkback":
            role = "accessibility-reviewer"
        gates.append(
            {
                "id": gate_id,
                "approved": True,
                "executedAt": f"2025-07-2{index}T10:00:00Z",
                "approvedBy": f"approver-{index}",
                "approverRole": role,
                "subject": f"Evidence subject {index}",
                "evidenceUri": f"urn:aqualight:manual-evidence:{gate_id}",
                "evidenceSha256": f"{index + 1:x}" * 64,
            }
        )
    return {
        "schemaVersion": 1,
        "releaseTag": TAG,
        "releaseCommit": COMMIT,
        "packageApproval": {
            "approvedBy": "release-manager",
            "role": "release-manager",
            "approvedAt": "2025-07-29T10:00:00Z",
            "source": "production-release-environment-secret",
        },
        "gates": gates,
    }


def payload(value: dict[str, object]) -> bytes:
    return json.dumps(value).encode("utf-8")


class ManualAcceptanceTest(unittest.TestCase):
    def test_complete_acceptance_passes(self) -> None:
        summary = validate(payload(acceptance()), TAG, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(6, len(summary["gates"]))

    def test_release_identity_must_match(self) -> None:
        value = acceptance()
        value["releaseTag"] = "v1.2.4"
        with self.assertRaisesRegex(ManualAcceptanceFailure, "releaseTag"):
            validate(payload(value), TAG, COMMIT)

        value = acceptance()
        value["releaseCommit"] = "c" * 40
        with self.assertRaisesRegex(ManualAcceptanceFailure, "releaseCommit"):
            validate(payload(value), TAG, COMMIT)

    def test_all_gates_must_be_present_in_order(self) -> None:
        value = acceptance()
        value["gates"] = value["gates"][:-1]

        with self.assertRaisesRegex(ManualAcceptanceFailure, "complete ordered"):
            validate(payload(value), TAG, COMMIT)

    def test_false_approval_is_rejected(self) -> None:
        value = acceptance()
        value["gates"][0]["approved"] = False

        with self.assertRaisesRegex(ManualAcceptanceFailure, "must equal true"):
            validate(payload(value), TAG, COMMIT)

    def test_legal_gate_requires_legal_approver(self) -> None:
        value = acceptance()
        value["gates"][4]["approverRole"] = "release-manager"

        with self.assertRaisesRegex(ManualAcceptanceFailure, "legal-approver"):
            validate(payload(value), TAG, COMMIT)

    def test_evidence_identity_is_fail_closed(self) -> None:
        cases = []
        missing_hash = acceptance()
        missing_hash["gates"][0]["evidenceSha256"] = "abc"
        cases.append(missing_hash)
        mutable_uri = acceptance()
        mutable_uri["gates"][0]["evidenceUri"] = "file:///tmp/evidence"
        cases.append(mutable_uri)

        for value in cases:
            with self.subTest(value=value):
                with self.assertRaises(ManualAcceptanceFailure):
                    validate(payload(value), TAG, COMMIT)

    def test_unknown_fields_are_rejected(self) -> None:
        value = acceptance()
        value["bypass"] = True

        with self.assertRaisesRegex(ManualAcceptanceFailure, "unknown"):
            validate(payload(value), TAG, COMMIT)

    def test_package_approval_must_follow_gate_execution(self) -> None:
        value = acceptance()
        value["packageApproval"]["approvedAt"] = "2025-07-20T10:00:00Z"

        with self.assertRaisesRegex(ManualAcceptanceFailure, "later"):
            validate(payload(value), TAG, COMMIT)

    def test_future_package_approval_is_rejected(self) -> None:
        value = acceptance()
        value["packageApproval"]["approvedAt"] = "2999-01-01T00:00:00Z"

        with self.assertRaisesRegex(ManualAcceptanceFailure, "future"):
            validate(payload(value), TAG, COMMIT)

    def test_input_tag_and_commit_are_fail_closed(self) -> None:
        raw = payload(acceptance())
        with self.assertRaisesRegex(ManualAcceptanceFailure, "vMAJOR"):
            validate(raw, "1.2.3", COMMIT)
        with self.assertRaisesRegex(ManualAcceptanceFailure, "40-character"):
            validate(raw, TAG, "abc")


if __name__ == "__main__":
    unittest.main()

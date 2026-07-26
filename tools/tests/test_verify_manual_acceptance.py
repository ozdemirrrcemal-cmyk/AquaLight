from __future__ import annotations

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
from release_candidate_manifest import (
    candidate_approval_identity,
    expected_files,
)

TAG = "v1.2.3"
VERSION = "1.2.3"
COMMIT = "b" * 40
RUN_ID = "12345"
CERTIFICATE = "c" * 64


def candidate_manifest() -> bytes:
    files = []
    for index, (artifact_id, path) in enumerate(expected_files(VERSION)):
        files.append(
            {
                "id": artifact_id,
                "path": path,
                "bytes": index + 1,
                "sha256": f"{index + 1:064x}",
            }
        )
    by_id = {row["id"]: row for row in files}
    return json.dumps(
        {
            "schemaVersion": 1,
            "passed": True,
            "suite": "release-candidate",
            "status": "awaiting-physical-acceptance",
            "repository": "owner/aqualight",
            "workflowRunId": RUN_ID,
            "releaseTag": TAG,
            "releaseVersion": VERSION,
            "releaseCommit": COMMIT,
            "applicationId": "com.aqua.aqualight",
            "versionName": VERSION,
            "versionCode": 42,
            "signingCertificateSha256": CERTIFICATE,
            "artifactDigests": {
                "aabSha256": by_id["release-aab"]["sha256"],
                "apkSha256": by_id["release-apk"]["sha256"],
                "mappingSha256": by_id["release-mapping"]["sha256"],
            },
            "files": files,
        },
        sort_keys=True,
    ).encode("utf-8")


def acceptance() -> dict[str, object]:
    gates = []
    for index, gate_id in enumerate(MANUAL_GATES):
        gates.append(
            {
                "id": gate_id,
                "approved": True,
                "executedAt": f"2025-07-2{index}T10:00:00Z",
                "approvedBy": f"approver-{index}",
                "approverRole": "qa-engineer",
                "subject": f"Evidence subject {index}",
                "evidenceUri": f"urn:aqualight:manual-evidence:{gate_id}",
                "evidenceSha256": f"{index + 1:x}" * 64,
            }
        )
    return {
        "schemaVersion": 2,
        "releaseTag": TAG,
        "releaseCommit": COMMIT,
        "candidateApproval": candidate_approval_identity(
            candidate_manifest()
        ),
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
        summary = validate(
            payload(acceptance()),
            TAG,
            COMMIT,
            candidate_manifest(),
        )

        self.assertTrue(summary["passed"])
        self.assertEqual(5, len(summary["gates"]))
        self.assertEqual(RUN_ID, summary["candidateApproval"]["workflowRunId"])

    def test_release_identity_must_match(self) -> None:
        value = acceptance()
        value["releaseTag"] = "v1.2.4"
        with self.assertRaisesRegex(ManualAcceptanceFailure, "releaseTag"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

        value = acceptance()
        value["releaseCommit"] = "c" * 40
        with self.assertRaisesRegex(ManualAcceptanceFailure, "releaseCommit"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

    def test_all_gates_must_be_present_in_order(self) -> None:
        value = acceptance()
        value["gates"] = value["gates"][:-1]

        with self.assertRaisesRegex(ManualAcceptanceFailure, "complete ordered"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

    def test_false_approval_is_rejected(self) -> None:
        value = acceptance()
        value["gates"][0]["approved"] = False

        with self.assertRaisesRegex(ManualAcceptanceFailure, "must equal true"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

    def test_candidate_artifact_identity_must_match(self) -> None:
        value = acceptance()
        value["candidateApproval"]["apkSha256"] = "f" * 64

        with self.assertRaisesRegex(ManualAcceptanceFailure, "candidateApproval"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

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
                    validate(
                        payload(value),
                        TAG,
                        COMMIT,
                        candidate_manifest(),
                    )

    def test_unknown_fields_are_rejected(self) -> None:
        value = acceptance()
        value["bypass"] = True

        with self.assertRaisesRegex(ManualAcceptanceFailure, "unknown"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

    def test_package_approval_must_follow_gate_execution(self) -> None:
        value = acceptance()
        value["packageApproval"]["approvedAt"] = "2025-07-20T10:00:00Z"

        with self.assertRaisesRegex(ManualAcceptanceFailure, "earlier"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

    def test_package_approval_cannot_equal_last_gate_time(self) -> None:
        value = acceptance()
        value["packageApproval"]["approvedAt"] = value["gates"][-1]["executedAt"]

        with self.assertRaisesRegex(ManualAcceptanceFailure, "earlier"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

    def test_future_package_approval_is_rejected(self) -> None:
        value = acceptance()
        value["packageApproval"]["approvedAt"] = "2999-01-01T00:00:00Z"

        with self.assertRaisesRegex(ManualAcceptanceFailure, "future"):
            validate(payload(value), TAG, COMMIT, candidate_manifest())

    def test_input_tag_and_commit_are_fail_closed(self) -> None:
        raw = payload(acceptance())
        with self.assertRaisesRegex(ManualAcceptanceFailure, "vMAJOR"):
            validate(raw, "1.2.3", COMMIT, candidate_manifest())
        with self.assertRaisesRegex(ManualAcceptanceFailure, "40-character"):
            validate(raw, TAG, "abc", candidate_manifest())

    def test_candidate_manifest_release_identity_must_match(self) -> None:
        manifest = json.loads(candidate_manifest())
        manifest["releaseCommit"] = "d" * 40

        with self.assertRaisesRegex(
            ManualAcceptanceFailure,
            "candidate manifest",
        ):
            validate(
                payload(acceptance()),
                TAG,
                COMMIT,
                json.dumps(manifest).encode("utf-8"),
            )


if __name__ == "__main__":
    unittest.main()

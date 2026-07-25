from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_stage14_policy import PolicyFailure, build_summary, validate_policy


class Stage14PolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.policy_path = (
            ROOT / "config" / "commercial" / "stage14-validation-policy.json"
        )
        cls.raw_policy = cls.policy_path.read_bytes()
        cls.policy = json.loads(cls.raw_policy)
        cls.app_gradle = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")

    def mutated_policy(self) -> dict:
        return copy.deepcopy(self.policy)

    def test_repository_policy_is_valid(self) -> None:
        validated = validate_policy(self.policy, self.app_gradle)
        summary = build_summary(validated, self.raw_policy)

        self.assertTrue(summary["passed"])
        self.assertEqual("aqualight-stage14-commercial-release", summary["policyId"])
        self.assertEqual([27, 36], summary["android"]["emulatorApiLevels"])
        self.assertEqual(64, len(summary["sourceSha256"]))
        self.assertEqual(64, len(summary["canonicalSha256"]))

    def test_unknown_field_is_rejected(self) -> None:
        policy = self.mutated_policy()
        policy["temporaryBypass"] = True

        with self.assertRaisesRegex(PolicyFailure, "unknown temporaryBypass"):
            validate_policy(policy, self.app_gradle)

    def test_nonzero_blocker_threshold_is_rejected(self) -> None:
        policy = self.mutated_policy()
        policy["blockerThresholds"]["codeql"]["high"] = 1

        with self.assertRaisesRegex(PolicyFailure, "must be 0"):
            validate_policy(policy, self.app_gradle)

    def test_missing_suite_is_rejected(self) -> None:
        policy = self.mutated_policy()
        policy["requiredSuites"].pop()

        with self.assertRaisesRegex(PolicyFailure, "complete ordered"):
            validate_policy(policy, self.app_gradle)

    def test_optional_required_artifact_cannot_be_removed(self) -> None:
        policy = self.mutated_policy()
        policy["requiredArtifacts"] = [
            artifact
            for artifact in policy["requiredArtifacts"]
            if artifact["id"] != "release-apk"
        ]

        with self.assertRaisesRegex(PolicyFailure, "complete ordered"):
            validate_policy(policy, self.app_gradle)

    def test_gradle_sdk_drift_is_rejected(self) -> None:
        drifted_gradle = self.app_gradle.replace("targetSdk 36", "targetSdk 35")

        with self.assertRaisesRegex(PolicyFailure, "does not match"):
            validate_policy(self.policy, drifted_gradle)

    def test_noncanonical_tag_pattern_is_rejected(self) -> None:
        policy = self.mutated_policy()
        policy["release"]["tagPattern"] = "^v.*$"

        with self.assertRaisesRegex(PolicyFailure, "tagPattern"):
            validate_policy(policy, self.app_gradle)


if __name__ == "__main__":
    unittest.main()

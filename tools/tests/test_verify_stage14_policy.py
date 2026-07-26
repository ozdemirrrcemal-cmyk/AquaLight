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
        cls.emulator_workflow = (
            ROOT / ".github" / "workflows" / "android_emulator_tests.yml"
        ).read_text(encoding="utf-8")
        cls.release_workflow = (
            ROOT / ".github" / "workflows" / "android_release.yml"
        ).read_text(encoding="utf-8")

    def mutated_policy(self) -> dict:
        return copy.deepcopy(self.policy)

    def test_repository_policy_is_valid(self) -> None:
        validated = self.validate(self.policy)
        summary = build_summary(validated, self.raw_policy)

        self.assertTrue(summary["passed"])
        self.assertEqual("aqualight-stage14-commercial-release", summary["policyId"])
        self.assertEqual([27, 37], summary["android"]["emulatorApiLevels"])
        self.assertEqual(64, len(summary["sourceSha256"]))
        self.assertEqual(64, len(summary["canonicalSha256"]))

    def test_unknown_field_is_rejected(self) -> None:
        policy = self.mutated_policy()
        policy["temporaryBypass"] = True

        with self.assertRaisesRegex(PolicyFailure, "unknown temporaryBypass"):
            self.validate(policy)

    def test_nonzero_blocker_threshold_is_rejected(self) -> None:
        policy = self.mutated_policy()
        policy["blockerThresholds"]["codeql"]["high"] = 1

        with self.assertRaisesRegex(PolicyFailure, "must be 0"):
            self.validate(policy)

    def test_missing_suite_is_rejected(self) -> None:
        policy = self.mutated_policy()
        policy["requiredSuites"].pop()

        with self.assertRaisesRegex(PolicyFailure, "complete ordered"):
            self.validate(policy)

    def test_optional_required_artifact_cannot_be_removed(self) -> None:
        policy = self.mutated_policy()
        policy["requiredArtifacts"] = [
            artifact
            for artifact in policy["requiredArtifacts"]
            if artifact["id"] != "release-apk"
        ]

        with self.assertRaisesRegex(PolicyFailure, "complete ordered"):
            self.validate(policy)

    def test_gradle_sdk_drift_is_rejected(self) -> None:
        drifted_gradle = self.app_gradle.replace("targetSdk 36", "targetSdk 35")

        with self.assertRaisesRegex(PolicyFailure, "does not match"):
            validate_policy(
                self.policy,
                drifted_gradle,
                self.emulator_workflow,
                self.release_workflow,
            )

    def test_noncanonical_tag_pattern_is_rejected(self) -> None:
        policy = self.mutated_policy()
        policy["release"]["tagPattern"] = "^v.*$"

        with self.assertRaisesRegex(PolicyFailure, "tagPattern"):
            self.validate(policy)

    def test_emulator_matrix_drift_is_rejected(self) -> None:
        drifted_workflow = self.emulator_workflow.replace(
            "api-level: [27, 37]",
            "api-level: [27, 35]",
        )

        with self.assertRaisesRegex(PolicyFailure, "emulator workflow API matrix"):
            validate_policy(
                self.policy,
                self.app_gradle,
                drifted_workflow,
                self.release_workflow,
            )

    def test_release_workflow_api_drift_is_rejected(self) -> None:
        drifted_workflow = self.release_workflow.replace(
            "api-level: 37",
            "api-level: 35",
        )

        with self.assertRaisesRegex(PolicyFailure, "release workflow API levels"):
            validate_policy(
                self.policy,
                self.app_gradle,
                self.emulator_workflow,
                drifted_workflow,
            )

    def test_current_system_image_install_cannot_be_removed(self) -> None:
        drifted_workflow = self.emulator_workflow.replace(
            "system-images;android-37;default;x86_64",
            "system-images;android-36;default;x86_64",
        )

        with self.assertRaisesRegex(PolicyFailure, "system image installation"):
            validate_policy(
                self.policy,
                self.app_gradle,
                drifted_workflow,
                self.release_workflow,
            )

    def test_pinned_sdk_manager_cannot_be_removed(self) -> None:
        drifted_workflow = self.emulator_workflow.replace(
            'cmdline-tools-version: "15859902"',
            'cmdline-tools-version: "12266719"',
        )

        with self.assertRaisesRegex(
            PolicyFailure,
            "command-line tools pin",
        ):
            validate_policy(
                self.policy,
                self.app_gradle,
                drifted_workflow,
                self.release_workflow,
            )

    def validate(self, policy: dict) -> dict:
        return validate_policy(
            policy,
            self.app_gradle,
            self.emulator_workflow,
            self.release_workflow,
        )


if __name__ == "__main__":
    unittest.main()

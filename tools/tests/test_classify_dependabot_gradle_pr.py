from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from classify_dependabot_gradle_pr import evaluate, load_metadata, load_policy


class DependabotGradleAutoMergePolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.policy = load_policy(
            ROOT / "config" / "dependabot" / "gradle-auto-merge-policy.json"
        )

    def metadata(
        self,
        dependency_names: list[str],
        *,
        update_type: str = "version-update:semver-patch",
        dependency_type: str = "direct:development",
        initial_pr_files: list[str] | None = None,
    ) -> dict[str, object]:
        return {
            "schemaVersion": 1,
            "pull_request": 200,
            "head_ref": "dependabot/gradle/test-update",
            "head_sha": "a" * 40,
            "base_sha": "b" * 40,
            "source_run_id": 123,
            "dependency_names": dependency_names,
            "dependency_type": dependency_type,
            "update_type": update_type,
            "package_ecosystem": "gradle",
            "maintainer_changes": False,
            "initial_pr_files": initial_pr_files or ["app/build.gradle"],
        }

    def evaluate_lines(
        self,
        metadata: dict[str, object],
        old_line: str,
        new_line: str,
        *,
        changed: bool = True,
    ) -> dict[str, object]:
        base = f"plugins {{}}\n{old_line}\n"
        head = f"plugins {{}}\n{new_line}\n"
        return evaluate(self.policy, metadata, base, head, changed)

    def test_allowlisted_test_patch_is_eligible(self) -> None:
        summary = self.evaluate_lines(
            self.metadata(["junit:junit"]),
            '    testImplementation "junit:junit:4.13.1"',
            '    testImplementation "junit:junit:4.13.2"',
        )

        self.assertTrue(summary["eligible"])
        self.assertEqual([], summary["reasons"])

    def test_grouped_android_test_minor_updates_are_eligible(self) -> None:
        metadata = self.metadata(
            ["androidx.test:core-ktx", "androidx.test.ext:junit"],
            update_type="version-update:semver-minor",
        )
        base = "\n".join(
            (
                'androidTestImplementation "androidx.test:core-ktx:1.6.1"',
                'androidTestImplementation "androidx.test.ext:junit:1.2.1"',
            )
        )
        head = "\n".join(
            (
                'androidTestImplementation "androidx.test:core-ktx:1.7.0"',
                'androidTestImplementation "androidx.test.ext:junit:1.3.0"',
            )
        )

        summary = evaluate(self.policy, metadata, base, head, True)

        self.assertTrue(summary["eligible"])
        self.assertEqual(2, len(summary["changes"]))

    def test_runtime_firebase_wrapper_and_toolchain_updates_are_manual(self) -> None:
        cases = (
            (
                "com.google.firebase:firebase-bom",
                'implementation platform("com.google.firebase:firebase-bom:34.5.0")',
                'implementation platform("com.google.firebase:firebase-bom:34.6.0")',
            ),
            (
                "gradle-wrapper",
                "distributionUrl=gradle-8.11.1-bin.zip",
                "distributionUrl=gradle-8.12.0-bin.zip",
            ),
            (
                "org.jetbrains.kotlin.android",
                "id 'org.jetbrains.kotlin.android' version '2.1.0'",
                "id 'org.jetbrains.kotlin.android' version '2.2.0'",
            ),
            (
                "com.android.application",
                "id 'com.android.application' version '8.10.0'",
                "id 'com.android.application' version '8.11.0'",
            ),
            (
                "com.google.gms.google-services",
                "id 'com.google.gms.google-services' version '4.4.4'",
                "id 'com.google.gms.google-services' version '4.5.0'",
            ),
        )
        for dependency, old_line, new_line in cases:
            with self.subTest(dependency=dependency):
                metadata = self.metadata(
                    [dependency],
                    update_type="version-update:semver-minor",
                    dependency_type="direct:production",
                )
                summary = self.evaluate_lines(metadata, old_line, new_line)
                self.assertFalse(summary["eligible"])
                self.assertTrue(
                    any("manual review" in reason for reason in summary["reasons"])
                )

    def test_major_prerelease_downgrade_and_date_versions_are_manual(self) -> None:
        cases = (
            ("4.13.2", "5.0.0"),
            ("4.13.2", "4.14.0-rc1"),
            ("4.13.2", "4.13.1"),
            ("20240303", "20250517"),
        )
        for previous, updated in cases:
            with self.subTest(previous=previous, updated=updated):
                summary = self.evaluate_lines(
                    self.metadata(["junit:junit"]),
                    f'testImplementation "junit:junit:{previous}"',
                    f'testImplementation "junit:junit:{updated}"',
                )
                self.assertFalse(summary["eligible"])

    def test_pre_one_minor_is_manual_but_patch_remains_eligible(self) -> None:
        minor = self.evaluate_lines(
            self.metadata(
                ["junit:junit"],
                update_type="version-update:semver-minor",
            ),
            'testImplementation "junit:junit:0.1.9"',
            'testImplementation "junit:junit:0.2.0"',
        )
        patch = self.evaluate_lines(
            self.metadata(["junit:junit"]),
            'testImplementation "junit:junit:0.1.8"',
            'testImplementation "junit:junit:0.1.9"',
        )

        self.assertFalse(minor["eligible"])
        self.assertTrue(
            any("pre-1.0 minor" in reason for reason in minor["reasons"])
        )
        self.assertTrue(patch["eligible"])

    def test_mixed_allowed_and_runtime_group_is_manual(self) -> None:
        metadata = self.metadata(
            ["junit:junit", "androidx.core:core-ktx"],
            update_type="version-update:semver-minor",
        )
        base = "\n".join(
            (
                'testImplementation "junit:junit:4.13.1"',
                'implementation "androidx.core:core-ktx:1.16.0"',
            )
        )
        head = "\n".join(
            (
                'testImplementation "junit:junit:4.13.2"',
                'implementation "androidx.core:core-ktx:1.17.0"',
            )
        )

        summary = evaluate(self.policy, metadata, base, head, True)

        self.assertFalse(summary["eligible"])
        self.assertIn(
            "androidx.core:core-ktx is not on the test dependency allowlist",
            summary["reasons"],
        )

    def test_scope_or_missing_trust_commit_disables_auto_merge(self) -> None:
        metadata = self.metadata(
            ["junit:junit"],
            initial_pr_files=["app/build.gradle", "settings.gradle"],
        )
        summary = self.evaluate_lines(
            metadata,
            'testImplementation "junit:junit:4.13.1"',
            'testImplementation "junit:junit:4.13.2"',
            changed=False,
        )

        self.assertFalse(summary["eligible"])
        self.assertTrue(
            any("trust refresh" in reason for reason in summary["reasons"])
        )
        self.assertTrue(
            any("initial PR scope" in reason for reason in summary["reasons"])
        )


if __name__ == "__main__":
    unittest.main()

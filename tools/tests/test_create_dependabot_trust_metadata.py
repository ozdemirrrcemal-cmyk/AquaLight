from __future__ import annotations

import argparse
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from create_dependabot_trust_metadata import create_metadata


class DependabotTrustMetadataTest(unittest.TestCase):
    def args(self, pr_files: Path, **overrides: object) -> argparse.Namespace:
        values: dict[str, object] = {
            "pull_request": 200,
            "head_ref": "dependabot/gradle/junit-4.13.2",
            "head_sha": "a" * 40,
            "base_sha": "b" * 40,
            "source_run_id": 300,
            "dependency_names": "junit:junit, androidx.test:core-ktx",
            "dependency_type": "direct:development",
            "update_type": "version-update:semver-patch",
            "package_ecosystem": "gradle",
            "maintainer_changes": "false",
            "pr_files": pr_files,
            "output": pr_files.with_name("metadata.json"),
        }
        values.update(overrides)
        return argparse.Namespace(**values)

    def test_creates_normalized_strict_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            pr_files = Path(directory) / "files.txt"
            pr_files.write_text("app/build.gradle\napp/build.gradle\n", encoding="utf-8")

            metadata = create_metadata(self.args(pr_files))

            self.assertEqual(["app/build.gradle"], metadata["initial_pr_files"])
            self.assertEqual(
                ["androidx.test:core-ktx", "junit:junit"],
                metadata["dependency_names"],
            )
            self.assertIs(False, metadata["maintainer_changes"])

    def test_unknown_maintainer_value_cannot_become_false(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            pr_files = Path(directory) / "files.txt"
            pr_files.write_text("app/build.gradle\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "exactly true or false"):
                create_metadata(
                    self.args(pr_files, maintainer_changes="unknown")
                )


if __name__ == "__main__":
    unittest.main()

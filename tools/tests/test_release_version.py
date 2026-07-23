from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.release_version import (
    FIRST_RELEASE_TAG,
    MAX_PLAY_VERSION_CODE,
    ReleaseVersionError,
    parse_release_tag,
    require_newer_version,
    require_release_sequence,
)

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "release_version.py"
WORKFLOW = ROOT / ".github" / "workflows" / "android_release.yml"


class ReleaseVersionTest(unittest.TestCase):
    def test_derives_android_values_from_first_release_tag(self) -> None:
        release = parse_release_tag(FIRST_RELEASE_TAG)

        self.assertEqual("1.0.0", release.version_name)
        self.assertEqual(1_000_000, release.version_code)

    def test_supports_highest_play_version_code(self) -> None:
        release = parse_release_tag("v2100.0.0")

        self.assertEqual(MAX_PLAY_VERSION_CODE, release.version_code)

    def test_rejects_zero_version_code(self) -> None:
        with self.assertRaisesRegex(ReleaseVersionError, "positive"):
            parse_release_tag("v0.0.0")

    def test_rejects_noncanonical_or_ambiguous_tags(self) -> None:
        invalid_tags = (
            "1.2.3",
            "v01.2.3",
            "v1.02.3",
            "v1.2.003",
            "v1.2",
            "v1.2.3-rc.1",
            "v1.1000.0",
            "v1.0.1000",
            " v1.2.3",
        )

        for tag in invalid_tags:
            with self.subTest(tag=tag):
                with self.assertRaises(ReleaseVersionError):
                    parse_release_tag(tag)

    def test_rejects_version_code_above_play_limit(self) -> None:
        with self.assertRaisesRegex(ReleaseVersionError, "above Google Play"):
            parse_release_tag("v2100.0.1")

    def test_requires_strictly_newer_release(self) -> None:
        previous = parse_release_tag("v1.2.3")

        require_newer_version(parse_release_tag("v1.2.4"), previous)
        with self.assertRaisesRegex(ReleaseVersionError, "must be newer"):
            require_newer_version(parse_release_tag("v1.2.3"), previous)
        with self.assertRaisesRegex(ReleaseVersionError, "must be newer"):
            require_newer_version(parse_release_tag("v1.2.2"), previous)

    def test_requires_v1_0_0_as_first_production_release(self) -> None:
        require_release_sequence(parse_release_tag("v1.0.0"), ())

        with self.assertRaisesRegex(
            ReleaseVersionError,
            "First production release must be v1.0.0",
        ):
            require_release_sequence(parse_release_tag("v1.0.1"), ())

    def test_release_sequence_uses_latest_production_release(self) -> None:
        previous = (
            parse_release_tag("v1.0.0"),
            parse_release_tag("v1.0.2"),
            parse_release_tag("v1.1.0"),
        )

        require_release_sequence(parse_release_tag("v1.1.1"), previous)
        with self.assertRaisesRegex(ReleaseVersionError, "must be newer"):
            require_release_sequence(parse_release_tag("v1.0.3"), previous)

    def test_cli_writes_json_and_github_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "github-output.txt"
            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "v2.4.6",
                    "--previous-tag",
                    "v2.4.4",
                    "--previous-tag",
                    "v2.4.5",
                    "--github-output",
                    str(output_path),
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )

            self.assertEqual(
                {
                    "tag": "v2.4.6",
                    "version_code": 2_004_006,
                    "version_name": "2.4.6",
                },
                json.loads(result.stdout),
            )
            self.assertEqual(
                "release_tag=v2.4.6\n"
                "version_name=2.4.6\n"
                "version_code=2004006\n",
                output_path.read_text(encoding="utf-8"),
            )

    def test_cli_rejects_a_noninitial_tag_without_release_history(self) -> None:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "v1.0.1"],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "First production release must be v1.0.0",
            result.stderr,
        )


class ReleaseWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.candidate_job, cls.production_job = cls.workflow.split(
            "\n  production-release:\n",
            maxsplit=1,
        )
        cls.candidate_job = cls.candidate_job.split(
            "\n  release-candidate:\n",
            maxsplit=1,
        )[1]

    def test_development_events_build_only_a_release_candidate(self) -> None:
        self.assertIn('branches: ["main"]', self.workflow)
        self.assertIn('- "v*.*.*"', self.workflow)
        self.assertIn("github.ref == 'refs/heads/main'", self.candidate_job)
        self.assertIn("assembleStagingReleaseSmoke", self.candidate_job)
        self.assertNotIn("assembleProductionRelease", self.candidate_job)
        self.assertNotIn("secrets.", self.candidate_job)
        self.assertNotIn("RELEASE_KEYSTORE_BASE64", self.candidate_job)

    def test_production_signing_is_tag_only_and_environment_scoped(self) -> None:
        self.assertIn(
            "startsWith(github.ref, 'refs/tags/')",
            self.production_job,
        )
        self.assertIn(
            "vars.AQL_PRODUCTION_RELEASE_ENABLED",
            self.workflow,
        )
        self.assertIn("environment: production-release", self.production_job)
        self.assertIn("RELEASE_KEYSTORE_BASE64", self.production_job)
        self.assertIn("assembleProductionRelease", self.production_job)


if __name__ == "__main__":
    unittest.main()

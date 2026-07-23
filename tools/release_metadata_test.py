#!/usr/bin/env python3

from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

from tools.release_metadata import (
    ANDROID_MAX_VERSION_CODE,
    ReleaseMetadataError,
    ReleaseVersion,
    parse_release_tag,
    render_github_output,
    validate_monotonic_release,
)


class ReleaseMetadataTest(unittest.TestCase):
    def test_derives_version_name_code_and_artifact_base(self) -> None:
        version = parse_release_tag("v1.2.3")

        self.assertEqual("1.2.3", version.version_name)
        self.assertEqual(1_002_003, version.version_code)
        self.assertEqual("AquaLight-1.2.3", version.artifact_base)

    def test_accepts_zero_major_for_pre_one_stable_versions(self) -> None:
        version = parse_release_tag("v0.12.34")

        self.assertEqual(12_034, version.version_code)

    def test_rejects_non_production_tag_shapes(self) -> None:
        invalid_tags = (
            "1.2.3",
            "v1.2",
            "v1.2.3.4",
            "v1.2.3-rc.1",
            "v01.2.3",
            "v1.02.3",
            "release-1.2.3",
            "v1.2.3 ",
        )

        for tag in invalid_tags:
            with self.subTest(tag=tag):
                with self.assertRaises(ReleaseMetadataError):
                    parse_release_tag(tag)

    def test_rejects_component_overflow(self) -> None:
        with self.assertRaises(ReleaseMetadataError):
            parse_release_tag("v1.1000.0")
        with self.assertRaises(ReleaseMetadataError):
            parse_release_tag("v1.0.1000")

    def test_rejects_android_version_code_overflow(self) -> None:
        overflowing_major = ANDROID_MAX_VERSION_CODE // 1_000_000 + 1

        with self.assertRaises(ReleaseMetadataError):
            parse_release_tag(f"v{overflowing_major}.0.0")

    def test_rejects_zero_version_code(self) -> None:
        with self.assertRaises(ReleaseMetadataError):
            parse_release_tag("v0.0.0")

    def test_requires_current_release_to_be_newer(self) -> None:
        previous = ReleaseVersion(1, 2, 3)

        with self.assertRaises(ReleaseMetadataError):
            validate_monotonic_release(ReleaseVersion(1, 2, 3), previous)
        with self.assertRaises(ReleaseMetadataError):
            validate_monotonic_release(ReleaseVersion(1, 2, 2), previous)

        validate_monotonic_release(ReleaseVersion(1, 2, 4), previous)
        validate_monotonic_release(ReleaseVersion(1, 3, 0), previous)
        validate_monotonic_release(ReleaseVersion(2, 0, 0), previous)

    def test_renders_github_output(self) -> None:
        output = render_github_output(
            ReleaseVersion(2, 4, 6),
            ReleaseVersion(2, 4, 5),
        )

        self.assertIn("release_tag=v2.4.6", output)
        self.assertIn("version_name=2.4.6", output)
        self.assertIn("version_code=2004006", output)
        self.assertIn("artifact_base=AquaLight-2.4.6", output)
        self.assertIn("previous_release_tag=v2.4.5", output)

    def test_cli_emits_machine_readable_values(self) -> None:
        script = Path(__file__).with_name("release_metadata.py")
        result = subprocess.run(
            [
                sys.executable,
                str(script),
                "--tag",
                "v3.5.8",
                "--previous-tag",
                "v3.5.7",
            ],
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("version_name=3.5.8", result.stdout)
        self.assertIn("version_code=3005008", result.stdout)

    def test_cli_fails_closed_for_non_increasing_release(self) -> None:
        script = Path(__file__).with_name("release_metadata.py")
        result = subprocess.run(
            [
                sys.executable,
                str(script),
                "--tag",
                "v3.5.7",
                "--previous-tag",
                "v3.5.7",
            ],
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertEqual(2, result.returncode)
        self.assertIn("must be newer", result.stderr)


if __name__ == "__main__":
    unittest.main()

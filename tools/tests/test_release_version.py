from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.release_version import (
    MAX_PLAY_VERSION_CODE,
    ReleaseVersionError,
    parse_release_tag,
    require_newer_version,
)

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "release_version.py"


class ReleaseVersionTest(unittest.TestCase):
    def test_derives_android_values_from_release_tag(self) -> None:
        release = parse_release_tag("v1.0.3")

        self.assertEqual("1.0.3", release.version_name)
        self.assertEqual(1_000_003, release.version_code)

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

    def test_cli_writes_json_and_github_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "github-output.txt"
            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "v2.4.6",
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


if __name__ == "__main__":
    unittest.main()

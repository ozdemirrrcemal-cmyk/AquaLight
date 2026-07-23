#!/usr/bin/env python3

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from versioning import MAX_ANDROID_VERSION_CODE, VersioningError, derive_release_version


class ReleaseVersioningTest(unittest.TestCase):
    def test_stable_tag_derives_name_and_monotonic_code(self) -> None:
        version = derive_release_version("v2.7.13", run_number=42, version_code_base=10_000)

        self.assertEqual("v2.7.13", version.tag)
        self.assertEqual("2.7.13", version.version_name)
        self.assertEqual(10_042, version.version_code)
        self.assertFalse(version.prerelease)

    def test_release_candidate_is_marked_prerelease(self) -> None:
        version = derive_release_version("v3.0.0-rc.2", run_number=9, version_code_base=500)

        self.assertEqual("3.0.0-rc.2", version.version_name)
        self.assertTrue(version.prerelease)

    def test_invalid_tag_is_rejected(self) -> None:
        invalid_tags = ["1.2.3", "v01.2.3", "v1.2", "v1.2.3-preview", "release-1.2.3"]

        for tag in invalid_tags:
            with self.subTest(tag=tag), self.assertRaises(VersioningError):
                derive_release_version(tag, run_number=1, version_code_base=0)

    def test_non_positive_run_number_is_rejected(self) -> None:
        with self.assertRaises(VersioningError):
            derive_release_version("v1.0.0", run_number=0, version_code_base=0)

    def test_android_version_code_limit_is_enforced(self) -> None:
        with self.assertRaises(VersioningError):
            derive_release_version(
                "v1.0.0",
                run_number=1,
                version_code_base=MAX_ANDROID_VERSION_CODE,
            )


if __name__ == "__main__":
    unittest.main()

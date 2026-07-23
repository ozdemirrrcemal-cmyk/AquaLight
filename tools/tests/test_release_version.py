from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.release_version import ANDROID_MAX_VERSION_CODE, parse_release_tag


class ReleaseVersionTest(unittest.TestCase):
    def test_derives_name_and_monotonic_code(self) -> None:
        first = parse_release_tag("v1.2.3")
        second = parse_release_tag("v1.2.4")
        next_minor = parse_release_tag("v1.3.0")
        next_major = parse_release_tag("v2.0.0")

        self.assertEqual("1.2.3", first.version_name)
        self.assertEqual(1_002_003, first.version_code)
        self.assertLess(first.version_code, second.version_code)
        self.assertLess(second.version_code, next_minor.version_code)
        self.assertLess(next_minor.version_code, next_major.version_code)

    def test_rejects_prerelease_and_malformed_tags(self) -> None:
        for tag in ("1.2.3", "v1.2", "v1.2.3-rc.1", "v01.2.3", "release-1.2.3"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                parse_release_tag(tag)

    def test_rejects_invalid_android_codes(self) -> None:
        with self.assertRaises(ValueError):
            parse_release_tag("v0.0.0")
        with self.assertRaises(ValueError):
            parse_release_tag("v1.1000.0")
        with self.assertRaises(ValueError):
            parse_release_tag(f"v{ANDROID_MAX_VERSION_CODE}.0.0")


if __name__ == "__main__":
    unittest.main()

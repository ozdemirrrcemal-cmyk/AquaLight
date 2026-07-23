import unittest

from tools.release_version import ci_version, parse_release_tag


class ReleaseVersionTest(unittest.TestCase):
    def test_stable_tag(self):
        version = parse_release_tag("v1.2.3")
        self.assertEqual("1.2.3", version.version_name)
        self.assertEqual(102_003_999, version.version_code)
        self.assertFalse(version.prerelease)

    def test_prerelease_order(self):
        alpha = parse_release_tag("v1.2.3-alpha.1").version_code
        beta = parse_release_tag("v1.2.3-beta.1").version_code
        rc = parse_release_tag("v1.2.3-rc.1").version_code
        stable = parse_release_tag("v1.2.3").version_code
        next_patch = parse_release_tag("v1.2.4-alpha.1").version_code
        self.assertLess(alpha, beta)
        self.assertLess(beta, rc)
        self.assertLess(rc, stable)
        self.assertLess(stable, next_patch)

    def test_invalid_tags_are_rejected(self):
        for tag in ("1.2.3", "v1.2", "v1.2.3-preview.1", "v1.2.3-rc.0"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                parse_release_tag(tag)

    def test_ci_version_is_non_publishable_and_in_range(self):
        version = ci_version(42)
        self.assertEqual("0.0.0-ci.42", version.version_name)
        self.assertEqual(1_000_042, version.version_code)
        self.assertTrue(version.prerelease)


if __name__ == "__main__":
    unittest.main()

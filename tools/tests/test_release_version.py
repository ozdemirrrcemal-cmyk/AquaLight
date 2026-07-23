import unittest

from tools.release_version import identity_from_tag, verify_monotonic


class ReleaseVersionTest(unittest.TestCase):
    def test_derives_semver_and_monotonic_android_code(self):
        identity = identity_from_tag("v12.34.56")
        self.assertEqual("12.34.56", identity.version_name)
        self.assertEqual(12_034_056, identity.version_code)

    def test_rejects_invalid_or_ambiguous_tags(self):
        for tag in ("1.2.3", "v01.2.3", "v1.1000.0", "v1.2.1000", "v0.0.0", "v1.2.3-rc1"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                identity_from_tag(tag)

    def test_rejects_non_monotonic_release(self):
        with self.assertRaises(ValueError):
            verify_monotonic(identity_from_tag("v1.9.9"), ["v1.10.0"])

    def test_ignores_non_release_tags(self):
        verify_monotonic(identity_from_tag("v2.0.0"), ["nightly", "release-candidate", "v1.999.999"])


if __name__ == "__main__":
    unittest.main()

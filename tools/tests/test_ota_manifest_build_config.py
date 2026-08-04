from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
APP_GRADLE = ROOT / "app/build.gradle"


class OtaManifestBuildConfigTest(unittest.TestCase):
    def test_global_manifest_url_is_not_exposed_through_build_config(self):
        source = APP_GRADLE.read_text(encoding="utf-8")

        for forbidden in (
            "AQL_OTA_MANIFEST_URL",
            "AQL_OTA_STABLE_MANIFEST_URL",
            "AQL_OTA_DEBUG_MANIFEST_URL",
            "stableOtaManifestUrl",
            "debugOtaManifestUrl",
            "releases/latest/download/manifest-stable.json",
        ):
            self.assertNotIn(forbidden, source)

        self.assertIn("AQL_OTA_MANIFEST_PUBLIC_KEY_PEM", source)
        self.assertIn("AQL_OTA_MANIFEST_KEY_ID", source)


if __name__ == "__main__":
    unittest.main()

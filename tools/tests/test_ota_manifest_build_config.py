from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
APP_GRADLE = ROOT / "app/build.gradle"
FIELD = 'buildConfigField "String", "AQL_OTA_MANIFEST_URL"'
STABLE_PATH = "releases/download/ota-stable/manifest-stable.json"
LEGACY_LATEST_PATH = "releases/latest/download/manifest-stable.json"


def named_block(source: str, name: str) -> str:
    match = re.search(rf"\b{re.escape(name)}\s*\{{", source)
    if match is None:
        raise AssertionError(f"missing Gradle block: {name}")
    start = match.end()
    depth = 1
    for index in range(start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index]
    raise AssertionError(f"unterminated Gradle block: {name}")


class OtaManifestBuildConfigTest(unittest.TestCase):
    def test_fixed_stable_manifest_is_default_and_debug_is_only_override(self):
        source = APP_GRADLE.read_text(encoding="utf-8")
        default_config = named_block(source, "defaultConfig")
        debug = named_block(named_block(source, "buildTypes"), "debug")

        self.assertIn(
            f"{FIELD}, aqlBuildConfigString(stableOtaManifestUrl)",
            default_config,
        )
        self.assertIn(
            f"{FIELD}, aqlBuildConfigString(debugOtaManifestUrl)",
            debug,
        )
        self.assertEqual(2, source.count(FIELD))
        self.assertIn(".getOrElse(stableOtaManifestUrl)", source)
        self.assertIn(STABLE_PATH, source)
        self.assertNotIn(LEGACY_LATEST_PATH, source)


if __name__ == "__main__":
    unittest.main()

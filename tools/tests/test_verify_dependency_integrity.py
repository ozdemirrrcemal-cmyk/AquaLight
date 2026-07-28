from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

sys.path.insert(0, str(ROOT / "tools"))

from verify_dependency_integrity import (
    parse_build_tools_manifest,
    parse_metadata,
    parse_plugin_versions,
    required_components_for_plugins,
)


PLUGIN_BLOCK = """
pluginManagement {
    repositories {
        maven { url "https://dl.google.com/dl/android/maven2" }
    }
    plugins {
        id 'com.android.application' version '8.10.0'
        id 'org.jetbrains.kotlin.android' version '2.1.0'
        id 'com.google.gms.google-services' version '4.4.4'
        id 'com.google.protobuf' version '0.9.5'
        id 'androidx.navigation.safeargs.kotlin' version '2.9.5'
    }
}
"""


class DependencyIntegrityPluginManifestTest(unittest.TestCase):
    def parse(self, source: str) -> dict[str, str]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "settings.gradle"
            path.write_text(source, encoding="utf-8")
            return parse_plugin_versions(path)

    def test_repository_plugin_versions_match_verification_metadata(self) -> None:
        versions = parse_plugin_versions(ROOT / "settings.gradle")
        required = required_components_for_plugins(versions)
        required.update(
            parse_build_tools_manifest(
                ROOT
                / "config"
                / "dependency-integrity"
                / "resolved-build-tools.json",
                versions,
            )
        )

        component_count, artifact_count, components = parse_metadata(
            ROOT / "gradle" / "verification-metadata.xml",
            required,
        )

        self.assertGreater(component_count, 0)
        self.assertGreater(artifact_count, 0)
        self.assertTrue(required.issubset(components))

    def test_comments_and_repository_urls_do_not_corrupt_parser(self) -> None:
        source = PLUGIN_BLOCK.replace(
            "id 'org.jetbrains.kotlin.android' version '2.1.0'",
            """
        /* ignored: id 'org.jetbrains.kotlin.android' version '99.0.0' */
        id 'org.jetbrains.kotlin.android' version '2.1.0' // pinned toolchain
            """.strip(),
        )

        versions = self.parse(source)

        self.assertEqual("2.1.0", versions["org.jetbrains.kotlin.android"])

    def test_duplicate_plugin_declaration_is_rejected(self) -> None:
        source = PLUGIN_BLOCK.replace(
            "id 'com.android.application' version '8.10.0'",
            """
        id 'com.android.application' version '8.10.0'
        id 'com.android.application' version '8.10.1'
            """.strip(),
        )

        with self.assertRaisesRegex(ValueError, "declared exactly once"):
            self.parse(source)

    def test_nonliteral_or_dynamic_version_is_rejected(self) -> None:
        nonliteral = PLUGIN_BLOCK.replace(
            "version '2.1.0'",
            "version kotlinVersion",
            1,
        )
        with self.assertRaisesRegex(ValueError, "literal version"):
            self.parse(nonliteral)

        dynamic = PLUGIN_BLOCK.replace(
            "version '2.1.0'",
            "version '2.+'",
            1,
        )
        with self.assertRaisesRegex(ValueError, "pinned literal"):
            self.parse(dynamic)

    def test_trailing_plugin_expression_is_rejected(self) -> None:
        source = PLUGIN_BLOCK.replace(
            "id 'com.android.application' version '8.10.0'",
            "id 'com.android.application' version '8.10.0' + suffix",
        )

        with self.assertRaisesRegex(ValueError, "literal version"):
            self.parse(source)

    def test_declared_version_drift_cannot_pass_on_historical_metadata(self) -> None:
        source = PLUGIN_BLOCK.replace(
            "id 'com.android.application' version '8.10.0'",
            "id 'com.android.application' version '8.10.1'",
        )
        versions = self.parse(source)
        required = required_components_for_plugins(versions)

        with self.assertRaisesRegex(
            ValueError,
            "com.android.tools.build:gradle:8.10.1",
        ):
            parse_metadata(
                ROOT / "gradle" / "verification-metadata.xml",
                required,
            )

    def test_missing_expected_plugin_is_rejected(self) -> None:
        source = PLUGIN_BLOCK.replace(
            "id 'com.google.gms.google-services' version '4.4.4'",
            "",
        )

        with self.assertRaisesRegex(
            ValueError,
            "com.google.gms.google-services must be declared exactly once",
        ):
            self.parse(source)

    def test_stale_resolved_build_tool_identity_is_rejected(self) -> None:
        versions = parse_plugin_versions(ROOT / "settings.gradle")
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "resolved-build-tools.json"
            manifest.write_text(
                """
                {
                  "androidGradlePluginVersion": "8.10.0",
                  "components": [
                    {
                      "group": "com.android.tools.build",
                      "name": "aapt2",
                      "version": "8.9.0-00000000"
                    },
                    {
                      "group": "com.android.tools.lint",
                      "name": "lint-gradle",
                      "version": "31.9.0"
                    }
                  ],
                  "schemaVersion": 1
                }
                """,
                encoding="utf-8",
            )
            required = required_components_for_plugins(versions)
            required.update(parse_build_tools_manifest(manifest, versions))

            with self.assertRaisesRegex(
                ValueError,
                "com.android.tools.build:aapt2:8.9.0-00000000",
            ):
                parse_metadata(
                    ROOT / "gradle" / "verification-metadata.xml",
                    required,
                )


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import json
import re
import unittest

from tools.firebase_environment_guard import (
    ENVIRONMENTS,
    LEGACY_VARIANT_TASKS,
    ROOT,
    validate_repository,
)


class FirebaseEnvironmentContractTest(unittest.TestCase):
    def test_repository_environment_contract_is_valid(self) -> None:
        self.assertEqual([], validate_repository())

    def test_projects_packages_and_app_ids_are_unique(self) -> None:
        project_ids: set[str] = set()
        package_names: set[str] = set()
        firebase_app_ids: set[str] = set()

        for environment, contract in ENVIRONMENTS.items():
            config = json.loads(
                (
                    ROOT / f"app/src/{environment}/google-services.json"
                ).read_text(encoding="utf-8")
            )
            project_ids.add(config["project_info"]["project_id"])
            package_names.add(contract["package_name"])
            firebase_app_ids.add(
                config["client"][0]["client_info"]["mobilesdk_app_id"]
            )

        self.assertEqual(3, len(project_ids))
        self.assertEqual(3, len(package_names))
        self.assertEqual(3, len(firebase_app_ids))

    def test_only_production_uses_a_live_project(self) -> None:
        self.assertTrue(ENVIRONMENTS["development"]["project_id"].startswith("demo-"))
        self.assertTrue(ENVIRONMENTS["staging"]["project_id"].startswith("demo-"))
        self.assertFalse(ENVIRONMENTS["production"]["project_id"].startswith("demo-"))

    def test_only_supported_gradle_variants_are_enabled(self) -> None:
        gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

        self.assertIn('development: ["debug"]', gradle)
        self.assertIn('staging    : ["releaseSmoke"]', gradle)
        self.assertIn('production : ["release"]', gradle)
        self.assertIn("variantBuilder.enable =", gradle)

    def test_gradle_task_input_keys_are_real_strings(self) -> None:
        gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

        for key in ("projectId", "packageName", "demoProject"):
            self.assertIn(f'"${{environment}}.{key}".toString()', gradle)

    def test_firebase_is_installed_before_repository_construction(self) -> None:
        application = (
            ROOT / "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
        ).read_text(encoding="utf-8")

        self.assertLess(
            application.index("FirebaseEnvironmentInstaller.install(this)"),
            application.index("appContainer = DefaultAppContainer(this)"),
        )

    def test_ci_does_not_use_ambiguous_aggregate_variants(self) -> None:
        paths = tuple((ROOT / ".github/workflows").glob("*.yml")) + (
            ROOT / "tools/run_release_smoke.sh",
            ROOT / "tools/verify_uninstall_clears_data.sh",
        )
        text = "\n".join(path.read_text(encoding="utf-8") for path in paths)

        for task in LEGACY_VARIANT_TASKS:
            self.assertIsNone(
                re.search(rf"(?<![A-Za-z]){re.escape(task)}(?![A-Za-z])", text),
                task,
            )

    def test_google_oauth_client_is_generated_per_flavor(self) -> None:
        values_paths = (
            ROOT / "app/src/main/res/values/strings.xml",
            ROOT / "app/src/main/res/values-tr/strings.xml",
        )
        for path in values_paths:
            self.assertNotIn(
                "default_web_client_id",
                path.read_text(encoding="utf-8"),
            )

    def test_nonproduction_apps_have_visible_environment_labels(self) -> None:
        expected_labels = {
            "development": ("AquaLight Dev", "AquaLight Geliştirme"),
            "staging": ("AquaLight Staging", "AquaLight Staging"),
        }
        for environment, labels in expected_labels.items():
            for qualifier, expected_label in zip(("values", "values-tr"), labels):
                source = (
                    ROOT / f"app/src/{environment}/res/{qualifier}/strings.xml"
                ).read_text(encoding="utf-8")
                self.assertIn(expected_label, source)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Fail CI when Android/Firebase environment isolation can regress."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]

ENVIRONMENTS = {
    "development": {
        "project_id": "demo-aqualight-development",
        "package_name": "com.aqua.aqualight.dev",
        "demo": True,
    },
    "staging": {
        "project_id": "demo-aqualight-staging",
        "package_name": "com.aqua.aqualight.staging",
        "demo": True,
    },
    "production": {
        "project_id": "aqualight-58aa2",
        "package_name": "com.aqua.aqualight",
        "demo": False,
    },
}

REQUIRED_WORKFLOW_TOKENS = {
    ".github/workflows/android.yml": (
        "lintDevelopmentDebug",
        "testDevelopmentDebugUnitTest",
        "assembleDevelopmentDebug",
        "developmentDebugRuntimeClasspath",
        "app/build/outputs/apk/development/debug",
    ),
    ".github/workflows/android_instrumentation.yml": (
        "assembleStagingReleaseSmoke",
        "app/build/outputs/apk/staging/releaseSmoke",
    ),
    ".github/workflows/codeql.yml": (
        "assembleDevelopmentDebug",
        "testDevelopmentDebugUnitTest",
        "lintDevelopmentDebug",
        "assembleStagingReleaseSmoke",
        "testStagingReleaseSmokeUnitTest",
        "lintStagingReleaseSmoke",
    ),
    ".github/workflows/android_release.yml": (
        "lintStagingReleaseSmoke",
        "testStagingReleaseSmokeUnitTest",
        "assembleStagingReleaseSmoke",
        "lintProductionRelease",
        "testProductionReleaseUnitTest",
        "assembleProductionRelease",
    ),
}

LEGACY_VARIANT_TASKS = (
    "assembleDebug",
    "testDebugUnitTest",
    "lintDebug",
    "assembleReleaseSmoke",
    "testReleaseSmokeUnitTest",
    "lintReleaseSmoke",
    "assembleRelease",
    "testReleaseUnitTest",
    "lintRelease",
    "connectedDebugAndroidTest",
)


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def selected_client(config: dict[str, Any], package_name: str) -> dict[str, Any] | None:
    matches = [
        client
        for client in config.get("client", [])
        if client.get("client_info", {})
        .get("android_client_info", {})
        .get("package_name")
        == package_name
    ]
    return matches[0] if len(matches) == 1 else None


def validate_repository(root: Path = ROOT) -> list[str]:
    failures: list[str] = []
    project_ids: set[str] = set()
    app_ids: set[str] = set()
    api_keys: set[str] = set()
    web_client_ids: set[str] = set()

    if (root / "app/google-services.json").exists():
        failures.append(
            "app/google-services.json is forbidden because it applies to every variant"
        )

    for environment, contract in ENVIRONMENTS.items():
        path = root / f"app/src/{environment}/google-services.json"
        if not path.is_file():
            failures.append(f"missing {environment} google-services.json")
            continue

        try:
            config = load_json(path)
        except (OSError, json.JSONDecodeError) as error:
            failures.append(f"invalid {environment} google-services.json: {error}")
            continue

        project_id = config.get("project_info", {}).get("project_id")
        if project_id != contract["project_id"]:
            failures.append(
                f"{environment} project ID must be {contract['project_id']}, "
                f"found {project_id}"
            )
        if project_id in project_ids:
            failures.append(f"Firebase project is shared across environments: {project_id}")
        if project_id:
            project_ids.add(project_id)

        is_demo = isinstance(project_id, str) and project_id.startswith("demo-")
        if is_demo != contract["demo"]:
            failures.append(
                f"{environment} demo/live policy does not match project {project_id}"
            )

        client = selected_client(config, contract["package_name"])
        if client is None:
            failures.append(
                f"{environment} must have exactly one Android client for "
                f"{contract['package_name']}"
            )
            continue

        app_id = client.get("client_info", {}).get("mobilesdk_app_id")
        if not app_id:
            failures.append(f"{environment} Firebase app ID is missing")
        elif app_id in app_ids:
            failures.append(f"Firebase app ID is shared across environments: {app_id}")
        else:
            app_ids.add(app_id)

        environment_api_keys = {
            item.get("current_key")
            for item in client.get("api_key", [])
            if item.get("current_key")
        }
        if len(environment_api_keys) != 1:
            failures.append(f"{environment} must have exactly one Android API key")
        for api_key in environment_api_keys:
            if api_key in api_keys:
                failures.append("Firebase API key is shared across environments")
            api_keys.add(api_key)

        environment_web_clients = {
            item.get("client_id")
            for item in client.get("oauth_client", [])
            if item.get("client_type") == 3 and item.get("client_id")
        }
        if len(environment_web_clients) != 1:
            failures.append(f"{environment} must have exactly one web OAuth client")
        for web_client_id in environment_web_clients:
            if web_client_id in web_client_ids:
                failures.append("Firebase web OAuth client is shared across environments")
            web_client_ids.add(web_client_id)

    firebaserc_path = root / ".firebaserc"
    if not firebaserc_path.is_file():
        failures.append(".firebaserc environment aliases are missing")
    else:
        aliases = load_json(firebaserc_path).get("projects", {})
        expected_aliases = {
            name: contract["project_id"] for name, contract in ENVIRONMENTS.items()
        }
        if aliases != expected_aliases:
            failures.append(".firebaserc aliases do not match Android Firebase environments")

    build_gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
    required_gradle_tokens = (
        'flavorDimensions "firebaseEnvironment"',
        "allowedEnvironmentBuildTypes",
        'development: ["debug"]',
        'staging    : ["releaseSmoke"]',
        'production : ["release"]',
        "verifyFirebaseEnvironmentSeparation",
        '"${environment}.projectId".toString()',
        '"${environment}.packageName".toString()',
        '"${environment}.demoProject".toString()',
        'configurations.named("developmentDebugRuntimeClasspath")',
        'configurations.named("stagingReleaseSmokeRuntimeClasspath")',
        'configurations.named("productionReleaseRuntimeClasspath")',
    )
    for token in required_gradle_tokens:
        if token not in build_gradle:
            failures.append(f"app/build.gradle is missing Firebase contract token: {token}")

    installer = (
        root
        / "app/src/main/java/com/aqua/aqualight/app/FirebaseEnvironmentInstaller.kt"
    ).read_text(encoding="utf-8")
    for token in (
        "BuildConfig.AQL_FIREBASE_PROJECT_ID",
        "BuildConfig.AQL_FIREBASE_ENVIRONMENT",
        "BuildConfig.AQL_FIREBASE_USE_EMULATORS",
        'private const val EMULATOR_HOST = "10.0.2.2"',
        "FirebaseAuth.getInstance(firebaseApp).useEmulator",
        "FirebaseFirestore.getInstance(firebaseApp).useEmulator",
    ):
        if token not in installer:
            failures.append(f"Firebase runtime installer is missing: {token}")

    application = (
        root / "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
    ).read_text(encoding="utf-8")
    installer_position = application.find("FirebaseEnvironmentInstaller.install(this)")
    container_position = application.find("appContainer = DefaultAppContainer(this)")
    if (
        installer_position < 0
        or container_position < 0
        or installer_position >= container_position
    ):
        failures.append("Firebase environment must be installed before app repositories")

    for values_path in (
        root / "app/src/main/res/values/strings.xml",
        root / "app/src/main/res/values-tr/strings.xml",
    ):
        if "default_web_client_id" in values_path.read_text(encoding="utf-8"):
            failures.append(
                f"production Google OAuth client is hard-coded in {values_path.relative_to(root)}"
            )

    environment_labels = {
        "development": ("AquaLight Dev", "AquaLight Geliştirme"),
        "staging": ("AquaLight Staging", "AquaLight Staging"),
    }
    for environment, labels in environment_labels.items():
        for qualifier, expected_label in zip(("values", "values-tr"), labels):
            label_path = (
                root / f"app/src/{environment}/res/{qualifier}/strings.xml"
            )
            if not label_path.is_file() or expected_label not in label_path.read_text(
                encoding="utf-8"
            ):
                failures.append(
                    f"{environment} {qualifier} app label must identify the environment"
                )

    workflow_text = ""
    for relative_path, required_tokens in REQUIRED_WORKFLOW_TOKENS.items():
        path = root / relative_path
        text = path.read_text(encoding="utf-8")
        workflow_text += f"\n{text}"
        for token in required_tokens:
            if token not in text:
                failures.append(f"{relative_path} is missing explicit variant token: {token}")

    scripts_text = "\n".join(
        (
            (root / "tools/run_release_smoke.sh").read_text(encoding="utf-8"),
            (root / "tools/verify_uninstall_clears_data.sh").read_text(
                encoding="utf-8"
            ),
        )
    )
    for token in (
        "connectedDevelopmentDebugAndroidTest",
        "com.aqua.aqualight.dev",
        "com.aqua.aqualight.staging",
        "app/build/outputs/apk/development/debug/app-development-debug.apk",
    ):
        if token not in scripts_text:
            failures.append(f"device smoke scripts are missing environment token: {token}")

    explicit_variant_text = workflow_text + "\n" + scripts_text
    for task in LEGACY_VARIANT_TASKS:
        if re.search(rf"(?<![A-Za-z]){re.escape(task)}(?![A-Za-z])", explicit_variant_text):
            failures.append(f"legacy aggregate Android task remains in CI: {task}")

    rules_test = (root / "firebase/rules.test.mjs").read_text(encoding="utf-8")
    if "demo-aqualight-development" not in rules_test:
        failures.append("Firestore rules tests do not use the development project identity")

    return failures


def main() -> int:
    failures = validate_repository()
    if failures:
        print("Firebase environment guard failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print(
        "Firebase environment guard passed: development, staging and production "
        "have isolated projects, packages and CI variants."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

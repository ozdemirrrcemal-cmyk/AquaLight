#!/usr/bin/env python3
"""Keep installable Debug on the production, physical-device data path."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

FORBIDDEN_DEBUG_TOKENS = (
    "DebugDeviceFixture",
    "DebugFixture",
    "DebugLightFixtureRuntime",
    "DebugTimerFixtureRuntime",
    "InstallableDebugFixture",
    "installable_debug_device_fixture",
    "installable-debug-fixture",
    "DEBUG-FIXTURE-",
    "FakeDevice",
    "MockDevice",
    "StubDevice",
    "replaceAppContainerForProcess(",
)
REQUIRED_WORKFLOW_TOKENS = (
    "python3 tools/installable_debug_real_device_guard.py",
    "./gradlew :app:assembleDebug",
)


def relative(path: Path, repository_root: Path) -> str:
    return path.relative_to(repository_root).as_posix()


def validate_repository(repository_root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    debug_source_root = repository_root / "app/src/debug"
    debug_device_source_root = (
        debug_source_root / "java/com/aqua/aqualight/debug/devices"
    )
    legacy_flag_path = (
        debug_source_root / "res/values/installable_debug_device_fixture.xml"
    )
    debug_manifest_path = debug_source_root / "AndroidManifest.xml"
    workflow_path = repository_root / ".github/workflows/installable_debug_apk.yml"
    application_path = (
        repository_root / "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
    )

    if debug_device_source_root.is_dir():
        for path in sorted(debug_device_source_root.rglob("*")):
            if path.is_file():
                errors.append(
                    f"{relative(path, repository_root)}: alternate debug device composition "
                    "is forbidden; installable Debug must use physical devices"
                )

    if legacy_flag_path.exists():
        errors.append(
            f"{relative(legacy_flag_path, repository_root)}: runtime fake-device switch "
            "must not exist"
        )

    scanned_paths = [
        path
        for path in sorted(debug_source_root.rglob("*"))
        if path.is_file() and path.suffix in {".kt", ".java", ".xml"}
    ]
    if debug_manifest_path.is_file() and debug_manifest_path not in scanned_paths:
        scanned_paths.append(debug_manifest_path)

    for path in scanned_paths:
        source = path.read_text(encoding="utf-8", errors="ignore")
        for token in FORBIDDEN_DEBUG_TOKENS:
            if token in source:
                errors.append(
                    f"{relative(path, repository_root)}: forbidden fake-device bootstrap "
                    f"token: {token}"
                )

    if not workflow_path.is_file():
        errors.append(".github/workflows/installable_debug_apk.yml: workflow is missing")
    else:
        workflow = workflow_path.read_text(encoding="utf-8", errors="strict")
        for token in FORBIDDEN_DEBUG_TOKENS:
            if token in workflow:
                errors.append(
                    f"{relative(workflow_path, repository_root)}: workflow must not enable "
                    f"fake-device path: {token}"
                )
        for token in REQUIRED_WORKFLOW_TOKENS:
            if token not in workflow:
                errors.append(
                    f"{relative(workflow_path, repository_root)}: required real-device Debug "
                    f"build token is missing: {token}"
                )

    if not application_path.is_file():
        errors.append(
            "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt: application is missing"
        )
    else:
        application = application_path.read_text(encoding="utf-8", errors="strict")
        if "appContainer = DefaultAppContainer(this)" not in application:
            errors.append(
                f"{relative(application_path, repository_root)}: production AppContainer "
                "bootstrap is missing"
            )

    return errors


def main() -> int:
    errors = validate_repository()
    if errors:
        print("Installable Debug real-device guard failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print("Installable Debug real-device guard passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

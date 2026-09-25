import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GUARD_PATH = ROOT / "tools/installable_debug_real_device_guard.py"
SPEC = importlib.util.spec_from_file_location(
    "installable_debug_real_device_guard",
    GUARD_PATH,
)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class InstallableDebugRealDeviceGuardTest(unittest.TestCase):
    def test_repository_passes(self) -> None:
        result = subprocess.run(
            [sys.executable, str(GUARD_PATH)],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertEqual(0, result.returncode, result.stderr)

    def test_any_alternate_debug_device_composition_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            source = (
                repository
                / "app/src/debug/java/com/aqua/aqualight/debug/devices/Alternate.kt"
            )
            source.parent.mkdir(parents=True)
            source.write_text("package com.aqua.aqualight.debug.devices\n", encoding="utf-8")

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("alternate debug device composition" in error for error in errors))

    def test_process_local_container_replacement_is_rejected_in_debug(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            source = repository / "app/src/debug/java/example/Bootstrap.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "fun install(app: App) = app.replaceAppContainerForProcess(container)\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("forbidden fake-device bootstrap" in error for error in errors))

    def test_workflow_cannot_reenable_legacy_device_data(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            workflow = repository / ".github/workflows/installable_debug_apk.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8")
                + "\ninstallable_debug_device_fixture: true\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("workflow must not enable" in error for error in errors))

    def test_production_container_bootstrap_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            application = (
                repository / "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
            )
            application.write_text("class AquaApp\n", encoding="utf-8")

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("production AppContainer bootstrap" in error for error in errors))

    @staticmethod
    def _create_minimum_repository(repository: Path) -> None:
        manifest = repository / "app/src/debug/AndroidManifest.xml"
        manifest.parent.mkdir(parents=True)
        manifest.write_text("<manifest><application /></manifest>\n", encoding="utf-8")

        workflow = repository / ".github/workflows/installable_debug_apk.yml"
        workflow.parent.mkdir(parents=True)
        workflow.write_text(
            "run: python3 tools/installable_debug_real_device_guard.py\n"
            "run: ./gradlew :app:assembleDebug\n",
            encoding="utf-8",
        )

        application = repository / "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
        application.parent.mkdir(parents=True)
        application.write_text(
            "appContainer = DefaultAppContainer(this)\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    unittest.main()

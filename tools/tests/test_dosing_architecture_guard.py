import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GUARD_PATH = ROOT / "tools/dosing_architecture_guard.py"
SPEC = importlib.util.spec_from_file_location("dosing_architecture_guard", GUARD_PATH)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class DosingArchitectureGuardTest(unittest.TestCase):
    def test_repository_passes(self) -> None:
        result = subprocess.run(
            [sys.executable, str(GUARD_PATH)],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_ui_to_data_dependency_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            screen = (
                repository
                / "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/"
                "DosingCatalogScreen.kt"
            )
            screen.write_text(
                screen.read_text(encoding="utf-8")
                + "\nimport com.aqua.aqualight.data.devices.dosing.SomeAdapter\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("data-layer dependency" in error for error in errors), errors)

    def test_ui_fully_qualified_data_dependency_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            screen = (
                repository
                / "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/"
                "DosingCatalogScreen.kt"
            )
            screen.write_text(
                screen.read_text(encoding="utf-8")
                + "\nprivate val forbidden: "
                "com.aqua.aqualight.data.devices.dosing.Adapter? = null\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("data-layer dependency" in error for error in errors), errors)

    def test_application_fully_qualified_outer_dependency_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            contract = (
                repository
                / "app/src/main/java/com/aqua/aqualight/application/devices/dosing/Contract.kt"
            )
            contract.write_text(
                contract.read_text(encoding="utf-8")
                + "\nprivate val forbidden: "
                "com.aqua.aqualight.data.devices.dosing.Adapter? = null\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("outer layer" in error for error in errors), errors)

    def test_data_fully_qualified_ui_dependency_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            adapter = (
                repository
                / "app/src/main/java/com/aqua/aqualight/data/devices/dosing/Adapter.kt"
            )
            adapter.write_text(
                adapter.read_text(encoding="utf-8")
                + "\nprivate val forbidden: "
                "com.aqua.aqualight.ui.tabs.devices.detail.dosing.root."
                "DosingCatalogScreen? = null\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("must not depend on UI" in error for error in errors), errors)

    def test_misplaced_application_declaration_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            misplaced = (
                repository
                / "app/src/main/java/com/aqua/aqualight/application/devices/"
                "DeviceDosingMutationCoordinator.kt"
            )
            misplaced.write_text(
                "package com.aqua.aqualight.application.devices\n\n"
                "class DeviceDosingMutationCoordinator\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("application/devices/dosing" in error for error in errors), errors)

    @staticmethod
    def _create_minimum_repository(repository: Path) -> None:
        source_root = repository / "app/src/main/java/com/aqua/aqualight"
        application = source_root / "application/devices/dosing"
        data = source_root / "data/devices/dosing"
        ui = source_root / "ui/tabs/devices/detail/dosing"
        application.mkdir(parents=True)
        data.mkdir(parents=True)
        (application / "Contract.kt").write_text(
            "package com.aqua.aqualight.application.devices.dosing\n\ninternal class Contract\n",
            encoding="utf-8",
        )
        (data / "Adapter.kt").write_text(
            "package com.aqua.aqualight.data.devices.dosing\n\ninternal class Adapter\n",
            encoding="utf-8",
        )
        for relative_path in GUARD.REQUIRED_DATA_FILES:
            (data / relative_path).write_text(
                "package com.aqua.aqualight.data.devices.dosing\n",
                encoding="utf-8",
            )
        for relative_path in GUARD.REQUIRED_UI_FILES:
            path = ui / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            package = "com.aqua.aqualight." + path.parent.relative_to(source_root).as_posix().replace(
                "/", "."
            )
            declaration = (
                "\ninternal class DosingCatalogScreen\n"
                if path.name == "DosingCatalogScreen.kt"
                else ""
            )
            path.write_text(f"package {package}\n{declaration}", encoding="utf-8")


if __name__ == "__main__":
    unittest.main()

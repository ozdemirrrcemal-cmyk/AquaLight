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

    def test_ui_firmware_low_level_signal_is_rejected(self) -> None:
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
                + "\nprivate val lowLevelActive = false\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("firmware low-level signal" in error for error in errors), errors)

    def test_ui_firmware_error_code_is_rejected(self) -> None:
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
                + '\nprivate const val forbidden = "STORAGE_ERROR"\n',
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("firmware error code" in error for error in errors), errors)

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

    def test_parallel_dosing_state_owner_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            duplicate = (
                repository
                / "app/src/main/java/com/aqua/aqualight/data/devices/dosing/"
                "ParallelDosingStateOwner.kt"
            )
            duplicate.write_text(
                "package com.aqua.aqualight.data.devices.dosing\n\n"
                "internal class ParallelDosingStateOwner\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("exactly one canonical" in error for error in errors), errors)

    def test_fail_closed_production_dosing_binding_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            composition = repository / "app/src/main/java/com/aqua/aqualight/composition"
            composition.mkdir(parents=True)
            (composition / "OwnerViewModelFactory.kt").write_text(
                "package com.aqua.aqualight.composition\n\n"
                "private val forbidden = UnavailableDeviceDosingChannelOperations\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("fail-closed Dosing binding" in error for error in errors), errors)

    def test_false_production_wiring_pin_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            fixtures = repository / "protocol/fixtures"
            fixtures.mkdir(parents=True)
            (fixtures / "aql_android_dosing_v1_pin.json").write_text(
                '{"contract":{"productionWiring":false}}',
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("productionWiring must be true" in error for error in errors), errors)

    def test_debug_dosing_fixture_implementation_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            debug_root = repository / "app/src/debug/java/com/aqua/aqualight/debug/devices"
            debug_root.mkdir(parents=True)
            (debug_root / "DebugFixtureDosingChannelOperations.kt").write_text(
                "package com.aqua.aqualight.debug.devices\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("Dosing debug fixture implementation" in error for error in errors), errors)

    def test_debug_fixture_family_allowlist_must_exclude_dosing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            debug_root = repository / "app/src/debug/java/com/aqua/aqualight/debug/devices"
            debug_root.mkdir(parents=True)
            (debug_root / "DebugDeviceFixtureCatalog.kt").write_text(
                ".filter { product -> product.family in FIXTURE_FAMILIES }\n"
                "private val FIXTURE_FAMILIES = setOf(\n"
                "    DeviceFamily.LIGHT,\n"
                "    DeviceFamily.DOSING\n"
                ")\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any("Dosing products must be excluded" in error for error in errors), errors)

    def test_debug_fixture_family_allowlist_without_dosing_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._create_minimum_repository(repository)
            debug_root = repository / "app/src/debug/java/com/aqua/aqualight/debug/devices"
            debug_root.mkdir(parents=True)
            (debug_root / "DebugDeviceFixtureCatalog.kt").write_text(
                ".filter { product -> product.family in FIXTURE_FAMILIES }\n"
                "private val FIXTURE_FAMILIES = setOf(\n"
                "    DeviceFamily.LIGHT,\n"
                "    DeviceFamily.TIMER\n"
                ")\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_repository(repository)

            self.assertFalse(any("Dosing products must be excluded" in error for error in errors), errors)

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
            path = data / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            package = "com.aqua.aqualight." + path.parent.relative_to(source_root).as_posix().replace(
                "/", "."
            )
            declaration = (
                "\ninternal class DeviceDosingV1StateOwner\n"
                if path.name == "DeviceDosingV1StateOwner.kt"
                else ""
            )
            path.write_text(
                f"package {package}\n{declaration}",
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

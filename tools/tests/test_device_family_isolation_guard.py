import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GUARD_PATH = ROOT / "tools/device_family_isolation_guard.py"
SPEC = importlib.util.spec_from_file_location("device_family_isolation_guard", GUARD_PATH)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class DeviceFamilyIsolationGuardTest(unittest.TestCase):
    def test_repository_passes(self) -> None:
        result = subprocess.run(
            [sys.executable, str(GUARD_PATH)],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_family_code_can_depend_on_shared_device_contracts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._write(
                repository,
                "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/CoolingScreen.kt",
                "package com.aqua.aqualight.ui.tabs.devices.detail.cooling\n"
                "import com.aqua.aqualight.application.devices.DeviceRootOperations\n"
                "internal class CoolingScreen\n",
            )
            self._write(
                repository,
                "app/src/main/java/com/aqua/aqualight/application/devices/dosing/DosingOperations.kt",
                "package com.aqua.aqualight.application.devices.dosing\n"
                "import com.aqua.aqualight.application.devices.DeviceRootOperations\n"
                "internal class DosingOperations\n",
            )

            self.assertEqual([], GUARD.validate_repository(repository))

    def test_cooling_ui_cannot_import_dosing_ui(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/CoolingScreen.kt",
            "package com.aqua.aqualight.ui.tabs.devices.detail.cooling\n"
            "import com.aqua.aqualight.ui.tabs.devices.detail.dosing.root.DeviceDosingRootViewModel\n"
            "internal class CoolingScreen\n",
            "cooling ui implementation must not depend on dosing implementation package",
        )

    def test_cooling_application_cannot_import_dosing_application(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/application/devices/cooling/CoolingOperations.kt",
            "package com.aqua.aqualight.application.devices.cooling\n"
            "import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations\n"
            "internal class CoolingOperations\n",
            "cooling application implementation must not depend on dosing implementation package",
        )

    def test_existing_cooling_runtime_path_cannot_import_dosing_data(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/cooling/DeviceCoolingRuntime.kt",
            "package com.aqua.aqualight.data.devices.runtime.modules.cooling\n"
            "import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1StateOwner\n"
            "internal class DeviceCoolingRuntime\n",
            "cooling data implementation must not depend on dosing implementation package",
        )

    def test_dosing_ui_cannot_import_cooling_ui(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/root/DosingScreen.kt",
            "package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root\n"
            "import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingRootViewModel\n"
            "internal class DosingScreen\n",
            "dosing ui implementation must not depend on cooling implementation package",
        )

    def test_dosing_application_cannot_import_cooling_application(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/application/devices/dosing/DosingOperations.kt",
            "package com.aqua.aqualight.application.devices.dosing\n"
            "import com.aqua.aqualight.application.devices.cooling.DeviceCoolingOperations\n"
            "internal class DosingOperations\n",
            "dosing application implementation must not depend on cooling implementation package",
        )

    def test_dosing_data_cannot_import_cooling_runtime(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/data/devices/dosing/v1/DeviceDosingRuntime.kt",
            "package com.aqua.aqualight.data.devices.dosing.v1\n"
            "import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository\n"
            "internal class DeviceDosingRuntime\n",
            "dosing data implementation must not depend on cooling implementation package",
        )

    def test_family_specific_symbol_cannot_leak_through_common_package(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/CoolingScreen.kt",
            "package com.aqua.aqualight.ui.tabs.devices.detail.cooling\n"
            "import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot\n"
            "internal class CoolingScreen(private val slot: DeviceDosingChannelSlot)\n",
            "cooling ui implementation must not reference dosing-specific symbol",
        )

    def test_shared_composition_may_wire_multiple_family_implementations(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._write(
                repository,
                "app/src/main/java/com/aqua/aqualight/composition/DeviceFamilyComposition.kt",
                "package com.aqua.aqualight.composition\n"
                "import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations\n"
                "import com.aqua.aqualight.application.devices.cooling.DeviceCoolingOperations\n"
                "internal class DeviceFamilyComposition\n",
            )

            self.assertEqual([], GUARD.validate_repository(repository))

    def _assert_rejected(self, path: str, source: str, expected: str) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._write(repository, path, source)

            errors = GUARD.validate_repository(repository)

            self.assertTrue(any(expected in error for error in errors), errors)

    @staticmethod
    def _write(repository: Path, relative_path: str, source: str) -> None:
        path = repository / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    unittest.main()

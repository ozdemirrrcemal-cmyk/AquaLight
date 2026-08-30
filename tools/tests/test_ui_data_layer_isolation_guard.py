import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GUARD_PATH = ROOT / "tools/ui_data_layer_isolation_guard.py"
SPEC = importlib.util.spec_from_file_location("ui_data_layer_isolation_guard", GUARD_PATH)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class UiDataLayerIsolationGuardTest(unittest.TestCase):
    def test_repository_passes(self) -> None:
        result = subprocess.run(
            [sys.executable, str(GUARD_PATH)],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_ui_import_of_data_is_rejected(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/ui/ExampleScreen.kt",
            "package com.aqua.aqualight.ui\n"
            "import com.aqua.aqualight.data.devices.repository.DevicesRepository\n"
            "internal class ExampleScreen\n",
            "UI layer must not add a dependency on data layer",
        )

    def test_ui_fully_qualified_data_reference_is_rejected(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/ui/ExampleViewModel.kt",
            "package com.aqua.aqualight.ui\n"
            "private val repository: com.aqua.aqualight.data.devices.repository.DevicesRepository? = null\n",
            "UI layer must not add a dependency on data layer",
        )

    def test_data_import_of_ui_is_rejected(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/data/ExampleRepository.kt",
            "package com.aqua.aqualight.data\n"
            "import com.aqua.aqualight.ui.tabs.devices.DeviceCardUi\n"
            "internal class ExampleRepository\n",
            "data layer must not depend on UI layer",
        )

    def test_data_fully_qualified_ui_reference_is_rejected(self) -> None:
        self._assert_rejected(
            "app/src/main/java/com/aqua/aqualight/data/ExampleMapper.kt",
            "package com.aqua.aqualight.data\n"
            "private val model: com.aqua.aqualight.ui.tabs.devices.DeviceCardUi? = null\n",
            "data layer must not depend on UI layer",
        )

    def test_databinding_package_is_not_misclassified_as_data_layer(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._write(
                repository,
                "app/src/main/java/com/aqua/aqualight/ui/ExampleFragment.kt",
                "package com.aqua.aqualight.ui\n"
                "import com.aqua.aqualight.databinding.ExampleFragmentBinding\n"
                "internal class ExampleFragment\n",
            )

            self.assertEqual([], GUARD.validate_repository(repository))

    def test_ui_may_depend_on_application_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._write(
                repository,
                "app/src/main/java/com/aqua/aqualight/ui/ExampleViewModel.kt",
                "package com.aqua.aqualight.ui\n"
                "import com.aqua.aqualight.application.devices.DeviceRootOperations\n"
                "internal class ExampleViewModel(private val operations: DeviceRootOperations)\n",
            )

            self.assertEqual([], GUARD.validate_repository(repository))

    def test_legacy_ui_data_edge_is_frozen_by_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            source_path = "app/src/main/java/com/aqua/aqualight/ui/LegacyViewModel.kt"
            reference = "com.aqua.aqualight.data.legacy.LegacyRepository"
            self._write(
                repository,
                source_path,
                "package com.aqua.aqualight.ui\n"
                f"import {reference}\n"
                "internal class LegacyViewModel\n",
            )
            self._write_baseline(repository, {source_path: [reference]})

            self.assertEqual([], GUARD.validate_repository(repository))

    def test_new_ui_data_edge_beside_legacy_baseline_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            source_path = "app/src/main/java/com/aqua/aqualight/ui/LegacyViewModel.kt"
            legacy = "com.aqua.aqualight.data.legacy.LegacyRepository"
            new_edge = "com.aqua.aqualight.data.devices.repository.DevicesRepository"
            self._write(
                repository,
                source_path,
                "package com.aqua.aqualight.ui\n"
                f"import {legacy}\n"
                f"import {new_edge}\n"
                "internal class LegacyViewModel\n",
            )
            self._write_baseline(repository, {source_path: [legacy]})

            errors = GUARD.validate_repository(repository)
            self.assertTrue(any(new_edge in error for error in errors), errors)

    def test_removed_legacy_edge_requires_baseline_to_shrink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            source_path = "app/src/main/java/com/aqua/aqualight/ui/LegacyViewModel.kt"
            legacy = "com.aqua.aqualight.data.legacy.LegacyRepository"
            self._write(
                repository,
                source_path,
                "package com.aqua.aqualight.ui\n"
                "import com.aqua.aqualight.application.devices.DeviceRootOperations\n"
                "internal class LegacyViewModel\n",
            )
            self._write_baseline(repository, {source_path: [legacy]})

            errors = GUARD.validate_repository(repository)
            self.assertTrue(any("stale UI -> data baseline edge" in error for error in errors), errors)

    def test_comments_and_strings_do_not_create_false_dependencies(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._write(
                repository,
                "app/src/main/java/com/aqua/aqualight/ui/ExampleScreen.kt",
                "package com.aqua.aqualight.ui\n"
                "// com.aqua.aqualight.data.devices.repository.DevicesRepository\n"
                "private const val NOTE = \"com.aqua.aqualight.data.devices\"\n"
                "internal class ExampleScreen\n",
            )

            self.assertEqual([], GUARD.validate_repository(repository))

    def test_navigation_guard_keeps_global_ui_data_guard_wired(self) -> None:
        source = (ROOT / "tools/navigation_guard.py").read_text(encoding="utf-8")
        self.assertIn(
            "from ui_data_layer_isolation_guard import validate_repository as validate_ui_data_isolation",
            source,
        )
        self.assertIn("validate_ui_data_isolation(ROOT)", source)

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

    @staticmethod
    def _write_baseline(repository: Path, edges: dict[str, list[str]]) -> None:
        path = repository / "config/architecture/ui-data-isolation-baseline.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "uiToData": edges,
                },
                indent=2,
                sort_keys=True,
            ),
            encoding="utf-8",
        )


if __name__ == "__main__":
    unittest.main()

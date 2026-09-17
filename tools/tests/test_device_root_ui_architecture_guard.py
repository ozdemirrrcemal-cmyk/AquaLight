import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GUARD_PATH = ROOT / "tools/device_root_ui_architecture_guard.py"
SPEC = importlib.util.spec_from_file_location("device_root_ui_architecture_guard", GUARD_PATH)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class DeviceRootUiArchitectureGuardTest(unittest.TestCase):
    def test_repository_passes(self) -> None:
        result = subprocess.run(
            [sys.executable, str(GUARD_PATH)],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_hard_coded_root_text_is_rejected(self) -> None:
        source = """<?xml version=\"1.0\" encoding=\"utf-8\"?>
<FrameLayout
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:background=\"@color/background_color\">
    <include android:id=\"@+id/appHeader\" layout=\"@layout/layout_aqua_header\" />
    <TextView android:text=\"Cooling\" />
</FrameLayout>
"""

        errors = GUARD.validate_layout_contract(Path("root.xml"), source)

        self.assertTrue(any("String resources" in error for error in errors), errors)

    def test_parallel_toolbar_is_rejected(self) -> None:
        source = """<?xml version=\"1.0\" encoding=\"utf-8\"?>
<FrameLayout
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:background=\"@color/background_color\">
    <include android:id=\"@+id/appHeader\" layout=\"@layout/layout_aqua_header\" />
    <androidx.appcompat.widget.Toolbar />
</FrameLayout>
"""

        errors = GUARD.validate_layout_contract(Path("root.xml"), source)

        self.assertTrue(any("parallel toolbar" in error for error in errors), errors)

    def test_shell_owned_root_must_not_paint_a_duplicate_background(self) -> None:
        source = """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:background="@color/background_color">
    <include android:id="@+id/appHeader" layout="@layout/layout_aqua_header" />
</FrameLayout>
"""

        errors = GUARD.validate_layout_contract(
            Path("root.xml"),
            source,
            background_owned_by_shell=True,
        )

        self.assertTrue(any("duplicate surface" in error for error in errors), errors)

    def test_timer_entry_surface_requires_compose_body(self) -> None:
        layout = """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
    <include android:id="@+id/appHeader" layout="@layout/layout_aqua_header" />
    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" />
</FrameLayout>
"""

        errors = GUARD.validate_timer_control_surface(
            layout,
            "class DeviceTimerRootFragment",
            'data class DeviceTimerRootUiState(val title: String = "")',
        )

        self.assertTrue(any("one ComposeView body" in error for error in errors), errors)

    def test_timer_entry_surface_rejects_direct_runtime_imports(self) -> None:
        layout = """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
    <include android:id="@+id/appHeader" layout="@layout/layout_aqua_header" />
    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/timerDashboardCompose"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
"""
        fragment = (
            "import com.aqua.aqualight.data.devices.runtime.modules.timer."
            "DeviceTimerRuntimeRepository\n"
        )

        errors = GUARD.validate_timer_control_surface(
            layout,
            fragment,
            "class DeviceTimerRootViewModel",
        )

        self.assertTrue(
            any("bypasses application boundaries" in error for error in errors),
            errors,
        )

    def test_timer_power_cannot_navigate_to_the_channel_surface(self) -> None:
        fragment = "onPowerClick = { slotId -> openChannel(slotId, true) }"

        errors = GUARD.validate_timer_control_surface(
            "<FrameLayout />",
            fragment,
            "class DeviceTimerRootViewModel",
        )

        self.assertTrue(
            any("direct persistent manual command" in error for error in errors),
            errors,
        )

    def test_cooling_code_outside_presentation_root_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            legacy_file = repository_root / GUARD.COOLING_UI_ROOT / "automatic/Legacy.kt"
            legacy_file.parent.mkdir(parents=True)
            legacy_file.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_cooling_feature_boundaries(repository_root)

        self.assertTrue(
            any("must live below" in error for error in errors),
            errors,
        )

    def test_cooling_package_must_match_presentation_path(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            root_file = (
                repository_root
                / GUARD.COOLING_PRESENTATION_ROOT
                / "root/DeviceCoolingRootFragment.kt"
            )
            root_file.parent.mkdir(parents=True)
            root_file.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.cooling.root\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_cooling_feature_boundaries(repository_root)

        self.assertTrue(
            any("Package must match" in error for error in errors),
            errors,
        )

    def test_system_status_is_a_known_cooling_presentation_area(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            status_file = (
                repository_root
                / GUARD.COOLING_PRESENTATION_ROOT
                / "status/DeviceCoolingSystemStatusScreen.kt"
            )
            status_file.parent.mkdir(parents=True)
            status_file.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_cooling_feature_boundaries(repository_root)

        self.assertFalse(any("Unknown Cooling presentation area" in error for error in errors))

    def test_cooling_sibling_areas_must_not_depend_on_root_package(self) -> None:
        for area in ("common", "dashboard", "manual"):
            with self.subTest(area=area), tempfile.TemporaryDirectory() as temporary_directory:
                repository_root = Path(temporary_directory)
                source_file = (
                    repository_root
                    / GUARD.COOLING_PRESENTATION_ROOT
                    / area
                    / "RootDependent.kt"
                )
                source_file.parent.mkdir(parents=True)
                source_file.write_text(
                    "package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation."
                    f"{area}\n\n"
                    "import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation."
                    "root.DeviceCoolingRootUiState\n",
                    encoding="utf-8",
                )

                errors = GUARD.validate_cooling_feature_boundaries(repository_root)

            self.assertTrue(
                any("instead of depending on presentation.root" in error for error in errors),
                errors,
            )

    def test_dosing_operational_error_resource_cannot_bypass_central_resolver(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            resolver = repository_root / GUARD.DOSING_COMMERCIAL_ERROR_RESOLVER
            resolver.parent.mkdir(parents=True)
            resolver.write_text(
                "internal object DeviceDosingCommercialErrorResolver\n",
                encoding="utf-8",
            )
            bypass = (
                repository_root
                / GUARD.DOSING_UI_ROOT
                / "channel/plan/DecentralizedDosingError.kt"
            )
            bypass.parent.mkdir(parents=True)
            bypass.write_text(
                "private val error = R.string.device_dosing_plan_unavailable\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_dosing_commercial_error_boundaries(repository_root)

        self.assertTrue(
            any("DeviceDosingCommercialErrorResolver" in error for error in errors),
            errors,
        )

    def test_dosing_form_validation_copy_may_remain_local(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            resolver = repository_root / GUARD.DOSING_COMMERCIAL_ERROR_RESOLVER
            resolver.parent.mkdir(parents=True)
            resolver.write_text(
                "internal object DeviceDosingCommercialErrorResolver\n",
                encoding="utf-8",
            )
            validation = (
                repository_root
                / GUARD.DOSING_UI_ROOT
                / "channel/calibration/DosingCalibrationPresentation.kt"
            )
            validation.parent.mkdir(parents=True)
            validation.write_text(
                "private val error = R.string.device_dosing_calibration_invalid_measurement\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_dosing_commercial_error_boundaries(repository_root)

        self.assertFalse(
            any("operational error resources" in error for error in errors),
            errors,
        )

    def test_legacy_light_menu_package_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            legacy_file = (
                repository_root
                / GUARD.LIGHT_PRESENTATION_ROOT
                / "menu/LegacyLightMenuFragment.kt"
            )
            legacy_file.parent.mkdir(parents=True)
            legacy_file.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("legacy Light package must not return" in error for error in errors),
            errors,
        )

    def test_parallel_light_state_owner_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            parallel_owner = (
                repository_root
                / GUARD.LIGHT_DATA_ROOT
                / "system/ParallelOwner.kt"
            )
            parallel_owner.parent.mkdir(parents=True)
            parallel_owner.write_text(
                "package com.aqua.aqualight.data.devices.light.system\n\n"
                "private val owner = DeviceLightRuntimeStateOwner()\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("construct exactly one state owner only" in error for error in errors),
            errors,
        )

    def test_duplicate_light_state_owner_in_composition_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            provider = repository_root / GUARD.LIGHT_RUNTIME_PROVIDER
            provider.parent.mkdir(parents=True)
            provider.write_text(
                "private val first = DeviceLightRuntimeStateOwner()\n"
                "private val second = DeviceLightRuntimeStateOwner()\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("construct exactly one state owner only" in error for error in errors),
            errors,
        )

    def test_light_suppression_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            source_file = (
                repository_root
                / GUARD.LIGHT_PRESENTATION_ROOT
                / "manual/Suppressed.kt"
            )
            source_file.parent.mkdir(parents=True)
            source_file.write_text(
                '@file:Suppress("MagicNumber")\n\n'
                "package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("instead of suppressing them" in error for error in errors),
            errors,
        )

    def test_light_manual_slider_cannot_use_blocking_operation_loading(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            state_file = (
                repository_root
                / GUARD.LIGHT_PRESENTATION_ROOT
                / "manual/DeviceLightManualControlUiState.kt"
            )
            state_file.parent.mkdir(parents=True)
            state_file.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual\n\n"
                "data class DeviceLightManualControlUiState(\n"
                "    override val initialLoading: Boolean,\n"
                "    override val operationInProgress: Boolean\n"
                ") : DeviceLightOperationLoadingState\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("slider commands must remain non-blocking" in error for error in errors),
            errors,
        )

    def test_light_failure_copy_cannot_be_mapped_outside_central_resolver(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            decentralized_mapper = (
                repository_root
                / GUARD.LIGHT_PRESENTATION_ROOT
                / "manual/DecentralizedErrorMapper.kt"
            )
            decentralized_mapper.parent.mkdir(parents=True)
            decentralized_mapper.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual\n\n"
                "private fun DeviceLightManualFailure.messageRes(): Int = 0\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("DeviceLightCommercialErrorResolver" in error for error in errors),
            errors,
        )

    def test_light_operational_error_resource_cannot_bypass_central_resolver(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            bypass = (
                repository_root
                / GUARD.LIGHT_PRESENTATION_ROOT
                / "automatic/programs/ResolverBypass.kt"
            )
            bypass.parent.mkdir(parents=True)
            bypass.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation."
                "automatic.programs\n\n"
                "private val error = R.string.device_light_auto_operation_error\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("operational error resources" in error for error in errors),
            errors,
        )

    def test_light_strings_cannot_be_split_across_resource_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            split_resource = (
                repository_root
                / "app/src/main/res/values/device_light_auto_strings.xml"
            )
            split_resource.parent.mkdir(parents=True)
            split_resource.write_text(
                "<resources>\n"
                "    <string name=\"device_light_auto_error\">Error</string>\n"
                "</resources>\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("canonical device_light_strings.xml" in error for error in errors),
            errors,
        )

    def test_automatic_presentation_package_cycle_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            editor = (
                repository_root
                / GUARD.LIGHT_PRESENTATION_ROOT
                / "automatic/editor/Editor.kt"
            )
            preset = (
                repository_root
                / GUARD.LIGHT_PRESENTATION_ROOT
                / "automatic/preset/Preset.kt"
            )
            editor.parent.mkdir(parents=True)
            preset.parent.mkdir(parents=True)
            editor.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation."
                "automatic.editor\n\n"
                "import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation."
                "automatic.preset.Preset\n",
                encoding="utf-8",
            )
            preset.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation."
                "automatic.preset\n\n"
                "import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation."
                "automatic.editor.Editor\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("dependency cycle" in error for error in errors),
            errors,
        )

    def test_automatic_editor_cannot_import_preset_directly(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            editor = (
                repository_root
                / GUARD.LIGHT_PRESENTATION_ROOT
                / "automatic/editor/Editor.kt"
            )
            editor.parent.mkdir(parents=True)
            editor.write_text(
                "package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation."
                "automatic.editor\n\n"
                "import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation."
                "automatic.preset.Preset\n",
                encoding="utf-8",
            )

            errors = GUARD.validate_light_feature_boundaries(repository_root)

        self.assertTrue(
            any("shared parent contract" in error for error in errors),
            errors,
        )


if __name__ == "__main__":
    unittest.main()

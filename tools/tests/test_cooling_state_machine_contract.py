from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
COOLING = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation"
)


class CoolingStateMachineContractTest(unittest.TestCase):

    def test_shared_vocabulary_distinguishes_empty_unavailable_and_unsupported(self) -> None:
        text = (COOLING / "common/CoolingDataState.kt").read_text(encoding="utf-8")
        for token in (
            "data object Initial",
            "data object Loading",
            "data class Content",
            "data class Empty",
            "data object Unavailable",
            "data object Unsupported",
            "REFRESHING",
            "STALE",
            "data object Saving",
            "data object Saved",
            "data object ValidationError",
            "data class OperationError",
        ):
            self.assertIn(token, text)

    def test_root_models_independent_data_sections_and_write_lifecycle(self) -> None:
        text = (COOLING / "root/DeviceCoolingRootUiState.kt").read_text(encoding="utf-8")
        for token in (
            "controlState:",
            "controlMutationState:",
            "automaticSummaryState:",
            "historyState:",
            "controlWriteEnabled",
        ):
            self.assertIn(token, text)

    def test_history_does_not_collapse_unsupported_or_unavailable_into_content(self) -> None:
        text = (COOLING / "history/DeviceCoolingTemperatureHistoryViewModel.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "DeviceCoolingTemperatureHistoryLoadResult.Unsupported -> CoolingDataState.Unsupported",
            text,
        )
        self.assertIn("DeviceCoolingTemperatureHistoryLoadResult.Unavailable ->", text)
        self.assertIn("CoolingDataState.Empty", text)
        self.assertIn("DeviceCoolingTemperatureHistoryLoadState.REFRESHING", text)

    def test_automatic_has_independent_read_and_mutation_state(self) -> None:
        text = (COOLING / "automatic/DeviceCoolingAutomaticUiState.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("dataState:", text)
        self.assertIn("mutationState:", text)
        self.assertIn("DeviceCoolingAutomaticLoadState.REFRESHING", text)
        self.assertIn("CoolingMutationState.ValidationError", text)

    def test_program_empty_is_authoritative_not_a_read_failure(self) -> None:
        text = (COOLING / "program/DeviceCoolingProgramSettingsViewModel.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("CoolingDataState.Empty<CoolingProgramSnapshot", text)
        self.assertIn("CoolingMutationState.ValidationError", text)
        self.assertIn("CoolingDataState.Unavailable", text)
        self.assertIn("CoolingDataState.Unsupported", text)

    def test_history_keeps_terminal_read_state_presentation(self) -> None:
        history = (COOLING / "history/CoolingTemperatureHistoryScreen.kt").read_text(
            encoding="utf-8"
        )
        for token in ("Unsupported", "Unavailable", "CoolingStateMessageCard"):
            self.assertIn(token, history)

    def test_mode_editors_rely_on_root_availability_gate(self) -> None:
        root = (COOLING / "root/DeviceCoolingRootFragment.kt").read_text(
            encoding="utf-8"
        )
        for route in ("openAutomaticSettings", "openManualSettings", "openProgramSettings"):
            self.assertRegex(
                root,
                re.compile(
                    rf"private fun {route}\(\) \{{\s*"
                    r"if \(!viewModel\.uiState\.value\.contentEnabled\) return"
                ),
            )

        editor_sources = (
            COOLING / "automatic/DeviceCoolingAutomaticSettingsFragment.kt",
            COOLING / "manual/DeviceCoolingManualSettingsScreen.kt",
            COOLING / "program/DeviceCoolingProgramSettingsFragment.kt",
        )
        for source in editor_sources:
            with self.subTest(source=source.name):
                self.assertNotIn(
                    "CoolingStateMessageCard",
                    source.read_text(encoding="utf-8"),
                )

        self.assertFalse(
            (COOLING / "automatic/DeviceCoolingAutomaticStateScreen.kt").exists()
        )
        self.assertFalse(
            (COOLING / "program/DeviceCoolingProgramAvailabilityScreen.kt").exists()
        )

    def test_mode_editor_availability_copy_and_style_are_removed(self) -> None:
        forbidden_copy = (
            "device_cooling_manual_loading_",
            "device_cooling_manual_unsupported_",
            "device_cooling_manual_unavailable_",
            "device_cooling_manual_invalid_",
            "device_cooling_automatic_loading_",
            "device_cooling_automatic_refreshing_",
            "device_cooling_automatic_stale_",
            "device_cooling_automatic_read_only_",
            "device_cooling_automatic_unsupported_",
            "device_cooling_automatic_unavailable_",
            "device_cooling_automatic_invalid_",
            "device_cooling_program_loading_",
            "device_cooling_program_unavailable_",
            "device_cooling_program_unsupported_",
            "device_cooling_program_load_failed_",
            "device_cooling_program_retry",
        )
        resources = ROOT / "app/src/main/res"
        for locale in ("values", "values-tr"):
            locale_copy = "\n".join(
                path.read_text(encoding="utf-8")
                for path in sorted((resources / locale).glob("*.xml"))
            )
            with self.subTest(locale=locale):
                for token in forbidden_copy:
                    self.assertNotIn(token, locale_copy)

                self.assertFalse(
                    (resources / locale / "device_cooling_program_state_strings.xml").exists()
                )

        automatic_style = (
            COOLING / "automatic/AquaCoolingAutomaticStyle.kt"
        ).read_text(encoding="utf-8")
        for token in (
            "messageCardMinimumHeight",
            "messageGap",
            "retryShape",
            "retryHorizontalPadding",
            "retryVerticalPadding",
            "retryBackground",
        ):
            self.assertNotIn(token, automatic_style)

    def test_mode_editor_mutation_failures_remain_visible_without_cards(self) -> None:
        automatic = (
            COOLING / "automatic/CoolingAutomaticSettingsScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("state.saveFailure?.let", automatic)
        self.assertIn("failure.toCommercialCoolingError()", automatic)

        manual = (COOLING / "manual/DeviceCoolingManualSettingsScreen.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("is CoolingMutationState.OperationError", manual)
        self.assertIn("toCommercialCoolingError().messageRes", manual)

        program = (
            COOLING / "program/DeviceCoolingProgramSettingsFragment.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("saveState.isFailure", program)
        self.assertIn("showSnackBar", program)

    def test_blocking_cooling_mutations_and_cold_root_use_central_loading(self) -> None:
        fragments = (
            "root/DeviceCoolingRootFragment.kt",
            "automatic/DeviceCoolingAutomaticSettingsFragment.kt",
            "program/DeviceCoolingProgramSettingsFragment.kt",
        )
        for relative_path in fragments:
            with self.subTest(fragment=relative_path):
                text = (COOLING / relative_path).read_text(encoding="utf-8")
                self.assertIn("setFragmentGlobalLoading", text)

        for relative_path in (
            "automatic/DeviceCoolingAutomaticUiState.kt",
            "program/DeviceCoolingProgramSettingsViewModel.kt",
        ):
            with self.subTest(load_state=relative_path):
                text = (COOLING / relative_path).read_text(encoding="utf-8")
                self.assertIn("dataState == CoolingDataState.Loading", text)

        root_state = (COOLING / "root/DeviceCoolingRootUiState.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("surfacePreparationPending", root_state)
        self.assertIn("showGlobalLoading", root_state)

    def test_manual_slider_commits_on_gesture_end_without_global_loading(self) -> None:
        fragment = (COOLING / "manual/DeviceCoolingManualSettingsFragment.kt").read_text(
            encoding="utf-8"
        )
        slider = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/ui/common/cooling/"
            "AquaCoolingFanPercentSlider.kt"
        ).read_text(encoding="utf-8")

        self.assertNotIn("setFragmentGlobalLoading", fragment)
        self.assertIn("onTargetPercentChangeFinished", fragment)
        self.assertIn("onDragEnd = interaction::finishChange", slider)


if __name__ == "__main__":
    unittest.main()

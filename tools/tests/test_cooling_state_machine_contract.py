from pathlib import Path
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

    def test_terminal_read_states_have_user_visible_presentations(self) -> None:
        history = (COOLING / "history/CoolingTemperatureHistoryScreen.kt").read_text(
            encoding="utf-8"
        )
        automatic = (COOLING / "automatic/DeviceCoolingAutomaticStateScreen.kt").read_text(
            encoding="utf-8"
        )
        for token in ("Unsupported", "Unavailable", "CoolingStateMessageCard"):
            self.assertIn(token, history)
            self.assertIn(token, automatic)

    def test_cooling_mutations_and_cold_root_use_central_loading(self) -> None:
        fragments = (
            "root/DeviceCoolingRootFragment.kt",
            "automatic/DeviceCoolingAutomaticSettingsFragment.kt",
            "manual/DeviceCoolingManualSettingsFragment.kt",
            "program/DeviceCoolingProgramSettingsFragment.kt",
        )
        for relative_path in fragments:
            with self.subTest(fragment=relative_path):
                text = (COOLING / relative_path).read_text(encoding="utf-8")
                self.assertIn("setFragmentGlobalLoading", text)

        root_state = (COOLING / "root/DeviceCoolingRootUiState.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("surfacePreparationPending", root_state)
        self.assertIn("showGlobalLoading", root_state)


if __name__ == "__main__":
    unittest.main()

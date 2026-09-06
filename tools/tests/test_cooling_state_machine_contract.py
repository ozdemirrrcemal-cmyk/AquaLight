from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
COOLING = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation"
)
COOLING_APPLICATION = ROOT / "app/src/main/java/com/aqua/aqualight/application/devices/cooling"


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

    def test_automatic_temperature_validation_stays_in_application_boundary(self) -> None:
        validation = (
            COOLING_APPLICATION / "DeviceCoolingAutomaticTemperatureValidation.kt"
        ).read_text(encoding="utf-8")
        transitions = (
            COOLING / "automatic/DeviceCoolingAutomaticDraftTransitions.kt"
        ).read_text(encoding="utf-8")

        for token in (
            "object DeviceCoolingAutomaticTemperatureValidation",
            "fun isValidStartTemperature(",
            "fun isValidMaximumSpeedTemperature(",
            "policy.minimumGapC - TEMPERATURE_EPSILON",
        ):
            self.assertIn(token, validation)

        for token in (
            "DeviceCoolingAutomaticTemperatureValidation.isValidStartTemperature(",
            "DeviceCoolingAutomaticTemperatureValidation.isValidMaximumSpeedTemperature(",
        ):
            self.assertIn(token, transitions)

        for forbidden in (
            "private fun Double.isValidStart",
            "private fun Double.isValidMaximum",
            "policy.minimumGapC",
            "TEMPERATURE_EPSILON",
        ):
            self.assertNotIn(forbidden, transitions)

    def test_program_empty_is_authoritative_not_a_read_failure(self) -> None:
        text = (COOLING / "program/DeviceCoolingProgramSettingsViewModel.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("CoolingDataState.Empty<CoolingProgramSnapshot", text)
        self.assertIn("CoolingMutationState.ValidationError", text)
        self.assertIn("CoolingDataState.Unavailable", text)
        self.assertIn("CoolingDataState.Unsupported", text)

    def test_program_time_picker_uses_application_owned_selectable_times(self) -> None:
        selections = (
            COOLING_APPLICATION / "program/CoolingProgramTimeSelections.kt"
        ).read_text(encoding="utf-8")
        schedule = (
            COOLING_APPLICATION / "program/CoolingProgramSchedule.kt"
        ).read_text(encoding="utf-8")
        view_model = (
            COOLING / "program/DeviceCoolingProgramSettingsViewModel.kt"
        ).read_text(encoding="utf-8")
        fragment = (
            COOLING / "program/DeviceCoolingProgramSettingsFragment.kt"
        ).read_text(encoding="utf-8")
        picker = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/ui/common/bottomsheet/"
            "AquaTimePickerBottomSheet.kt"
        ).read_text(encoding="utf-8")

        for token in (
            "object CoolingProgramTimeSelections",
            "CoolingProgramSchedule.updateStartTime(",
            "CoolingProgramSchedule.updateEndTime(",
        ):
            self.assertIn(token, selections)
        self.assertNotIn("android.", selections)
        self.assertNotIn("com.aqua.aqualight.ui", selections)
        self.assertIn("maxOf(current.endMinutes", schedule)
        self.assertIn("CoolingProgramTimeSelections.forStartTime(", view_model)
        self.assertIn("CoolingProgramTimeSelections.forEndTime(", view_model)
        self.assertIn("selectableMinutesOfDay = selection.selectableMinutesOfDay", fragment)
        self.assertNotIn("devices.detail.cooling", picker)

    def test_program_timeline_uses_true_quarter_day_ticks(self) -> None:
        primitives = (
            COOLING / "program/CoolingProgramPrimitives.kt"
        ).read_text(encoding="utf-8")
        for token in (
            "TIMELINE_06_MINUTES",
            "TIMELINE_12_MINUTES",
            "TIMELINE_18_MINUTES",
            "ticks[index].minutesOfDay.toFloat() / MINUTES_PER_DAY",
        ):
            self.assertIn(token, primitives)

        for locale in ("values", "values-tr"):
            strings = (
                ROOT / f"app/src/main/res/{locale}/device_cooling_strings.xml"
            ).read_text(encoding="utf-8")
            for valid_axis in ("axis_00", "axis_06", "axis_12", "axis_18", "axis_24"):
                self.assertIn(f"device_cooling_program_{valid_axis}", strings)
            for stale_axis in ("axis_08", "axis_14", "axis_20"):
                self.assertNotIn(f"device_cooling_program_{stale_axis}", strings)

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

    def test_dashboard_temperature_metrics_use_compact_unclipped_copy(self) -> None:
        dashboard = COOLING / "dashboard"
        card = (dashboard / "CoolingTemperatureCard.kt").read_text(encoding="utf-8")
        primitives = (dashboard / "CoolingDashboardPrimitives.kt").read_text(
            encoding="utf-8"
        )
        style = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/ui/common/cooling/"
            "AquaCoolingComposeStyle.kt"
        ).read_text(encoding="utf-8")

        self.assertEqual(2, card.count("coolingTemperatureMetricText("))
        self.assertIn("device_cooling_temperature_metric_value_format", primitives)
        self.assertIn("val temperatureMetricWidth = 76.dp", style)
        self.assertIn("val temperatureChartMetricGap = 4.dp", style)
        self.assertIn("val temperatureMetricContentGap = 8.dp", style)

        for locale in ("values", "values-tr"):
            strings = (
                ROOT / f"app/src/main/res/{locale}/device_cooling_strings.xml"
            ).read_text(encoding="utf-8")
            self.assertIn(
                '<string name="device_cooling_temperature_metric_value_format">'
                "%1$.1f°</string>",
                strings,
            )

    def test_dashboard_mode_summary_is_removed_and_options_fill_its_space(self) -> None:
        mode_card = (
            COOLING / "dashboard/CoolingFanControlCards.kt"
        ).read_text(encoding="utf-8")
        style = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/ui/common/cooling/"
            "AquaCoolingComposeStyle.kt"
        ).read_text(encoding="utf-8")

        for token in (
            "CoolingActiveModeSummary",
            "modeStatusDotSize",
            "modeStatusGap",
            "modeStatusTopPadding",
        ):
            self.assertNotIn(token, mode_card + style)
        self.assertIn("val optionVerticalPadding = 8.dp", style)
        self.assertIn("val radioSize = 21.dp", style)

        for locale in ("values", "values-tr"):
            strings = (
                ROOT / f"app/src/main/res/{locale}/device_cooling_strings.xml"
            ).read_text(encoding="utf-8")
            self.assertNotIn('<string name="device_cooling_mode_active">', strings)
            self.assertNotIn("device_cooling_inline_separator", strings)

    def test_runtime_fan_percent_remains_continuous_until_presentation(self) -> None:
        application_snapshot = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/application/devices/cooling/control/"
            "DeviceCoolingControlSnapshot.kt"
        ).read_text(encoding="utf-8")
        mapper = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/data/devices/cooling/control/"
            "DeviceCoolingControlSnapshotMapper.kt"
        ).read_text(encoding="utf-8")
        gauge = (COOLING / "dashboard/CoolingFanControlCards.kt").read_text(
            encoding="utf-8"
        )

        self.assertIn("val actualFanPercent: Double?", application_snapshot)
        self.assertIn("val targetFanPercent: Double?", application_snapshot)
        self.assertIn("actualFanPercent = live?.fan?.outputPercent", mapper)
        self.assertIn(
            "targetFanPercent = live?.fan?.targetPercent ?: status.control.targetPercent",
            mapper,
        )
        self.assertNotIn("outputPercent?.toWritableIntPercentOrNull()", mapper)
        self.assertIn("toCoolingDisplayPercentOrNull()", gauge)

    def test_automatic_copy_matches_firmware_threshold_and_silent_scope(self) -> None:
        application_policy = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/application/devices/cooling/"
            "DeviceCoolingAutomaticSettingsOperations.kt"
        ).read_text(encoding="utf-8")
        adapter = (
            ROOT
            / "app/src/main/java/com/aqua/aqualight/data/devices/cooling/"
            "DefaultDeviceCoolingAutomaticSettingsOperations.kt"
        ).read_text(encoding="utf-8")

        self.assertIn("val hysteresisC: Double", application_policy)
        self.assertIn("hysteresisC = automaticPolicy.hysteresisC", adapter)

        english = (
            ROOT / "app/src/main/res/values/device_cooling_strings.xml"
        ).read_text(encoding="utf-8")
        turkish = (
            ROOT / "app/src/main/res/values-tr/device_cooling_strings.xml"
        ).read_text(encoding="utf-8")
        self.assertIn("exceeds this threshold", english)
        self.assertIn("Automatic and Program modes", english)
        self.assertIn("eşiği aşınca", turkish)
        self.assertIn("Otomatik ve Program modlarında", turkish)

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

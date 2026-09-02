package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingRootStateMachineTest {

    @Test
    fun transientControlFailureKeepsLastAuthoritativeValueStale() {
        val previous = availableControl().toRootControlState(CoolingDataState.Initial)

        val next = DeviceCoolingControlResult.Failed(
            DeviceCoolingControlFailure.Unavailable
        ).toRootControlState(previous)

        val content = next as CoolingDataState.Content<
            CoolingControlPresentation,
            DeviceCoolingControlFailure
            >
        assertEquals(CoolingDataFreshness.STALE, content.freshness)
        assertEquals(DeviceCoolingControlFailure.Unavailable, content.refreshFailure)
        assertEquals(DeviceCoolingControlMode.AUTOMATIC, content.value.selectedMode)
    }

    @Test
    fun unsupportedControlIsTerminalAndNeverMasqueradesAsStaleContent() {
        val previous = availableControl().toRootControlState(CoolingDataState.Initial)

        val next = DeviceCoolingControlResult.Failed(
            DeviceCoolingControlFailure.Unsupported
        ).toRootControlState(previous)

        assertEquals(CoolingDataState.Unsupported, next)
    }

    @Test
    fun partialAutomaticSnapshotKeepsLastValidatedPairStale() {
        val previous: CoolingDataState<
            CoolingAutomaticSummaryPresentation,
            DeviceCoolingAutomaticFailure
            > = CoolingDataState.Content(
            CoolingAutomaticSummaryPresentation(25.0, 27.0)
        )
        val partial = DeviceCoolingAutomaticSettingsSnapshot(
            available = true,
            loaded = true,
            editable = false,
            startTemperatureC = 26.0,
            maximumSpeedTemperatureC = null
        )

        val next = partial.toRootAutomaticState(previous)

        val content = next as CoolingDataState.Content<
            CoolingAutomaticSummaryPresentation,
            DeviceCoolingAutomaticFailure
            >
        assertEquals(CoolingDataFreshness.STALE, content.freshness)
        assertEquals(DeviceCoolingAutomaticFailure.InvalidConfiguration, content.refreshFailure)
        assertEquals(25.0, content.value.startTemperatureC, 0.0)
        assertEquals(27.0, content.value.maximumSpeedTemperatureC, 0.0)
    }

    @Test
    fun emptyHistoryOverviewIsEmptyNotUnavailable() {
        val result = DeviceCoolingTemperatureHistoryLoadResult.Loaded(
            DeviceCoolingTemperatureHistorySnapshot(
                range = DeviceCoolingTemperatureHistoryRange.HOURS_24,
                generatedAtEpochMillis = 1L,
                minimumTemperatureC = null,
                averageTemperatureC = null,
                maximumTemperatureC = null,
                points = emptyList(),
                dailySummaries = emptyList()
            )
        )

        assertTrue(result.toRootHistoryState() is CoolingDataState.Empty)
    }

    @Test
    fun controlWritesAreDisabledWhileMutationIsSaving() {
        val control = availableControl().toRootControlState(CoolingDataState.Initial)
        val state = DeviceCoolingRootUiState(
            contentEnabled = true,
            controlState = control,
            controlMutationState = CoolingMutationState.Saving
        )

        assertFalse(state.controlWriteEnabled)
        assertFalse(state.modeSelectionWritable)
    }

    private companion object {
        fun availableControl(): DeviceCoolingControlResult = DeviceCoolingControlResult.Available(
            DeviceCoolingControlSnapshot(
                mode = DeviceCoolingControlMode.AUTOMATIC,
                manualFanPercent = 40,
                actualFanPercent = 35,
                tankTemperatureC = 25.4,
                capabilities = DeviceCoolingControlCapabilities(
                    supportedModes = setOf(
                        DeviceCoolingControlMode.AUTOMATIC,
                        DeviceCoolingControlMode.MANUAL
                    ),
                    modeSelectionWritable = true,
                    manualFan = DeviceCoolingManualFanCapabilities(
                        minimumPercent = 0,
                        maximumPercent = 100,
                        stepPercent = 1,
                        writable = true
                    )
                )
            )
        )
    }
}

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingPwmOutputHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingProgramRuntimeSnapshot
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
        assertTrue(state.showGlobalLoading)
    }

    @Test
    fun `cold surface preparation blocks globally until authority is available`() {
        val cold = DeviceCoolingRootUiState(surfacePreparationPending = true)
        val warm = DeviceCoolingRootUiState(
            surfacePreparationPending = true,
            controlState = availableControl().toRootControlState(CoolingDataState.Initial)
        )

        assertTrue(cold.showGlobalLoading)
        assertFalse(warm.showGlobalLoading)
    }

    @Test
    fun `dashboard uses firmware alarm count and program status without recomputing`() {
        val base = (availableControl() as DeviceCoolingControlResult.Available).snapshot
        val result = DeviceCoolingControlResult.Available(
            base.copy(
                telemetry = DeviceCoolingTelemetrySnapshot(
                    roomTemperatureC = 24.0,
                    humidityPercent = 50.0,
                    powerWatts = 0.2,
                    estimatedKwhPerDay = 0.0048,
                    fanHealth = DeviceCoolingFanHealth.UNVERIFIED,
                    sensorHealth = DeviceCoolingSensorHealth.WARNING,
                    alarms = listOf(
                        DeviceCoolingAlarmSnapshot(
                            code = DeviceCoolingAlarmCode.AMBIENT_SENSOR_FAULT,
                            severity = DeviceCoolingAlarmSeverity.WARNING,
                            active = true,
                            latched = false
                        )
                    ),
                    activeAlarmCount = 6,
                    highestAlarmSeverity = DeviceCoolingAlarmSeverity.CRITICAL,
                    fan = DeviceCoolingFanTelemetrySnapshot(
                        targetPercent = 40.0,
                        outputPercent = 40.0,
                        rpm = null,
                        rpmAvailable = false,
                        pwmOutputHealth = DeviceCoolingPwmOutputHealth.OK,
                        physicalHealth = DeviceCoolingFanHealth.UNVERIFIED
                    )
                ),
                programRuntime = DeviceCoolingProgramRuntimeSnapshot(
                    persistedRevision = 4L,
                    evaluatedRevision = 4L,
                    slotCount = 3,
                    clockReady = true,
                    currentMinuteOfDay = 600,
                    activeSlotIndex = 1
                )
            )
        )

        val state = result.toRootDashboardOverviewState(CoolingDataState.Initial)
            as CoolingDataState.Content<CoolingDashboardOverviewPresentation, Nothing>

        assertEquals(6, state.value.activeAlarmCount)
        assertEquals(3, state.value.programSlotCount)
        assertEquals(DeviceCoolingAlarmSeverity.CRITICAL, state.value.highestAlarmSeverity)
        assertEquals(CoolingHealthState.READY, state.value.fanOutputHealth)
    }

    private companion object {
        fun availableControl(): DeviceCoolingControlResult = DeviceCoolingControlResult.Available(
            DeviceCoolingControlSnapshot(
                mode = DeviceCoolingControlMode.AUTOMATIC,
                manualFanPercent = 40,
                actualFanPercent = 35.0,
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

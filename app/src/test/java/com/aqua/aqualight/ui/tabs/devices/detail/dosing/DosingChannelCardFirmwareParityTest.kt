package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingDoseProgressVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toDosingChannelCardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingChannelCardFirmwareParityTest {

    @Test
    fun `active firmware day keeps zero of twenty four while execution checkpoint catches up`() {
        val state = snapshot(
            progress = hourlyProgress(
                scheduleState = DeviceDosingScheduleState.ACTIVE,
                executionCurrent = false
            )
        ).toDosingChannelCardUiState().programProgress

        assertTrue(state.scheduledToday)
        assertEquals(24, state.totalOccurrences)
        assertEquals(0, state.completedOccurrences)
        assertEquals(24, state.occurrences.size)
        assertEquals(DosingDoseProgressVisualState.READY, state.visualState)
        assertEquals(24.0, state.scheduledAmountTodayMl, 0.0)
        assertEquals(24.0, state.remainingScheduledTodayMl, 0.0)
        assertEquals(0f, state.completionFraction, 0f)
    }

    @Test
    fun `firmware noSchedule remains no dose today without deleting canonical occurrences`() {
        val state = snapshot(
            progress = hourlyProgress(
                scheduleState = DeviceDosingScheduleState.NO_SCHEDULE,
                executionCurrent = false
            )
        ).toDosingChannelCardUiState().programProgress

        assertFalse(state.scheduledToday)
        assertEquals(24, state.totalOccurrences)
        assertEquals(24, state.occurrences.size)
        assertEquals(DosingDoseProgressVisualState.EMPTY, state.visualState)
    }

    private fun hourlyProgress(
        scheduleState: DeviceDosingScheduleState,
        executionCurrent: Boolean
    ) = DeviceDosingChannelProgress(
        scheduledAmountMicroliters = 24_000L,
        completedAmountMicroliters = 0L,
        remainingAmountMicroliters = 24_000L,
        occurrences = List(24) { index ->
            DeviceDosingOccurrenceProgress(
                index = index,
                eventId = index + 1L,
                programDayOffset = 0,
                timeMillis = index * 60L * 60L * 1_000L,
                amountMicroliters = 1_000L,
                state = DeviceDosingOccurrenceState.PENDING
            )
        },
        scheduleState = scheduleState,
        totalOccurrences = 24,
        completedOccurrences = 0,
        resolvedOccurrences = 0,
        pendingOccurrences = 24,
        runningOccurrences = 0,
        skippedOccurrences = 0,
        uncertainOccurrences = 0,
        completionPercent = 0.0,
        executionCurrent = executionCurrent
    )

    private fun snapshot(progress: DeviceDosingChannelProgress) = DeviceDosingChannelSnapshot(
        deviceUid = "device-1",
        slotId = "dosing:channel1",
        pumpCount = 4,
        channelNumber = 1,
        channelTitle = "Macro nutrients",
        revision = 8L,
        runtimeEnabled = true,
        runtimeReason = DeviceDosingRuntimeReason.NONE,
        deliveryAccountingCertain = true,
        calibrated = true,
        lastCalibratedAtEpochSeconds = 1L,
        scheduling = DeviceDosingSchedulingPolicy(),
        program = DeviceDosingProgram(
            enabled = true,
            weekdays = List(7) { true },
            schedule = DeviceDosingProgramSchedule.Hourly24(
                dailyDoseMicroliters = 24_000L,
                minuteOfHour = 0
            ),
            missedDoseRecoveryEnabled = false
        ),
        progress = progress,
        reservoir = DeviceDosingReservoirSnapshot(),
        activeRun = DeviceDosingActiveRun(),
        controls = DeviceDosingChannelControls()
    )
}

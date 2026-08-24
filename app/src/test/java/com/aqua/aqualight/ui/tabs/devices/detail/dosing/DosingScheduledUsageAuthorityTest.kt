package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toDosingChannelCardUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class DosingScheduledUsageAuthorityTest {

    @Test
    fun `scheduled delivered total comes from firmware usage instead of progress projection`() {
        val state = snapshot(
            usage = DeviceDosingDailyUsageSnapshot(
                valid = true,
                scheduledDeliveredMicroliters = 380L,
                totalDeliveredMicroliters = 380L
            )
        ).toDosingChannelCardUiState()

        assertEquals(0.38, state.programProgress.scheduledDeliveredTodayMl, 0.0)
        assertEquals(0f, state.programProgress.completionFraction, 0f)
        assertEquals(0, state.programProgress.completedOccurrences)
    }

    @Test
    fun `invalid firmware usage date never exposes stale scheduled delivery`() {
        val state = snapshot(
            usage = DeviceDosingDailyUsageSnapshot(
                valid = false,
                scheduledDeliveredMicroliters = 380L,
                totalDeliveredMicroliters = 380L
            )
        ).toDosingChannelCardUiState()

        assertEquals(0.0, state.programProgress.scheduledDeliveredTodayMl, 0.0)
    }

    private fun snapshot(usage: DeviceDosingDailyUsageSnapshot) = DeviceDosingChannelSnapshot(
        deviceUid = "device-1",
        slotId = "dosing:channel2",
        pumpCount = 4,
        channelNumber = 2,
        channelTitle = "Potassium",
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
            schedule = DeviceDosingProgramSchedule.Single(
                dailyDoseMicroliters = 1_000L,
                startTimeMillis = 36_000_000L
            ),
            missedDoseRecoveryEnabled = true
        ),
        progress = DeviceDosingChannelProgress(
            scheduledAmountMicroliters = 1_000L,
            completedAmountMicroliters = 0L,
            occurrences = listOf(
                DeviceDosingOccurrenceProgress(
                    index = 0,
                    eventId = 1L,
                    programDayOffset = 0,
                    timeMillis = 36_000_000L,
                    amountMicroliters = 1_000L,
                    state = DeviceDosingOccurrenceState.SKIPPED
                )
            ),
            executionCurrent = true
        ),
        reservoir = DeviceDosingReservoirSnapshot(),
        activeRun = DeviceDosingActiveRun(),
        controls = DeviceDosingChannelControls(),
        usageToday = usage
    )
}

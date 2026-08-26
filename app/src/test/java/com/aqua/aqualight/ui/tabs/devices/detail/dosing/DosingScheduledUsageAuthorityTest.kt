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
    fun `program progress uses completed amount when calendar day usage is higher`() {
        val state = snapshot(
            completedAmountMicroliters = PROGRAM_TARGET_MICROLITERS,
            occurrenceState = DeviceDosingOccurrenceState.COMPLETED,
            usage = DeviceDosingDailyUsageSnapshot(
                valid = true,
                scheduledDeliveredMicroliters = HIGHER_CALENDAR_DAY_USAGE_MICROLITERS,
                totalDeliveredMicroliters = HIGHER_CALENDAR_DAY_USAGE_MICROLITERS
            )
        ).toDosingChannelCardUiState()

        assertEquals(5.0, state.programProgress.scheduledDeliveredTodayMl, 0.0)
        assertEquals(1f, state.programProgress.completionFraction, 0f)
        assertEquals(1, state.programProgress.completedOccurrences)
    }

    @Test
    fun `skipped occurrence does not advance program progress from physical daily usage`() {
        val state = snapshot(
            completedAmountMicroliters = 0L,
            occurrenceState = DeviceDosingOccurrenceState.SKIPPED,
            usage = DeviceDosingDailyUsageSnapshot(
                valid = true,
                scheduledDeliveredMicroliters = PARTIAL_PHYSICAL_USAGE_MICROLITERS,
                totalDeliveredMicroliters = PARTIAL_PHYSICAL_USAGE_MICROLITERS
            )
        ).toDosingChannelCardUiState()

        assertEquals(0.0, state.programProgress.scheduledDeliveredTodayMl, 0.0)
        assertEquals(0f, state.programProgress.completionFraction, 0f)
        assertEquals(0, state.programProgress.completedOccurrences)
    }

    private fun snapshot(
        completedAmountMicroliters: Long,
        occurrenceState: DeviceDosingOccurrenceState,
        usage: DeviceDosingDailyUsageSnapshot
    ) = DeviceDosingChannelSnapshot(
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
                dailyDoseMicroliters = PROGRAM_TARGET_MICROLITERS,
                startTimeMillis = 36_000_000L
            ),
            missedDoseRecoveryEnabled = true
        ),
        progress = DeviceDosingChannelProgress(
            scheduledAmountMicroliters = PROGRAM_TARGET_MICROLITERS,
            completedAmountMicroliters = completedAmountMicroliters,
            occurrences = listOf(
                DeviceDosingOccurrenceProgress(
                    index = 0,
                    eventId = 1L,
                    programDayOffset = 0,
                    timeMillis = 36_000_000L,
                    amountMicroliters = PROGRAM_TARGET_MICROLITERS,
                    state = occurrenceState
                )
            ),
            executionCurrent = true
        ),
        reservoir = DeviceDosingReservoirSnapshot(),
        activeRun = DeviceDosingActiveRun(),
        controls = DeviceDosingChannelControls(),
        usageToday = usage
    )

    private companion object {
        const val PROGRAM_TARGET_MICROLITERS = 5_000L
        const val HIGHER_CALENDAR_DAY_USAGE_MICROLITERS = 5_340L
        const val PARTIAL_PHYSICAL_USAGE_MICROLITERS = 380L
    }
}

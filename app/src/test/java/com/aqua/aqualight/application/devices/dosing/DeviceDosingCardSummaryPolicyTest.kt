package com.aqua.aqualight.application.devices.dosing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingCardSummaryPolicyTest {

    @Test
    fun `build composes complete firmware snapshots without recreating scheduler state`() {
        val snapshots = (FIRST_CHANNEL_NUMBER..PUMP_COUNT).map { channelNumber ->
            snapshot(
                channelNumber = channelNumber,
                runtimeEnabled = channelNumber != PUMP_COUNT,
                title = if (channelNumber == POTASSIUM_CHANNEL_NUMBER) {
                    POTASSIUM_TITLE
                } else {
                    "Channel $channelNumber"
                }
            )
        }

        val summary = requireNotNull(
            DeviceDosingCardSummaryPolicy.build(
                deviceUid = DEVICE_UID,
                snapshots = snapshots
            )
        )
        val potassium = summary.channels[POTASSIUM_CHANNEL_INDEX]

        assertEquals(PUMP_COUNT, summary.channelCount)
        assertEquals(ACTIVE_CHANNEL_COUNT, summary.activeChannelCount)
        assertEquals(POTASSIUM_TITLE, potassium.title)
        assertEquals(DAILY_AMOUNT_MICROLITERS, potassium.dailyDoseMicroliters)
        assertEquals(DOSE_TIME_MILLIS, potassium.nextDose?.timeMillis)
        assertEquals(RESERVOIR_REMAINING_MICROLITERS, potassium.reservoir?.remainingMicroliters)
        assertEquals(DeviceDosingCardReservoirState.ESTIMATED, potassium.reservoir?.state)
        assertTrue(potassium.runtimeEnabled)
    }

    @Test
    fun `build fails closed when central state has only a partial channel set`() {
        val summary = DeviceDosingCardSummaryPolicy.build(
            deviceUid = DEVICE_UID,
            snapshots = listOf(
                snapshot(
                    channelNumber = FIRST_CHANNEL_NUMBER,
                    runtimeEnabled = true,
                    title = "NPK"
                )
            )
        )

        assertNull(summary)
    }

    private fun snapshot(
        channelNumber: Int,
        runtimeEnabled: Boolean,
        title: String
    ): DeviceDosingChannelSnapshot = DeviceDosingChannelSnapshot(
        deviceUid = DEVICE_UID,
        slotId = "dosing:channel$channelNumber",
        pumpCount = PUMP_COUNT,
        channelNumber = channelNumber,
        channelTitle = title,
        revision = REVISION,
        runtimeEnabled = runtimeEnabled,
        runtimeReason = if (runtimeEnabled) {
            DeviceDosingRuntimeReason.NONE
        } else {
            DeviceDosingRuntimeReason.PROGRAM_DISABLED
        },
        deliveryAccountingCertain = true,
        calibrated = true,
        lastCalibratedAtEpochSeconds = LAST_CALIBRATED_AT_EPOCH_SECONDS,
        scheduling = DeviceDosingSchedulingPolicy(),
        program = program(),
        progress = progress(channelNumber),
        reservoir = reservoir(),
        activeRun = DeviceDosingActiveRun(),
        controls = DeviceDosingChannelControls(),
        usageToday = DeviceDosingDailyUsageSnapshot()
    )

    private fun program(): DeviceDosingProgram = DeviceDosingProgram(
        enabled = true,
        weekdays = List(WEEKDAY_COUNT) { true },
        schedule = DeviceDosingProgramSchedule.Single(
            dailyDoseMicroliters = DAILY_AMOUNT_MICROLITERS,
            startTimeMillis = DOSE_TIME_MILLIS
        ),
        missedDoseRecoveryEnabled = true
    )

    private fun progress(channelNumber: Int): DeviceDosingChannelProgress =
        DeviceDosingChannelProgress(
            scheduledAmountMicroliters = DAILY_AMOUNT_MICROLITERS,
            completedAmountMicroliters = EMPTY_AMOUNT_MICROLITERS,
            remainingAmountMicroliters = DAILY_AMOUNT_MICROLITERS,
            occurrences = listOf(
                DeviceDosingOccurrenceProgress(
                    index = FIRST_OCCURRENCE_INDEX,
                    eventId = channelNumber.toLong(),
                    programDayOffset = CURRENT_PROGRAM_DAY_OFFSET,
                    timeMillis = DOSE_TIME_MILLIS,
                    amountMicroliters = DAILY_AMOUNT_MICROLITERS,
                    state = DeviceDosingOccurrenceState.PENDING
                )
            ),
            executionCurrent = true,
            programDayDate = PROGRAM_DAY_DATE
        )

    private fun reservoir(): DeviceDosingReservoirSnapshot = DeviceDosingReservoirSnapshot(
        trackingEnabled = true,
        capacityMicroliters = RESERVOIR_CAPACITY_MICROLITERS,
        remainingMicroliters = RESERVOIR_REMAINING_MICROLITERS,
        accountingCertain = true,
        lowLevelActive = false,
        lowLevelAlertEnabled = true
    )

    private companion object {
        const val DEVICE_UID = "device-1"
        const val POTASSIUM_TITLE = "Potassium"
        const val FIRST_CHANNEL_NUMBER = 1
        const val POTASSIUM_CHANNEL_NUMBER = 2
        const val POTASSIUM_CHANNEL_INDEX = 1
        const val PUMP_COUNT = 4
        const val ACTIVE_CHANNEL_COUNT = 3
        const val REVISION = 7L
        const val LAST_CALIBRATED_AT_EPOCH_SECONDS = 1L
        const val WEEKDAY_COUNT = 7
        const val DAILY_AMOUNT_MICROLITERS = 32_000L
        const val DOSE_TIME_MILLIS = 50_400_000L
        const val EMPTY_AMOUNT_MICROLITERS = 0L
        const val FIRST_OCCURRENCE_INDEX = 0
        const val CURRENT_PROGRAM_DAY_OFFSET = 0
        const val RESERVOIR_CAPACITY_MICROLITERS = 500_000L
        const val RESERVOIR_REMAINING_MICROLITERS = 446_200L
        const val PROGRAM_YEAR = 2026
        const val PROGRAM_MONTH = 8
        const val PROGRAM_DAY = 24
        val PROGRAM_DAY_DATE: LocalDate = LocalDate.of(PROGRAM_YEAR, PROGRAM_MONTH, PROGRAM_DAY)
    }
}

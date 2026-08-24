@file:Suppress("LongMethod", "MagicNumber")

package com.aqua.aqualight.application.devices.dosing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingCardSummaryPolicyTest {

    @Test
    fun `build composes complete firmware snapshots without recreating scheduler state`() {
        val snapshots = (1..4).map { channelNumber ->
            snapshot(
                channelNumber = channelNumber,
                runtimeEnabled = channelNumber != 4,
                title = if (channelNumber == 2) "Potassium" else "Channel $channelNumber"
            )
        }

        val summary = requireNotNull(
            DeviceDosingCardSummaryPolicy.build(
                deviceUid = DEVICE_UID,
                snapshots = snapshots
            )
        )

        assertEquals(4, summary.channelCount)
        assertEquals(3, summary.activeChannelCount)
        assertEquals("Potassium", summary.channels[1].title)
        assertEquals(32_000L, summary.channels[1].dailyDoseMicroliters)
        assertEquals(50_400_000L, summary.channels[1].nextDose?.timeMillis)
        assertEquals(446_200L, summary.channels[1].reservoir?.remainingMicroliters)
        assertEquals(
            DeviceDosingCardReservoirState.ESTIMATED,
            summary.channels[1].reservoir?.state
        )
        assertTrue(summary.channels[1].runtimeEnabled)
    }

    @Test
    fun `build fails closed when central state has only a partial channel set`() {
        val summary = DeviceDosingCardSummaryPolicy.build(
            deviceUid = DEVICE_UID,
            snapshots = listOf(snapshot(channelNumber = 1, runtimeEnabled = true, title = "NPK"))
        )

        assertNull(summary)
    }

    private fun snapshot(
        channelNumber: Int,
        runtimeEnabled: Boolean,
        title: String
    ): DeviceDosingChannelSnapshot {
        val amountMicroliters = 32_000L
        return DeviceDosingChannelSnapshot(
            deviceUid = DEVICE_UID,
            slotId = "dosing:channel$channelNumber",
            pumpCount = 4,
            channelNumber = channelNumber,
            channelTitle = title,
            revision = 7L,
            runtimeEnabled = runtimeEnabled,
            runtimeReason = if (runtimeEnabled) {
                DeviceDosingRuntimeReason.NONE
            } else {
                DeviceDosingRuntimeReason.PROGRAM_DISABLED
            },
            deliveryAccountingCertain = true,
            calibrated = true,
            lastCalibratedAtEpochSeconds = 1L,
            scheduling = DeviceDosingSchedulingPolicy(),
            program = DeviceDosingProgram(
                enabled = true,
                weekdays = List(7) { true },
                schedule = DeviceDosingProgramSchedule.Single(
                    dailyDoseMicroliters = amountMicroliters,
                    startTimeMillis = 50_400_000L
                ),
                missedDoseRecoveryEnabled = true
            ),
            progress = DeviceDosingChannelProgress(
                scheduledAmountMicroliters = amountMicroliters,
                completedAmountMicroliters = 0L,
                remainingAmountMicroliters = amountMicroliters,
                occurrences = listOf(
                    DeviceDosingOccurrenceProgress(
                        index = 0,
                        eventId = channelNumber.toLong(),
                        programDayOffset = 0,
                        timeMillis = 50_400_000L,
                        amountMicroliters = amountMicroliters,
                        state = DeviceDosingOccurrenceState.PENDING
                    )
                ),
                executionCurrent = true,
                programDayDate = LocalDate.of(2026, 8, 24)
            ),
            reservoir = DeviceDosingReservoirSnapshot(
                trackingEnabled = true,
                capacityMicroliters = 500_000L,
                remainingMicroliters = 446_200L,
                accountingCertain = true,
                lowLevelActive = false,
                lowLevelAlertEnabled = true
            ),
            activeRun = DeviceDosingActiveRun(),
            controls = DeviceDosingChannelControls(),
            usageToday = DeviceDosingDailyUsageSnapshot()
        )
    }

    private companion object {
        const val DEVICE_UID = "device-1"
    }
}

package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1StatusParser
import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1TestFixtures
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toInitialDosingChannelCardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.withChannelSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingActivationSuffixContractTest {

    @Test
    fun `progress parser accepts canonical suffix indexes independent from array position`() {
        val status = DeviceDosingV1TestFixtures.progressStatus()
        val occurrences = status.getJSONArray("occurrences")
        occurrences.getJSONObject(0).put("index", 14)
        occurrences.getJSONObject(1).put("index", 15)

        val parsed = DeviceDosingV1StatusParser.parseProgress(status)

        assertEquals(listOf(14, 15), parsed.occurrences.map { occurrence -> occurrence.index })
        assertEquals(listOf(901L, 902L), parsed.occurrences.map { occurrence -> occurrence.eventId })
    }

    @Test
    fun `progress parser still rejects canonical indexes that lose firmware order`() {
        val status = DeviceDosingV1TestFixtures.progressStatus()
        val occurrences = status.getJSONArray("occurrences")
        occurrences.getJSONObject(0).put("index", 15)
        occurrences.getJSONObject(1).put("index", 14)

        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1StatusParser.parseProgress(status)
        }
    }

    @Test
    fun `custom period presentation keeps full day behavior and maps activation suffix canonically`() {
        val program = customProgram()
        val fullState = channelSlot().toInitialDosingChannelCardUiState().withChannelSnapshot(
            snapshot(
                program = program,
                progress = DeviceDosingChannelProgress(
                    scheduledAmountMicroliters = 4_000L,
                    occurrences = listOf(
                        occurrence(index = 0, timeMillis = HOUR_11),
                        occurrence(index = 1, timeMillis = HOUR_11 + HALF_HOUR),
                        occurrence(index = 2, timeMillis = HOUR_18),
                        occurrence(index = 3, timeMillis = HOUR_18 + HALF_HOUR)
                    ),
                    executionCurrent = true,
                    programDayDate = MONDAY
                )
            )
        )
        val suffixState = channelSlot().toInitialDosingChannelCardUiState().withChannelSnapshot(
            snapshot(
                program = program,
                progress = DeviceDosingChannelProgress(
                    scheduledAmountMicroliters = 2_000L,
                    occurrences = listOf(
                        occurrence(index = 2, timeMillis = HOUR_18),
                        occurrence(index = 3, timeMillis = HOUR_18 + HALF_HOUR)
                    ),
                    executionCurrent = true,
                    programDayDate = MONDAY
                )
            )
        )

        assertEquals(
            listOf(2, 2),
            fullState.programProgress.customPeriods.map { period -> period.occurrences.size }
        )
        assertEquals(
            listOf(0, 2),
            suffixState.programProgress.customPeriods.map { period -> period.occurrences.size }
        )
        assertEquals(4.0, fullState.programProgress.scheduledAmountTodayMl, 0.0)
        assertEquals(2.0, suffixState.programProgress.scheduledAmountTodayMl, 0.0)
        assertEquals(2, suffixState.programProgress.totalOccurrences)
        assertEquals(HOUR_18, requireNotNull(suffixState.programProgress.nextDose).timeMillis)
    }

    @Test
    fun `past only custom occurrence stays absent when firmware projects no eligible dose`() {
        val program = DeviceDosingProgram(
            enabled = true,
            weekdays = List(7) { true },
            schedule = DeviceDosingProgramSchedule.CustomPeriods(
                dailyDoseMicroliters = 1_000L,
                periods = listOf(DeviceDosingCustomPeriodDraft(HOUR_11, HOUR_12, 1))
            ),
            missedDoseRecoveryEnabled = true
        )
        val state = channelSlot().toInitialDosingChannelCardUiState().withChannelSnapshot(
            snapshot(
                program = program,
                progress = DeviceDosingChannelProgress(
                    executionCurrent = true,
                    programDayDate = MONDAY
                )
            )
        )

        assertFalse(state.programProgress.scheduledToday)
        assertTrue(state.programProgress.occurrences.isEmpty())
        assertTrue(state.programProgress.customPeriods.isEmpty())
        assertNull(state.programProgress.nextDose)
        assertEquals(1.0, state.programProgress.dailyDoseMl, 0.0)
        assertEquals(0.0, state.programProgress.scheduledAmountTodayMl, 0.0)
    }

    private fun customProgram() = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        schedule = DeviceDosingProgramSchedule.CustomPeriods(
            dailyDoseMicroliters = 4_000L,
            periods = listOf(
                DeviceDosingCustomPeriodDraft(HOUR_11, HOUR_12, 2),
                DeviceDosingCustomPeriodDraft(HOUR_18, HOUR_19, 2)
            )
        ),
        missedDoseRecoveryEnabled = true
    )

    private fun channelSlot() = DeviceDosingChannelSlot(
        index = DeviceSlotIndex(1),
        wireKey = DeviceChannelWireKey("channel2"),
        defaultDisplayName = "Channel 2",
        displayNameEditable = true
    )

    private fun snapshot(
        program: DeviceDosingProgram,
        progress: DeviceDosingChannelProgress
    ) = DeviceDosingChannelSnapshot(
        deviceUid = "device-1",
        slotId = "dosing:channel2",
        pumpCount = 2,
        channelNumber = 2,
        channelTitle = "Macro nutrients",
        revision = 7L,
        runtimeEnabled = true,
        runtimeReason = DeviceDosingRuntimeReason.NONE,
        deliveryAccountingCertain = true,
        calibrated = true,
        lastCalibratedAtEpochSeconds = 100L,
        scheduling = DeviceDosingSchedulingPolicy(),
        program = program,
        progress = progress,
        reservoir = DeviceDosingReservoirSnapshot(),
        activeRun = DeviceDosingActiveRun(),
        controls = DeviceDosingChannelControls()
    )

    private fun occurrence(
        index: Int,
        timeMillis: Long
    ) = DeviceDosingOccurrenceProgress(
        index = index,
        eventId = index + 1L,
        programDayOffset = 0,
        timeMillis = timeMillis,
        amountMicroliters = 1_000L,
        state = DeviceDosingOccurrenceState.PENDING
    )

    private companion object {
        const val MILLIS_PER_HOUR = 60L * 60L * 1_000L
        const val HALF_HOUR = 30L * 60L * 1_000L
        const val HOUR_11 = 11L * MILLIS_PER_HOUR
        const val HOUR_12 = 12L * MILLIS_PER_HOUR
        const val HOUR_18 = 18L * MILLIS_PER_HOUR
        const val HOUR_19 = 19L * MILLIS_PER_HOUR
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 10)
    }
}

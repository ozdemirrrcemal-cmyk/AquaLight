package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpVisualState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingTimerDoseDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingDoseProgressVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingProgramModeUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingReservoirTone
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toInitialDosingChannelCardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toPumpVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.withChannelSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingChannelCardMapperTest {

    @Test
    fun `initial card identity is calibration-first and contains no fake metrics`() {
        val state = channelSlot().toInitialDosingChannelCardUiState()

        assertEquals("dosing:channel2", state.slotId)
        assertEquals(2, state.channelNumber)
        assertEquals("Channel 2", state.displayName)
        assertEquals(DosingChannelVisualState.NOT_CONFIGURED, state.visualState)
        assertFalse(state.scheduleDays.isEveryDay)
        assertTrue(state.scheduleDays.selectedDays.isEmpty())
        assertNull(state.programProgress.mode)
        assertEquals(0.0, state.programProgress.dailyDoseMl, 0.0)
        assertTrue(state.programProgress.occurrences.isEmpty())
        assertNull(state.reservoir)
    }

    @Test
    fun `central snapshot name replaces catalog bootstrap exactly`() {
        val snapshot = snapshot(
            program = hourlyProgram(),
            progress = hourlyProgress(),
            reservoir = DeviceDosingReservoirSnapshot()
        )
        val initial = channelSlot().toInitialDosingChannelCardUiState()

        assertEquals("Channel 2", initial.displayName)
        assertEquals(
            "Channel 2",
            initial.withChannelSnapshot(snapshot.copy(channelTitle = "Channel 2")).displayName
        )
        assertEquals(
            "Trace Elements",
            initial.withChannelSnapshot(snapshot.copy(channelTitle = "Trace Elements")).displayName
        )
    }

    @Test
    fun `hourly snapshot keeps scheduled and manual usage separate while hiding disabled reservoir`() {
        val program = hourlyProgram()
        val snapshot = snapshot(
            program = program,
            progress = hourlyProgress(),
            reservoir = DeviceDosingReservoirSnapshot(),
            activeRun = DeviceDosingActiveRun(
                active = true,
                source = DeviceDosingRunSource.SCHEDULED,
                targetAmountMicroliters = 1_000L,
                remainingMillis = 500L
            ),
            usage = DeviceDosingDailyUsageSnapshot(
                valid = true,
                scheduledDeliveredMicroliters = 9_000L,
                manualDeliveredMicroliters = 10_000L,
                totalDeliveredMicroliters = 19_000L
            )
        )

        val state = channelSlot()
            .toInitialDosingChannelCardUiState()
            .withChannelSnapshot(snapshot, MONDAY)

        assertEquals("Macro nutrients", state.displayName)
        assertEquals(DosingChannelVisualState.DOSING, state.visualState)
        assertEquals(5, state.scheduleDays.selectedDays.size)
        assertEquals(DosingProgramModeUiState.HOURLY_24, state.programProgress.mode)
        assertEquals(24.0, state.programProgress.dailyDoseMl, 0.0)
        assertEquals(9.0, state.programProgress.scheduledDeliveredTodayMl, 0.0)
        assertEquals(10.0, state.programProgress.manualDeliveredTodayMl, 0.0)
        assertEquals(24, state.programProgress.totalOccurrences)
        assertEquals(9, state.programProgress.completedOccurrences)
        assertEquals(
            listOf(4.0, 8.0, 12.0, 16.0, 20.0, 24.0),
            state.programProgress.markers.map { marker -> marker.cumulativeAmountMl }
        )
        assertEquals(DosingDoseProgressVisualState.ACTIVE, state.programProgress.visualState)
        assertNull(state.reservoir)
        assertEquals(DosingPumpVisualState.RUNNING, snapshot.toPumpVisualState())
    }

    @Test
    fun `tracked reservoir maps fill and weekday-aware critical day estimate`() {
        val program = DeviceDosingProgram(
            enabled = true,
            weekdays = List(7) { true },
            schedule = DeviceDosingProgramSchedule.Single(
                dailyDoseMicroliters = 10_000L,
                startTimeMillis = 9 * 60 * 60 * 1_000L
            ),
            missedDoseRecoveryEnabled = true
        )
        val progress = DeviceDosingChannelProgress(
            scheduledAmountMicroliters = 10_000L,
            completedAmountMicroliters = 2_000L,
            occurrences = listOf(
                occurrence(index = 0, state = DeviceDosingOccurrenceState.RUNNING, amount = 10_000L)
            ),
            executionCurrent = true,
            programDayDate = MONDAY
        )
        val state = channelSlot().toInitialDosingChannelCardUiState().withChannelSnapshot(
            snapshot = snapshot(
                program = program,
                progress = progress,
                reservoir = DeviceDosingReservoirSnapshot(
                    trackingEnabled = true,
                    capacityMicroliters = 100_000L,
                    remainingMicroliters = 95_000L
                )
            ),
            today = MONDAY
        )

        val reservoir = requireNotNull(state.reservoir)
        assertEquals(95.0, reservoir.remainingMl, 0.0)
        assertEquals(0.95f, reservoir.fillFraction, 0.0f)
        assertEquals(9, reservoir.estimatedRemainingDays)
        assertEquals(DosingReservoirTone.CRITICAL, reservoir.tone)
    }

    @Test
    fun `disabled custom program preserves grouped plan without manufacturing runtime progress`() {
        val program = DeviceDosingProgram(
            enabled = false,
            weekdays = List(7) { true },
            schedule = DeviceDosingProgramSchedule.CustomPeriods(
                dailyDoseMicroliters = 8_000L,
                periods = listOf(
                    DeviceDosingCustomPeriodDraft(28_800_000L, 36_000_000L, 3),
                    DeviceDosingCustomPeriodDraft(50_400_000L, 57_600_000L, 3),
                    DeviceDosingCustomPeriodDraft(72_000_000L, 79_200_000L, 2)
                )
            ),
            missedDoseRecoveryEnabled = true
        )
        val disabledSnapshot = snapshot(
            program = program,
            progress = DeviceDosingChannelProgress(),
            reservoir = DeviceDosingReservoirSnapshot()
        ).copy(
            runtimeEnabled = false,
            runtimeReason = DeviceDosingRuntimeReason.PROGRAM_DISABLED
        )

        val state = channelSlot().toInitialDosingChannelCardUiState()
            .withChannelSnapshot(disabledSnapshot, MONDAY)

        assertEquals(DosingChannelVisualState.AUTOMATIC_DOSING_OFF, state.visualState)
        assertFalse(state.visualState.showsStatusPill)
        assertEquals(DosingProgramModeUiState.CUSTOM_PERIODS, state.programProgress.mode)
        assertEquals(DosingDoseProgressVisualState.DISABLED, state.programProgress.visualState)
        assertEquals(listOf(3, 3, 2), state.programProgress.customPeriods.map {
            period -> period.occurrences.size
        })
        assertEquals(
            listOf(3.0, 6.0, 8.0),
            state.programProgress.markers.map { marker -> marker.cumulativeAmountMl }
        )
        assertEquals(8, state.programProgress.totalOccurrences)
        assertEquals(0.0, state.programProgress.scheduledDeliveredTodayMl, 0.0)
        assertNull(state.reservoir)
    }

    @Test
    fun `timer markers preserve firmware weighted occurrence amounts`() {
        val amounts = listOf(1_500L, 2_000L, 1_250L, 2_750L)
        val program = DeviceDosingProgram(
            enabled = true,
            weekdays = List(7) { true },
            schedule = DeviceDosingProgramSchedule.Timer(
                doses = amounts.mapIndexed { index, amount ->
                    DeviceDosingTimerDoseDraft(
                        startTimeMs = (index + 1L) * 3L * 60L * 60L * 1_000L,
                        amountMicroliters = amount
                    )
                }
            ),
            missedDoseRecoveryEnabled = true
        )
        val progress = DeviceDosingChannelProgress(
            scheduledAmountMicroliters = amounts.sum(),
            completedAmountMicroliters = amounts.take(2).sum(),
            occurrences = amounts.mapIndexed { index, amount ->
                occurrence(
                    index = index,
                    amount = amount,
                    state = if (index < 2) {
                        DeviceDosingOccurrenceState.COMPLETED
                    } else {
                        DeviceDosingOccurrenceState.PENDING
                    }
                )
            },
            executionCurrent = true
        )

        val state = channelSlot().toInitialDosingChannelCardUiState().withChannelSnapshot(
            snapshot = snapshot(
                program = program,
                progress = progress,
                reservoir = DeviceDosingReservoirSnapshot()
            ),
            today = MONDAY
        )

        assertEquals(listOf(1.5, 2.0, 1.25, 2.75), state.programProgress.occurrences.map {
            occurrence -> occurrence.amountMl
        })
        assertEquals(
            listOf(1.5, 3.5, 4.75, 7.5),
            state.programProgress.markers.map { marker -> marker.cumulativeAmountMl }
        )
        assertEquals(listOf(0.2f, 7f / 15f, 19f / 30f, 1f), state.programProgress.markers.map {
            marker -> marker.positionFraction
        })
    }

    private fun channelSlot() = DeviceDosingChannelSlot(
        index = DeviceSlotIndex(1),
        wireKey = DeviceChannelWireKey("channel2"),
        defaultDisplayName = "Channel 2",
        displayNameEditable = true
    )

    private fun hourlyProgram() = DeviceDosingProgram(
        enabled = true,
        weekdays = listOf(true, true, true, true, true, false, false),
        schedule = DeviceDosingProgramSchedule.Hourly24(
            dailyDoseMicroliters = 24_000L,
            startTimeMillis = 36_900_000L
        ),
        missedDoseRecoveryEnabled = true
    )

    private fun hourlyProgress() = DeviceDosingChannelProgress(
        scheduledAmountMicroliters = 24_000L,
        completedAmountMicroliters = 9_000L,
        occurrences = List(24) { index ->
            occurrence(
                index = index,
                state = when {
                    index < 9 -> DeviceDosingOccurrenceState.COMPLETED
                    index == 9 -> DeviceDosingOccurrenceState.RUNNING
                    else -> DeviceDosingOccurrenceState.PENDING
                }
            )
        },
        executionCurrent = true
    )

    private fun snapshot(
        program: DeviceDosingProgram,
        progress: DeviceDosingChannelProgress,
        reservoir: DeviceDosingReservoirSnapshot,
        activeRun: DeviceDosingActiveRun = DeviceDosingActiveRun(),
        usage: DeviceDosingDailyUsageSnapshot = DeviceDosingDailyUsageSnapshot()
    ) = DeviceDosingChannelSnapshot(
        deviceUid = "device-1",
        slotId = "dosing:channel2",
        pumpCount = 2,
        channelNumber = 2,
        channelTitle = "Macro nutrients",
        revision = 7L,
        runtimeEnabled = true,
        runtimeReason = if (activeRun.active) {
            DeviceDosingRuntimeReason.BUSY
        } else {
            DeviceDosingRuntimeReason.NONE
        },
        deliveryAccountingCertain = true,
        calibrated = true,
        lastCalibratedAtEpochSeconds = 100L,
        scheduling = DeviceDosingSchedulingPolicy(),
        program = program,
        progress = progress,
        reservoir = reservoir,
        activeRun = activeRun,
        controls = DeviceDosingChannelControls(),
        usageToday = usage
    )

    private fun occurrence(
        index: Int,
        state: DeviceDosingOccurrenceState,
        amount: Long = 1_000L
    ) = DeviceDosingOccurrenceProgress(
        index = index,
        eventId = index + 1L,
        programDayOffset = 0,
        timeMillis = index * 60L * 60L * 1_000L,
        amountMicroliters = amount,
        state = state
    )

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 10)
    }
}

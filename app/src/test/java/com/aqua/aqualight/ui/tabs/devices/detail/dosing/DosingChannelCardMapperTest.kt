package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.R
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
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingOccurrenceVisualState
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
    fun `initial card exposes topology without manufacturing runtime state`() {
        val state = channelSlot().toInitialDosingChannelCardUiState()

        assertEquals("dosing:channel2", state.slotId)
        assertEquals(2, state.channelNumber)
        assertEquals("Channel 2", state.displayName)
        assertNull(state.visualState)
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
    fun `only invalid time uses the rtc specific attention label`() {
        val baseSnapshot = snapshot(
            program = hourlyProgram(),
            progress = hourlyProgress(),
            reservoir = DeviceDosingReservoirSnapshot()
        )
        val invalidTimeSnapshot = baseSnapshot.copy(
            runtimeEnabled = false,
            runtimeReason = DeviceDosingRuntimeReason.INVALID_TIME
        )

        val invalidTimeState = channelSlot().toInitialDosingChannelCardUiState()
            .withChannelSnapshot(invalidTimeSnapshot)

        assertEquals(DosingChannelVisualState.RTC_ATTENTION, invalidTimeState.visualState)
        assertEquals(
            R.string.device_dosing_channel_status_rtc_attention,
            requireNotNull(invalidTimeState.visualState).labelRes
        )
        assertTrue(requireNotNull(invalidTimeState.visualState).showsStatusPill)
        assertEquals(DosingPumpVisualState.ERROR, invalidTimeSnapshot.toPumpVisualState())

        val invalidTimeWithoutChannelSetup = channelSlot().toInitialDosingChannelCardUiState()
            .withChannelSnapshot(
                invalidTimeSnapshot.copy(
                    calibrated = false,
                    program = null
                )
            )
        assertEquals(
            DosingChannelVisualState.RTC_ATTENTION,
            invalidTimeWithoutChannelSetup.visualState
        )
    }

    @Test
    fun `non rtc attention reasons keep the generic attention label`() {
        val baseSnapshot = snapshot(
            program = hourlyProgram(),
            progress = hourlyProgress(),
            reservoir = DeviceDosingReservoirSnapshot()
        )

        listOf(
            DeviceDosingRuntimeReason.RESERVOIR_UNAVAILABLE,
            DeviceDosingRuntimeReason.ACCOUNTING_UNCERTAIN,
            DeviceDosingRuntimeReason.UNSAFE_AFTER_CALIBRATION,
            DeviceDosingRuntimeReason.INVALID_PROGRAM,
            DeviceDosingRuntimeReason.UNKNOWN
        ).forEach { runtimeReason ->
            val genericState = channelSlot().toInitialDosingChannelCardUiState()
                .withChannelSnapshot(
                    baseSnapshot.copy(
                        runtimeEnabled = false,
                        runtimeReason = runtimeReason
                    )
                )

            assertEquals(DosingChannelVisualState.ERROR, genericState.visualState)
            assertEquals(
                R.string.device_dosing_channel_status_attention,
                requireNotNull(genericState.visualState).labelRes
            )
        }

        val uncertainReservoirState = channelSlot().toInitialDosingChannelCardUiState()
            .withChannelSnapshot(
                baseSnapshot.copy(
                    reservoir = DeviceDosingReservoirSnapshot(
                        trackingEnabled = true,
                        capacityMicroliters = 100_000L,
                        remainingMicroliters = 50_000L,
                        accountingCertain = false
                    )
                )
            )

        assertEquals(DosingChannelVisualState.ERROR, uncertainReservoirState.visualState)
        assertEquals(
            R.string.device_dosing_channel_status_attention,
            requireNotNull(uncertainReservoirState.visualState).labelRes
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
            .withChannelSnapshot(snapshot)

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
    fun `tracked reservoir uses firmware reservation at weekday warning boundary`() {
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
                    remainingMicroliters = 90_000L
                )
            )
        )

        val reservoir = requireNotNull(state.reservoir)
        assertEquals(90.0, reservoir.remainingMl, 0.0)
        assertEquals(0.9f, reservoir.fillFraction, 0.0f)
        assertEquals(10, reservoir.estimatedRemainingDays)
        assertEquals(DosingReservoirTone.WARNING, reservoir.tone)
    }

    @Test
    fun `disabled custom program renders empty rail without manufacturing firmware progress`() {
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
            .withChannelSnapshot(disabledSnapshot)

        assertEquals(DosingChannelVisualState.AUTOMATIC_DOSING_OFF, state.visualState)
        assertFalse(requireNotNull(state.visualState).showsStatusPill)
        assertEquals(DosingProgramModeUiState.CUSTOM_PERIODS, state.programProgress.mode)
        assertEquals(DosingDoseProgressVisualState.DISABLED, state.programProgress.visualState)
        assertTrue(state.programProgress.occurrences.isEmpty())
        assertTrue(state.programProgress.customPeriods.isEmpty())
        assertTrue(state.programProgress.markers.isEmpty())
        assertEquals(0, state.programProgress.totalOccurrences)
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
            )
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

    @Test
    fun `interrupted delivery stays occurrence scoped without global error state`() {
        val program = hourlyProgram()
        val interruptedProgress = DeviceDosingChannelProgress(
            scheduledAmountMicroliters = 24_000L,
            completedAmountMicroliters = 8_000L,
            occurrences = listOf(
                occurrence(
                    index = 0,
                    amount = 8_000L,
                    state = DeviceDosingOccurrenceState.COMPLETED
                ),
                occurrence(
                    index = 1,
                    amount = 1_000L,
                    state = DeviceDosingOccurrenceState.UNCERTAIN
                ),
                occurrence(
                    index = 2,
                    amount = 15_000L,
                    state = DeviceDosingOccurrenceState.PENDING
                )
            ),
            executionCurrent = true,
            accountingCertain = false,
            programDayDate = MONDAY
        )
        val snapshot = snapshot(
            program = program,
            progress = interruptedProgress,
            reservoir = DeviceDosingReservoirSnapshot(
                trackingEnabled = true,
                capacityMicroliters = 450_000L,
                remainingMicroliters = 290_000L,
                accountingCertain = true
            )
        ).copy(deliveryAccountingCertain = false)

        val state = channelSlot().toInitialDosingChannelCardUiState()
            .withChannelSnapshot(snapshot)

        assertEquals(DosingChannelVisualState.CONFIGURED, state.visualState)
        assertEquals(DosingPumpVisualState.IDLE, snapshot.toPumpVisualState())
        assertEquals(DosingDoseProgressVisualState.READY, state.programProgress.visualState)
        assertEquals(
            DosingOccurrenceVisualState.UNCERTAIN,
            state.programProgress.occurrences[1].visualState
        )
        assertEquals(290.0, requireNotNull(state.reservoir).remainingMl, 0.0)
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
            minuteOfHour = 15
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

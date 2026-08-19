package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.ui.graphics.Color
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
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelRunSourceUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelRuntimeUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelSetupUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingDoseProgressVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingOccurrenceVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingProgressPalette
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingReservoirTone
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.colorFor
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.toDosingChannelCardUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingChannelCardDataContractTest {

    @Test
    fun `active firmware program day remains visible before execution checkpoint becomes current`() {
        val state = snapshot(
            progress = progress(
                programDayDate = PROGRAM_DAY,
                executionCurrent = false
            )
        ).toDosingChannelCardUiState()

        assertTrue(state.programProgress.scheduledToday)
        assertEquals(1, state.programProgress.totalOccurrences)
        assertEquals(
            DosingOccurrenceVisualState.PENDING,
            state.programProgress.occurrences.single().visualState
        )
        assertEquals(DosingDoseProgressVisualState.READY, state.programProgress.visualState)
    }

    @Test
    fun `no firmware program day hides compiled occurrences without inventing schedule state`() {
        val state = snapshot(
            progress = progress(
                programDayDate = null,
                executionCurrent = false
            )
        ).toDosingChannelCardUiState()

        assertFalse(state.programProgress.scheduledToday)
        assertTrue(state.programProgress.occurrences.isEmpty())
        assertEquals(DosingDoseProgressVisualState.EMPTY, state.programProgress.visualState)
    }

    @Test
    fun `manual run stays independent from automatic program setup state`() {
        val state = snapshot(
            program = null,
            progress = DeviceDosingChannelProgress(),
            activeRun = DeviceDosingActiveRun(
                active = true,
                source = DeviceDosingRunSource.MANUAL,
                targetAmountMicroliters = 5_000L,
                remainingMillis = 500L
            )
        ).toDosingChannelCardUiState()

        assertEquals(DosingChannelSetupUiState.PROGRAM_NOT_CONFIGURED, state.setupState)
        assertEquals(DosingChannelRuntimeUiState.DOSING, state.runtimeState)
        assertEquals(DosingChannelRunSourceUiState.MANUAL, state.activeRunSource)
        assertEquals(DosingChannelVisualState.DOSING, state.visualState)
    }

    @Test
    fun `attention state is never hidden by missing automatic program`() {
        val state = snapshot(
            program = null,
            progress = DeviceDosingChannelProgress(),
            deliveryAccountingCertain = false
        ).toDosingChannelCardUiState()

        assertEquals(DosingChannelSetupUiState.PROGRAM_NOT_CONFIGURED, state.setupState)
        assertEquals(DosingChannelRuntimeUiState.ATTENTION, state.runtimeState)
        assertEquals(DosingChannelVisualState.ERROR, state.visualState)
    }

    @Test
    fun `untrusted reservoir remaining never becomes a trustworthy zero value on the card`() {
        val state = snapshot(
            reservoir = DeviceDosingReservoirSnapshot(
                trackingEnabled = true,
                capacityMicroliters = 100_000L,
                remainingMicroliters = 0L,
                accountingCertain = false
            ),
            deliveryAccountingCertain = false
        ).toDosingChannelCardUiState()

        val reservoir = requireNotNull(state.reservoir)
        assertFalse(reservoir.remainingAvailable)
        assertFalse(reservoir.fillAvailable)
        assertEquals(DosingReservoirTone.UNCERTAIN, reservoir.tone)
    }

    @Test
    fun `remaining day forecast uses firmware program day even before checkpoint initialization`() {
        val state = snapshot(
            progress = progress(
                programDayDate = PROGRAM_DAY,
                executionCurrent = false,
                scheduledAmountMicroliters = 10_000L
            ),
            reservoir = DeviceDosingReservoirSnapshot(
                trackingEnabled = true,
                capacityMicroliters = 100_000L,
                remainingMicroliters = 25_000L,
                accountingCertain = true
            )
        ).toDosingChannelCardUiState()

        assertEquals(2, requireNotNull(state.reservoir).estimatedRemainingDays)
    }

    @Test
    fun `running occurrence uses active palette instead of pending palette`() {
        val palette = DosingProgressPalette(
            track = Color.Black,
            outline = Color.DarkGray,
            completed = Color.Green,
            active = Color.Red,
            pending = Color.Blue,
            skipped = Color.Yellow,
            uncertain = Color.Magenta,
            valueText = Color.White,
            tagSurface = Color.Gray,
            tagOutline = Color.Cyan
        )

        assertEquals(Color.Red, palette.colorFor(DosingOccurrenceVisualState.ACTIVE))
        assertEquals(Color.Blue, palette.colorFor(DosingOccurrenceVisualState.PENDING))
    }

    private fun snapshot(
        program: DeviceDosingProgram? = program(),
        progress: DeviceDosingChannelProgress = progress(PROGRAM_DAY, executionCurrent = true),
        reservoir: DeviceDosingReservoirSnapshot = DeviceDosingReservoirSnapshot(),
        activeRun: DeviceDosingActiveRun = DeviceDosingActiveRun(),
        deliveryAccountingCertain: Boolean = true
    ) = DeviceDosingChannelSnapshot(
        deviceUid = DEVICE_UID,
        slotId = SLOT_ID,
        pumpCount = 2,
        channelNumber = 1,
        channelTitle = "Nutrients",
        revision = 7L,
        runtimeEnabled = true,
        runtimeReason = DeviceDosingRuntimeReason.NONE,
        deliveryAccountingCertain = deliveryAccountingCertain,
        calibrated = true,
        lastCalibratedAtEpochSeconds = 100L,
        scheduling = DeviceDosingSchedulingPolicy(),
        program = program,
        progress = progress,
        reservoir = reservoir,
        activeRun = activeRun,
        controls = DeviceDosingChannelControls(),
        usageToday = DeviceDosingDailyUsageSnapshot()
    )

    private fun program() = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        schedule = DeviceDosingProgramSchedule.Single(
            dailyDoseMicroliters = 10_000L,
            startTimeMillis = DOSE_TIME_MS
        ),
        missedDoseRecoveryEnabled = true
    )

    private fun progress(
        programDayDate: LocalDate?,
        executionCurrent: Boolean,
        scheduledAmountMicroliters: Long = 10_000L
    ) = DeviceDosingChannelProgress(
        scheduledAmountMicroliters = scheduledAmountMicroliters,
        completedAmountMicroliters = 0L,
        occurrences = listOf(
            DeviceDosingOccurrenceProgress(
                index = 0,
                eventId = 1L,
                programDayOffset = 0,
                timeMillis = DOSE_TIME_MS,
                amountMicroliters = scheduledAmountMicroliters,
                state = DeviceDosingOccurrenceState.PENDING
            )
        ),
        executionCurrent = executionCurrent,
        accountingCertain = true,
        programDayDate = programDayDate
    )

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel1"
        const val DOSE_TIME_MS = 28_800_000L
        val PROGRAM_DAY: LocalDate = LocalDate.of(2026, 8, 19)
    }
}

package com.aqua.aqualight.application.devices.dosing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingProgressSemanticsTest {
    @Test
    fun `next scheduled occurrence preserves firmware order and skips resolved states`() {
        val progress = progress(
            occurrences = listOf(
                occurrence(index = 0, state = DeviceDosingOccurrenceState.COMPLETED),
                occurrence(index = 1, state = DeviceDosingOccurrenceState.SKIPPED),
                occurrence(index = 2, state = DeviceDosingOccurrenceState.PENDING),
                occurrence(index = 3, state = DeviceDosingOccurrenceState.PENDING)
            )
        )

        assertEquals(2, progress.nextScheduledOccurrence()?.index)
    }

    @Test
    fun `next scheduled occurrence fails closed for stale firmware projection`() {
        val progress = progress(
            occurrences = listOf(occurrence(index = 0, state = DeviceDosingOccurrenceState.PENDING)),
            executionCurrent = false
        )

        assertNull(progress.nextScheduledOccurrence())
    }

    @Test
    fun `next scheduled occurrence fails closed without firmware program day anchor`() {
        val progress = progress(
            occurrences = listOf(occurrence(index = 0, state = DeviceDosingOccurrenceState.PENDING)),
            programDayDate = null
        )

        assertNull(progress.nextScheduledOccurrence())
    }

    @Test
    fun `next scheduled occurrence is absent when no pending occurrence remains`() {
        val progress = progress(
            occurrences = listOf(
                occurrence(index = 0, state = DeviceDosingOccurrenceState.COMPLETED),
                occurrence(index = 1, state = DeviceDosingOccurrenceState.UNCERTAIN)
            )
        )

        assertNull(progress.nextScheduledOccurrence())
    }

    private fun progress(
        occurrences: List<DeviceDosingOccurrenceProgress>,
        executionCurrent: Boolean = true,
        programDayDate: LocalDate? = LocalDate.of(2026, 8, 24)
    ): DeviceDosingChannelProgress {
        val scheduledAmount = occurrences.sumOf(DeviceDosingOccurrenceProgress::amountMicroliters)
        val completedAmount = occurrences
            .filter { occurrence -> occurrence.state == DeviceDosingOccurrenceState.COMPLETED }
            .sumOf(DeviceDosingOccurrenceProgress::amountMicroliters)
        return DeviceDosingChannelProgress(
            scheduledAmountMicroliters = scheduledAmount,
            completedAmountMicroliters = completedAmount,
            remainingAmountMicroliters = scheduledAmount - completedAmount,
            occurrences = occurrences,
            scheduleState = DeviceDosingScheduleState.ACTIVE,
            executionCurrent = executionCurrent,
            programDayDate = programDayDate
        )
    }

    private fun occurrence(
        index: Int,
        state: DeviceDosingOccurrenceState
    ): DeviceDosingOccurrenceProgress = DeviceDosingOccurrenceProgress(
        index = index,
        eventId = index.toLong(),
        programDayOffset = 0,
        timeMillis = index * 60_000L,
        amountMicroliters = 125_000L,
        state = state
    )
}

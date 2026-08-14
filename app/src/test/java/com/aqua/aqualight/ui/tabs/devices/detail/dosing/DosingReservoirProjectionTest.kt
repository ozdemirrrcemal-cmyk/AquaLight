package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingReservoirProjection
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DosingReservoirProjectionTest {

    @Test
    fun `daily recurrence returns calendar day on which the next dose cannot be covered`() {
        val days = DosingReservoirProjection.estimateRemainingDays(
            remainingMicroliters = 47_000L,
            dailyDoseMicroliters = 7_500L,
            remainingScheduledTodayMicroliters = 7_500L,
            selectedWeekdays = List(7) { true },
            today = MONDAY
        )

        assertEquals(6, days)
    }

    @Test
    fun `selected weekdays are projected as calendar days rather than dose count`() {
        val tuesdayAndThursday = listOf(false, true, false, true, false, false, false)
        val days = DosingReservoirProjection.estimateRemainingDays(
            remainingMicroliters = 20_000L,
            dailyDoseMicroliters = 10_000L,
            remainingScheduledTodayMicroliters = 0L,
            selectedWeekdays = tuesdayAndThursday,
            today = MONDAY
        )

        assertEquals(8, days)
    }

    @Test
    fun `missing recurrence does not manufacture a remaining day estimate`() {
        val days = DosingReservoirProjection.estimateRemainingDays(
            remainingMicroliters = 20_000L,
            dailyDoseMicroliters = 10_000L,
            remainingScheduledTodayMicroliters = 0L,
            selectedWeekdays = List(7) { false },
            today = MONDAY
        )

        assertNull(days)
    }

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 10)
    }
}

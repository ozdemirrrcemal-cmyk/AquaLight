package com.aqua.aqualight.application.devices.cooling.program

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingProgramTimeSelectionsTest {

    @Test
    fun startOptionsUseTheAuthoritativeStepAndSupportAtomicMinimumPeriodMoves() {
        val selection = requireNotNull(
            CoolingProgramTimeSelections.forStartTime(
                slots = listOf(slot(startMinutes = 0, endMinutes = 15)),
                policy = policy(),
                slotIndex = 0
            )
        )

        assertEquals(0, selection.currentMinutesOfDay)
        assertTrue(hour(8) in selection.selectableMinutesOfDay)
        assertFalse(hour(8) + 1 in selection.selectableMinutesOfDay)
        assertTrue(selection.selectableMinutesOfDay.all { minute -> minute % 5 == 0 })
    }

    @Test
    fun startAndEndOptionsExcludeRangesThatWouldOverlapAnotherPeriod() {
        val edited = slot(startMinutes = hour(7), endMinutes = hour(7) + 15)
        val blocker = slot(startMinutes = hour(8), endMinutes = hour(9))
        val slots = listOf(edited, blocker)

        val starts = requireNotNull(
            CoolingProgramTimeSelections.forStartTime(slots, policy(), slotIndex = 0)
        ).selectableMinutesOfDay
        val ends = requireNotNull(
            CoolingProgramTimeSelections.forEndTime(slots, policy(), slotIndex = 0)
        ).selectableMinutesOfDay

        assertTrue(hour(7) + 45 in starts)
        assertFalse(hour(7) + 50 in starts)
        assertTrue(hour(8) in ends)
        assertFalse(hour(8) + 5 in ends)
    }

    @Test
    fun endOptionsKeepTheFirmwareEndOfDayBoundary() {
        val selection = requireNotNull(
            CoolingProgramTimeSelections.forEndTime(
                slots = listOf(slot(startMinutes = hour(23), endMinutes = hour(23) + 15)),
                policy = policy(),
                slotIndex = 0
            )
        )

        assertTrue(MINUTES_PER_DAY in selection.selectableMinutesOfDay)
        assertFalse(hour(23) + 10 in selection.selectableMinutesOfDay)
    }

    private fun policy(): CoolingProgramPolicy = CoolingProgramPolicy(
        maximumSlotCount = 8,
        timeStepMinutes = 5,
        minimumSlotDurationMinutes = 15,
        fan = CoolingProgramFanPolicy(
            minimumPercent = 0,
            maximumPercent = 100,
            stepPercent = 1
        ),
        fanOnTemperature = CoolingProgramFanOnTemperaturePolicy(
            minimumC = 0.0,
            maximumC = 40.0,
            stepC = 0.5,
            defaultC = 25.0
        )
    )

    private fun slot(startMinutes: Int, endMinutes: Int): CoolingProgramSlot = CoolingProgramSlot(
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        fanOnTemperatureC = 25.0,
        targetFanPercent = 50
    )

    private fun hour(value: Int): Int = value * MINUTES_PER_HOUR

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}

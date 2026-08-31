package com.aqua.aqualight.application.devices.cooling.program

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingProgramScheduleTest {

    @Test
    fun adjacentPeriodsAreValid() {
        val slots = listOf(
            slot(hour(8), hour(14)),
            slot(hour(14), hour(20))
        )

        assertTrue(CoolingProgramSchedule.isValidProgram(slots, policy()))
    }

    @Test
    fun overlappingPeriodsAreInvalid() {
        val slots = listOf(
            slot(hour(8), hour(14)),
            slot(hour(13), hour(20))
        )

        assertFalse(CoolingProgramSchedule.isValidProgram(slots, policy()))
    }

    @Test
    fun sameDayPeriodRejectsEndBeforeStart() {
        val original = slot(hour(8), hour(14))

        val result = CoolingProgramSchedule.updateEndTime(
            slots = listOf(original),
            policy = policy(),
            slotIndex = 0,
            endMinutes = hour(7)
        )

        assertEquals(
            CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.INVALID_TIME_RANGE),
            result
        )
    }

    @Test
    fun fanLimitSnapsToReportedPolicyStep() {
        val original = slot(hour(8), hour(14), fanLimitPercent = 60)

        val result = CoolingProgramSchedule.updateFanLimit(
            slots = listOf(original),
            policy = policy(fanPercentStep = 10),
            slotIndex = 0,
            percent = 66
        )

        assertTrue(result is CoolingProgramEditResult.Updated)
        val updated = (result as CoolingProgramEditResult.Updated).slots.single()
        assertEquals(70, updated.fanLimitPercent)
    }

    @Test
    fun maximumSlotCountRejectsAdditionalPeriod() {
        val result = CoolingProgramSchedule.addSlot(
            slots = listOf(slot(hour(8), hour(14))),
            policy = policy(maximumSlotCount = 1)
        )

        assertEquals(
            CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.MAXIMUM_SLOT_COUNT_REACHED
            ),
            result
        )
    }

    private fun slot(
        startMinutes: Int,
        endMinutes: Int,
        fanLimitPercent: Int = 60
    ): CoolingProgramSlot = CoolingProgramSlot(
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        fanLimitPercent = fanLimitPercent
    )

    private fun policy(
        maximumSlotCount: Int = 6,
        fanPercentStep: Int = 5
    ): CoolingProgramPolicy = CoolingProgramPolicy(
        maximumSlotCount = maximumSlotCount,
        minimumFanPercent = 0,
        maximumFanPercent = 100,
        fanPercentStep = fanPercentStep,
        minimumSlotDurationMinutes = 15
    )

    private companion object {
        const val MINUTES_PER_HOUR = 60

        fun hour(value: Int): Int = value * MINUTES_PER_HOUR
    }
}

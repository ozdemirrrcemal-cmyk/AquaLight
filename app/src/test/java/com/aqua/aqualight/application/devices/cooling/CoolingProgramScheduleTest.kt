package com.aqua.aqualight.application.devices.cooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingProgramScheduleTest {

    @Test
    fun adjacentPeriodsAreValid() {
        val slots = listOf(
            slot("morning", hour(8), hour(14)),
            slot("evening", hour(14), hour(20))
        )

        assertTrue(CoolingProgramSchedule.isValidProgram(slots, capabilities()))
    }

    @Test
    fun overlappingPeriodsAreInvalid() {
        val slots = listOf(
            slot("morning", hour(8), hour(14)),
            slot("evening", hour(13), hour(20))
        )

        assertFalse(CoolingProgramSchedule.isValidProgram(slots, capabilities()))
    }

    @Test
    fun crossMidnightUpdateIsRejected() {
        val original = slot("period", hour(8), hour(14))

        val result = CoolingProgramSchedule.updateEndTime(
            slots = listOf(original),
            capabilities = capabilities(),
            slotId = original.id,
            endMinutes = hour(7)
        )

        assertEquals(
            CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.INVALID_TIME_RANGE),
            result
        )
    }

    @Test
    fun fanLimitSnapsToReportedCapabilityStep() {
        val original = slot("period", hour(8), hour(14), fanLimitPercent = 60)

        val result = CoolingProgramSchedule.updateFanLimit(
            slots = listOf(original),
            capabilities = capabilities(fanLimitStepPercent = 10),
            slotId = original.id,
            percent = 66
        )

        assertTrue(result is CoolingProgramEditResult.Updated)
        val updated = (result as CoolingProgramEditResult.Updated).slots.single()
        assertEquals(70, updated.fanLimitPercent)
    }

    @Test
    fun duplicateDraftIdentityIsRejected() {
        val original = slot("period", hour(8), hour(14))

        val result = CoolingProgramSchedule.addSlot(
            slots = listOf(original),
            capabilities = capabilities(),
            newSlotId = original.id
        )

        assertEquals(
            CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.DUPLICATE_SLOT_ID),
            result
        )
    }

    private fun slot(
        id: String,
        startMinutes: Int,
        endMinutes: Int,
        fanLimitPercent: Int = 60
    ): CoolingProgramSlot = CoolingProgramSlot(
        id = id,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        fanLimitPercent = fanLimitPercent
    )

    private fun capabilities(
        fanLimitStepPercent: Int = 5
    ): CoolingProgramCapabilities = CoolingProgramCapabilities(
        minimumSlotCount = 0,
        maximumSlotCount = 6,
        minimumFanLimitPercent = 0,
        maximumFanLimitPercent = 100,
        fanLimitStepPercent = fanLimitStepPercent,
        minimumSlotDurationMinutes = 15
    )

    private companion object {
        const val MINUTES_PER_HOUR = 60

        fun hour(value: Int): Int = value * MINUTES_PER_HOUR
    }
}

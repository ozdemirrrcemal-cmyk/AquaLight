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
    fun firmwareEndOfDayBoundaryIsValid() {
        val slot = slot(hour(23), MINUTES_PER_DAY)

        assertTrue(CoolingProgramSchedule.isValidProgram(listOf(slot), policy()))
    }

    @Test
    fun timeEditRejectsValueOutsideFirmwareStep() {
        val original = slot(hour(8), hour(14))

        val result = CoolingProgramSchedule.updateStartTime(
            slots = listOf(original),
            policy = policy(timeStepMinutes = 5),
            slotIndex = 0,
            startMinutes = hour(8) + 1
        )

        assertEquals(
            CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.INVALID_TIME_RANGE),
            result
        )
    }

    @Test
    fun validationRejectsSlotOutsideFirmwareStep() {
        val offStep = slot(hour(8) + 1, hour(14))

        assertFalse(CoolingProgramSchedule.isValidProgram(listOf(offStep), policy(timeStepMinutes = 5)))
    }

    @Test
    fun targetFanPercentSnapsToReportedPolicyStep() {
        val original = slot(hour(8), hour(14), targetFanPercent = 60)

        val result = CoolingProgramSchedule.updateTargetFanPercent(
            slots = listOf(original),
            policy = policy(fanPercentStep = 10),
            slotIndex = 0,
            percent = 66
        )

        assertTrue(result is CoolingProgramEditResult.Updated)
        val updated = (result as CoolingProgramEditResult.Updated).slots.single()
        assertEquals(70, updated.targetFanPercent)
    }

    @Test
    fun fanOnTemperatureSnapsToReportedPolicyStep() {
        val original = slot(hour(8), hour(14), fanOnTemperatureC = 25.0)

        val result = CoolingProgramSchedule.updateFanOnTemperature(
            slots = listOf(original),
            policy = policy(fanOnTemperatureStepC = 0.5),
            slotIndex = 0,
            temperatureC = 25.3
        )

        assertTrue(result is CoolingProgramEditResult.Updated)
        val updated = (result as CoolingProgramEditResult.Updated).slots.single()
        assertEquals(25.5, updated.fanOnTemperatureC, 0.0)
    }

    @Test
    fun addedPeriodUsesAuthoritativeTemperatureDefault() {
        val result = CoolingProgramSchedule.addSlot(
            slots = emptyList(),
            policy = policy(defaultFanOnTemperatureC = 26.0)
        )

        assertTrue(result is CoolingProgramEditResult.Updated)
        val slot = (result as CoolingProgramEditResult.Updated).slots.single()
        assertEquals(26.0, slot.fanOnTemperatureC, 0.0)
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
        fanOnTemperatureC: Double = 25.0,
        targetFanPercent: Int = 60
    ): CoolingProgramSlot = CoolingProgramSlot(
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        fanOnTemperatureC = fanOnTemperatureC,
        targetFanPercent = targetFanPercent
    )

    private fun policy(
        maximumSlotCount: Int = 6,
        timeStepMinutes: Int = 5,
        fanPercentStep: Int = 5,
        fanOnTemperatureStepC: Double = 0.5,
        defaultFanOnTemperatureC: Double = 25.0
    ): CoolingProgramPolicy = CoolingProgramPolicy(
        maximumSlotCount = maximumSlotCount,
        timeStepMinutes = timeStepMinutes,
        minimumSlotDurationMinutes = 15,
        fan = CoolingProgramFanPolicy(
            minimumPercent = 0,
            maximumPercent = 100,
            stepPercent = fanPercentStep
        ),
        fanOnTemperature = CoolingProgramFanOnTemperaturePolicy(
            minimumC = 15.0,
            maximumC = 40.0,
            stepC = fanOnTemperatureStepC,
            defaultC = defaultFanOnTemperatureC
        )
    )

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        fun hour(value: Int): Int = value * MINUTES_PER_HOUR
    }
}

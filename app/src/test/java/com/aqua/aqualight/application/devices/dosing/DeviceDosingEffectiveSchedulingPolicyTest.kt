package com.aqua.aqualight.application.devices.dosing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingEffectiveSchedulingPolicyTest {

    @Test
    fun `plan validation accepts exact calibrated boundaries and rejects outside values`() {
        val policy = DeviceDosingSchedulingPolicy(
            amountResolutionMicroliters = 1L,
            effectiveScheduledDoseMicroliters = 80L..2_880_000L
        )

        assertTrue(singleProgram(80L).isValidFor(policy))
        assertTrue(singleProgram(2_880_000L).isValidFor(policy))
        assertFalse(singleProgram(79L).isValidFor(policy))
        assertFalse(singleProgram(2_880_001L).isValidFor(policy))
    }

    @Test
    fun `unavailable effective limits do not invent a channel calibration range`() {
        val policy = DeviceDosingSchedulingPolicy(
            amountResolutionMicroliters = 1L,
            effectiveScheduledDoseMicroliters = null
        )

        assertTrue(singleProgram(1L).isValidFor(policy))
        assertTrue(singleProgram(3_000_000L).isValidFor(policy))
    }

    @Test
    fun `calibration change immediately changes plan preflight boundaries`() {
        val beforeCalibrationChange = DeviceDosingSchedulingPolicy(
            effectiveScheduledDoseMicroliters = 80L..2_880_000L
        )
        val afterCalibrationChange = DeviceDosingSchedulingPolicy(
            effectiveScheduledDoseMicroliters = 120L..2_400_000L
        )
        val program = singleProgram(100L)

        assertTrue(program.isValidFor(beforeCalibrationChange))
        assertFalse(program.isValidFor(afterCalibrationChange))
    }

    private fun singleProgram(amountMicroliters: Long) = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        schedule = DeviceDosingProgramSchedule.Single(
            dailyDoseMicroliters = amountMicroliters,
            startTimeMillis = 36_900_000L
        ),
        missedDoseRecoveryEnabled = false
    )
}

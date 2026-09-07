package com.aqua.aqualight.application.devices.cooling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingAutomaticTemperatureValidationTest {

    @Test
    fun startTemperatureKeepsExistingRangeAndGapRules() {
        assertTrue(
            DeviceCoolingAutomaticTemperatureValidation.isValidStartTemperature(
                value = 25.0,
                policy = POLICY,
                maximumSpeedTemperatureC = 25.5
            )
        )
        assertFalse(
            DeviceCoolingAutomaticTemperatureValidation.isValidStartTemperature(
                value = 30.5,
                policy = POLICY,
                maximumSpeedTemperatureC = 32.0
            )
        )
        assertFalse(
            DeviceCoolingAutomaticTemperatureValidation.isValidStartTemperature(
                value = 25.1,
                policy = POLICY,
                maximumSpeedTemperatureC = 25.5
            )
        )
    }

    @Test
    fun maximumTemperatureKeepsExistingRangeAndGapRules() {
        assertTrue(
            DeviceCoolingAutomaticTemperatureValidation.isValidMaximumSpeedTemperature(
                value = 25.5,
                policy = POLICY,
                startTemperatureC = 25.0
            )
        )
        assertFalse(
            DeviceCoolingAutomaticTemperatureValidation.isValidMaximumSpeedTemperature(
                value = 32.5,
                policy = POLICY,
                startTemperatureC = 25.0
            )
        )
        assertFalse(
            DeviceCoolingAutomaticTemperatureValidation.isValidMaximumSpeedTemperature(
                value = 25.4,
                policy = POLICY,
                startTemperatureC = 25.0
            )
        )
    }

    @Test
    fun minimumGapKeepsExistingFloatingPointTolerance() {
        assertTrue(
            DeviceCoolingAutomaticTemperatureValidation.isValidStartTemperature(
                value = 25.0,
                policy = POLICY,
                maximumSpeedTemperatureC = 25.499_999_5
            )
        )
        assertFalse(
            DeviceCoolingAutomaticTemperatureValidation.isValidStartTemperature(
                value = 25.0,
                policy = POLICY,
                maximumSpeedTemperatureC = 25.499_998
            )
        )
    }

    @Test
    fun validationDoesNotIntroduceStepSnapping() {
        assertTrue(
            DeviceCoolingAutomaticTemperatureValidation.isValidStartTemperature(
                value = 25.25,
                policy = POLICY,
                maximumSpeedTemperatureC = 26.0
            )
        )
        assertFalse(
            DeviceCoolingAutomaticTemperatureValidation.isValidMaximumSpeedTemperature(
                value = Double.NaN,
                policy = POLICY,
                startTemperatureC = 25.0
            )
        )
    }

    private companion object {
        val POLICY = DeviceCoolingAutomaticTemperaturePolicy(
            startMinimumC = 18.0,
            startMaximumC = 30.0,
            maximumSpeedMinimumC = 18.5,
            maximumSpeedMaximumC = 32.0,
            stepC = 0.5,
            minimumGapC = 0.5,
            hysteresisC = 0.5
        )
    }
}

package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingMutationContractTest {
    @Test
    fun `exact config result validates requested global config and fan name`() {
        val payload = DeviceCoolingConfigApplyPayload(
            mode = DeviceCoolingMode.ON,
            minTemperatureC = 29.0,
            maxTemperatureC = 36.0,
            fans = listOf(DeviceCoolingFanDisplayNamePayload("fan1", "Sol Fan")),
            save = true
        )
        val result = DeviceCoolingMutationParser.parseConfigApply(
            DeviceCoolingRuntimeFixtures.configApply()
        )

        DeviceCoolingCommandValidation.validateRequest(
            payload,
            DeviceCoolingStatusParser.parse(DeviceCoolingRuntimeFixtures.status())
        )
        DeviceCoolingCommandValidation.validateResult(payload, result)

        assertEquals(DeviceCoolingMode.ON, result.config.mode)
        assertEquals("Sol Fan", result.config.fans.single().fan.displayName)
        assertTrue(result.saved)
    }

    @Test
    fun `save echo mismatch is rejected`() {
        val payload = DeviceCoolingConfigApplyPayload(
            mode = DeviceCoolingMode.ON,
            minTemperatureC = 29.0,
            maxTemperatureC = 36.0,
            save = false
        )
        val result = DeviceCoolingMutationParser.parseConfigApply(
            DeviceCoolingRuntimeFixtures.configApply(save = true, fanDisplayNameEditable = false)
                .put("appliedFanDisplayNames", false)
        )

        assertTrue(
            runCatching {
                DeviceCoolingCommandValidation.validateResult(payload, result)
            }.isFailure
        )
    }

    @Test
    fun `partial temperature request is validated against current status`() {
        val status = DeviceCoolingStatusParser.parse(DeviceCoolingRuntimeFixtures.status())
        val invalid = DeviceCoolingConfigApplyPayload(minTemperatureC = 40.0)

        assertTrue(
            runCatching {
                DeviceCoolingCommandValidation.validateRequest(invalid, status)
            }.isFailure
        )
    }

    @Test
    fun `duplicate fan keys are rejected before encoding`() {
        assertTrue(
            runCatching {
                DeviceCoolingConfigApplyPayload(
                    fans = listOf(
                        DeviceCoolingFanDisplayNamePayload("fan1", "One"),
                        DeviceCoolingFanDisplayNamePayload(" FAN1 ", "Two")
                    )
                )
            }.isFailure
        )
    }
}

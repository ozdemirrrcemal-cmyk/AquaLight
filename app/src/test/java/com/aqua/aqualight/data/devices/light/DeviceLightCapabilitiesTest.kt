package com.aqua.aqualight.data.devices.light

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalV1Contract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightCapabilitiesTest {

    @Test
    fun `system capability requires the exact root topology and features`() {
        val supported = systemRoot()

        assertTrue(supported.supportsLightSystem())
        assertFalse(supported.copy(fanOutputCount = 1).supportsLightSystem())
        assertFalse(supported.copy(temperatureSensorCount = 0).supportsLightSystem())
        assertFalse(
            supported.copy(supportedFeatures = listOf(LIGHT_FAN_CONTROL)).supportsLightSystem()
        )
    }

    @Test
    fun `adaptation capability requires both status and policy support`() {
        val supported = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val policyDisabled = supported.copy(
            policy = supported.policy.copy(
                acclimation = supported.policy.acclimation.copy(supported = false)
            )
        )

        assertTrue(supported.supportsLightAdaptation())
        assertFalse(policyDisabled.supportsLightAdaptation())
        assertFalse(
            supported.copy(
                features = supported.features.copy(acclimation = false)
            ).supportsLightAdaptation()
        )
    }

    private fun systemRoot() = DeviceRootSnapshot(
        deviceUid = "light-pro",
        title = "WRGB Pro Elite",
        availability = OwnerDeviceAvailability.REACHABLE,
        family = OwnerDeviceFamily.LIGHT,
        catalogState = DeviceRootCatalogState.VALID,
        productKey = DeviceLightThermalV1Contract.PRODUCT_KEY,
        fanOutputCount = DeviceLightThermalV1Contract.FAN_OUTPUT_CAPACITY,
        temperatureSensorCount = DeviceLightThermalV1Contract.TEMPERATURE_SENSOR_CAPACITY,
        supportedFeatures = listOf(LIGHT_FAN_CONTROL, LIGHT_TEMPERATURE_PROTECTION)
    )

    private companion object {
        const val LIGHT_FAN_CONTROL = "LIGHT_FAN_CONTROL"
        const val LIGHT_TEMPERATURE_PROTECTION = "LIGHT_TEMPERATURE_PROTECTION"
    }
}

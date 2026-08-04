package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceLightProtectionThresholdPolicy
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingTemperatureSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionRuntimeCapabilities
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceLightProtectionOperationsTest {

    @Test
    fun `maps valid runtime state into application values and edit policy`() {
        val snapshot = toDeviceLightProtectionSnapshot(
            available = true,
            coolingState = DeviceCoolingRuntimeState(
                temperature = DeviceCoolingTemperatureSnapshot(
                    sensorIndex = 0,
                    readingValid = true,
                    temperatureC = 54.25,
                    sampledAtMs = 1_000L
                )
            ),
            protectionStatus = protectionStatus()
        )

        assertEquals(54.25, snapshot.currentTemperatureCelsius ?: 0.0, 0.0)
        assertEquals(60.0, snapshot.thresholdCelsius ?: 0.0, 0.0)
        assertEquals(
            DeviceLightProtectionThresholdPolicy(
                currentCelsius = 60,
                minimumCelsius = 50,
                maximumCelsius = 70,
                stepCelsius = 1
            ),
            snapshot.thresholdPolicy
        )
        assertTrue(snapshot.loaded)
    }

    @Test
    fun `fails closed for invalid temperature and read only threshold`() {
        val snapshot = toDeviceLightProtectionSnapshot(
            available = true,
            coolingState = DeviceCoolingRuntimeState(
                temperature = DeviceCoolingTemperatureSnapshot(
                    sensorIndex = 0,
                    readingValid = false,
                    temperatureC = 54.25,
                    sampledAtMs = 1_000L
                )
            ),
            protectionStatus = protectionStatus(readOnly = true)
        )

        assertNull(snapshot.currentTemperatureCelsius)
        assertNull(snapshot.thresholdPolicy)
    }

    @Test
    fun `does not expose runtime values when catalog availability is false`() {
        val snapshot = toDeviceLightProtectionSnapshot(
            available = false,
            coolingState = DeviceCoolingRuntimeState(
                temperature = DeviceCoolingTemperatureSnapshot(
                    sensorIndex = 0,
                    readingValid = true,
                    temperatureC = 54.25,
                    sampledAtMs = 1_000L
                )
            ),
            protectionStatus = protectionStatus()
        )

        assertFalse(snapshot.available)
        assertNull(snapshot.currentTemperatureCelsius)
        assertNull(snapshot.thresholdCelsius)
        assertNull(snapshot.thresholdPolicy)
    }

    @Test
    fun `rejects missing non finite and mismatched firmware thresholds`() {
        assertTrue(
            firmwareThresholdMatchesRequest(
                returnedThresholdCelsius = 63.0,
                requestedThresholdCelsius = 63
            )
        )
        assertFalse(
            firmwareThresholdMatchesRequest(
                returnedThresholdCelsius = 62.0,
                requestedThresholdCelsius = 63
            )
        )
        assertFalse(
            firmwareThresholdMatchesRequest(
                returnedThresholdCelsius = null,
                requestedThresholdCelsius = 63
            )
        )
        assertFalse(
            firmwareThresholdMatchesRequest(
                returnedThresholdCelsius = Double.NaN,
                requestedThresholdCelsius = 63
            )
        )
    }

    private fun protectionStatus(
        readOnly: Boolean = false
    ): DeviceLightTemperatureProtectionStatus = DeviceLightTemperatureProtectionStatus(
        supported = true,
        temperatureProtection = DeviceLightTemperatureProtectionSnapshot(
            supported = true,
            active = true,
            thresholdEditable = true,
            thresholdC = 60.0,
            minimumC = 50.0,
            maximumC = 70.0
        ),
        runtime = DeviceLightTemperatureProtectionRuntimeCapabilities(
            module = "light",
            readOnly = readOnly,
            supportsStatusGet = true,
            supportsSet = !readOnly,
            event = "light.status.changed"
        )
    )
}

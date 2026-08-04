package com.aqua.aqualight.ui.tabs.devices.detail.settings

import com.aqua.aqualight.application.devices.DeviceLightProtectionSnapshot
import com.aqua.aqualight.application.devices.DeviceLightProtectionThresholdPolicy
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFamilySettingsStateTest {

    @Test
    fun `projects common device information into ready Settings state`() {
        val state = wrgbSnapshot().toDeviceFamilySettingsUiState()

        assertEquals("Living room light", state.deviceName)
        assertEquals("AQL-WPE-123456", state.serialNumber)
        assertEquals("2.0", state.hardwareRevision)
        assertEquals("1.2.3 / build 42", state.firmwareVersion)
        assertEquals(OwnerDeviceFamily.LIGHT, state.family)
        assertEquals(
            DeviceSettingsInformationLoadState.READY,
            state.informationLoadState
        )
    }

    @Test
    fun `projects light protection application values into ready state`() {
        val state = DeviceFamilySettingsUiState().withLightProtectionSnapshot(
            DeviceLightProtectionSnapshot(
                available = true,
                currentTemperatureCelsius = 54.25,
                thresholdCelsius = 60.0,
                thresholdPolicy = DeviceLightProtectionThresholdPolicy(
                    currentCelsius = 60,
                    minimumCelsius = 50,
                    maximumCelsius = 70,
                    stepCelsius = 1
                ),
                loaded = true
            )
        )

        assertTrue(state.showLightProtectionInventory)
        assertEquals(54.25, state.lightProtection.currentTemperatureCelsius ?: 0.0, 0.0)
        assertEquals(60.0, state.lightProtection.thresholdCelsius ?: 0.0, 0.0)
        assertEquals(
            DeviceTemperatureProtectionEditorUiState(
                currentCelsius = 60,
                minimumCelsius = 50,
                maximumCelsius = 70,
                stepCelsius = 1
            ),
            state.lightProtection.editor
        )
        assertEquals(
            DeviceLightProtectionLoadState.READY,
            state.lightProtection.loadState
        )
    }

    @Test
    fun `uses loaded contract to distinguish loading from unavailable values`() {
        val loading = DeviceFamilySettingsUiState().withLightProtectionSnapshot(
            DeviceLightProtectionSnapshot(
                available = true,
                currentTemperatureCelsius = 53.5,
                loaded = false
            )
        )
        val readyWithoutTemperature = loading.withLightProtectionSnapshot(
            DeviceLightProtectionSnapshot(
                available = true,
                thresholdCelsius = 60.0,
                loaded = true
            )
        )

        assertEquals(
            DeviceLightProtectionLoadState.LOADING,
            loading.lightProtection.loadState
        )
        assertEquals(53.5, loading.lightProtection.currentTemperatureCelsius ?: 0.0, 0.0)
        assertEquals(
            DeviceLightProtectionLoadState.READY,
            readyWithoutTemperature.lightProtection.loadState
        )
        assertEquals(null, readyWithoutTemperature.lightProtection.currentTemperatureCelsius)
    }

    @Test
    fun `keeps hardware revision loading until exact catalog proof exists`() {
        val state = wrgbSnapshot().copy(
            catalogState = DeviceRootCatalogState.INVALID,
            hardwareRevision = ""
        ).toDeviceFamilySettingsUiState()

        assertEquals("", state.hardwareRevision)
        assertEquals(
            DeviceSettingsInformationLoadState.LOADING,
            state.informationLoadState
        )
    }

    @Test
    fun `shows Light protection inventory only from application availability`() {
        val available = DeviceFamilySettingsUiState().withLightProtectionSnapshot(
            DeviceLightProtectionSnapshot(available = true)
        )
        val unavailable = available.withLightProtectionSnapshot(
            DeviceLightProtectionSnapshot(available = false)
        )

        assertTrue(available.showLightProtectionInventory)
        assertFalse(unavailable.showLightProtectionInventory)
    }

    @Test
    fun `accepts saved editor results only for the currently bound device`() {
        assertTrue(
            isSavedSettingsEditorResult(
                result = "saved",
                payloadId = "device-current",
                expectedPayloadId = "device-current",
                savedResult = "saved"
            )
        )
        assertFalse(
            isSavedSettingsEditorResult(
                result = "saved",
                payloadId = "device-before-recreation",
                expectedPayloadId = "device-current",
                savedResult = "saved"
            )
        )
        assertFalse(
            isSavedSettingsEditorResult(
                result = "cancelled",
                payloadId = "device-current",
                expectedPayloadId = "device-current",
                savedResult = "saved"
            )
        )
    }

    private fun wrgbSnapshot(): DeviceRootSnapshot = DeviceRootSnapshot(
        deviceUid = "device-wrgb-settings",
        title = "Living room light",
        availability = OwnerDeviceAvailability.REACHABLE,
        family = OwnerDeviceFamily.LIGHT,
        catalogState = DeviceRootCatalogState.VALID,
        productKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqualight.light.wrgb_pro_elite",
        model = "wrgb_pro_elite_120",
        serialNumber = "AQL-WPE-123456",
        hardwareRevision = "2.0",
        firmwareLabel = "1.2.3 / build 42",
        temperatureSensorCount = 1,
        supportedFeatures = listOf("LIGHT_TEMPERATURE_PROTECTION")
    )
}

package com.aqua.aqualight.ui.tabs.devices.detail.settings

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
    fun `creates Light protection inventory only for exact supported WRGB contract`() {
        val wrgbState = wrgbSnapshot().toDeviceFamilySettingsUiState()

        assertTrue(wrgbState.showLightProtectionInventory)
        assertEquals(
            LightTemperatureProtectionUiContract.DEFAULT_THRESHOLD_C,
            wrgbState.temperatureProtectionThresholdC,
            0.0
        )

        val rgbSlim = wrgbSnapshot().copy(
            model = "rgb_pro_slim",
            temperatureSensorCount = 0,
            supportedFeatures = emptyList()
        )
        assertFalse(rgbSlim.toDeviceFamilySettingsUiState().showLightProtectionInventory)
    }

    @Test
    fun `fails closed when Light feature or validated catalog proof is missing`() {
        val featureDrift = wrgbSnapshot().copy(supportedFeatures = emptyList())
        val invalidCatalog = wrgbSnapshot().copy(catalogState = DeviceRootCatalogState.INVALID)

        assertFalse(featureDrift.toDeviceFamilySettingsUiState().showLightProtectionInventory)
        assertFalse(invalidCatalog.toDeviceFamilySettingsUiState().showLightProtectionInventory)
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

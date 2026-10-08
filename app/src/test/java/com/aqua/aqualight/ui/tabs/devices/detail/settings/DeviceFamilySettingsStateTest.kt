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
        assertEquals("1.2.3", state.firmwareVersion)
        assertEquals(DeviceSettingsFirmwareLoadState.READY, state.firmwareLoadState)
        assertEquals(OwnerDeviceFamily.LIGHT, state.family)
        assertEquals(
            DeviceSettingsInformationLoadState.READY,
            state.informationLoadState
        )
    }

    @Test
    fun `rejects cached firmware without current catalog proof`() {
        val state = wrgbSnapshot().copy(
            catalogState = DeviceRootCatalogState.INVALID,
            firmwareLabel = "1.2.3 / cached build 42"
        ).toDeviceFamilySettingsUiState()

        assertEquals("", state.firmwareVersion)
        assertEquals(DeviceSettingsFirmwareLoadState.LOADING, state.firmwareLoadState)
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
    fun `does not substitute device uid when serial number is unavailable`() {
        val state = wrgbSnapshot().copy(serialNumber = "").toDeviceFamilySettingsUiState()

        assertEquals("", state.serialNumber)
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
        firmwareLabel = "1.2.3",
        temperatureSensorCount = 1,
        supportedFeatures = listOf("LIGHT_TEMPERATURE_PROTECTION")
    )
}

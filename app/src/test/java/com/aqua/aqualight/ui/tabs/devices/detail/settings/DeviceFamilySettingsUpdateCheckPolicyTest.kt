package com.aqua.aqualight.ui.tabs.devices.detail.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFamilySettingsUpdateCheckPolicyTest {

    @Test
    fun `automatic availability check runs only for passive refresh states`() {
        assertTrue(DeviceSettingsUpdateActionState.Idle.allowsAutomaticUpdateCheck())
        assertTrue(DeviceSettingsUpdateActionState.UpToDate.allowsAutomaticUpdateCheck())

        assertFalse(DeviceSettingsUpdateActionState.Checking.allowsAutomaticUpdateCheck())
        assertFalse(
            DeviceSettingsUpdateActionState.UpdateAvailable("2.0.0")
                .allowsAutomaticUpdateCheck()
        )
        assertFalse(
            DeviceSettingsUpdateActionState.UpdateInProgress(
                version = "2.0.0",
                progressPermille = 500
            ).allowsAutomaticUpdateCheck()
        )
        assertFalse(DeviceSettingsUpdateActionState.Unsupported.allowsAutomaticUpdateCheck())
    }

    @Test
    fun `completed check suppresses only immediate duplicate requests`() {
        val checkedAt = 10_000L

        assertTrue(
            isDeviceSettingsUpdateCheckDebounced(
                lastCheckedAtMillis = checkedAt,
                nowMillis = checkedAt
            )
        )
        assertTrue(
            isDeviceSettingsUpdateCheckDebounced(
                lastCheckedAtMillis = checkedAt,
                nowMillis = checkedAt + DEVICE_SETTINGS_UPDATE_CHECK_DEBOUNCE_MILLIS - 1L
            )
        )
        assertFalse(
            isDeviceSettingsUpdateCheckDebounced(
                lastCheckedAtMillis = checkedAt,
                nowMillis = checkedAt + DEVICE_SETTINGS_UPDATE_CHECK_DEBOUNCE_MILLIS
            )
        )
        assertFalse(
            isDeviceSettingsUpdateCheckDebounced(
                lastCheckedAtMillis = null,
                nowMillis = checkedAt
            )
        )
    }
}

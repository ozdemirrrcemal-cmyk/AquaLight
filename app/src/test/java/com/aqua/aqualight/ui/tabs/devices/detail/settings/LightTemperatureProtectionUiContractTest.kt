package com.aqua.aqualight.ui.tabs.devices.detail.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightTemperatureProtectionUiContractTest {

    @Test
    fun `matches the firmware default and inclusive limits`() {
        assertTrue(
            LightTemperatureProtectionUiContract.isAllowedThreshold(
                LightTemperatureProtectionUiContract.DEFAULT_THRESHOLD_C
            )
        )
        assertTrue(LightTemperatureProtectionUiContract.isAllowedThreshold(50))
        assertTrue(LightTemperatureProtectionUiContract.isAllowedThreshold(70))
        assertFalse(LightTemperatureProtectionUiContract.isAllowedThreshold(49))
        assertFalse(LightTemperatureProtectionUiContract.isAllowedThreshold(71))
    }
}

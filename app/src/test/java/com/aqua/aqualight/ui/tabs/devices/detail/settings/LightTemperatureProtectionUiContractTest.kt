package com.aqua.aqualight.ui.tabs.devices.detail.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightTemperatureProtectionUiContractTest {

    @Test
    fun `matches firmware default and inclusive limits`() {
        assertEquals(60.0, LightTemperatureProtectionUiContract.DEFAULT_THRESHOLD_C, 0.0)
        assertEquals(50.0, LightTemperatureProtectionUiContract.MINIMUM_THRESHOLD_C, 0.0)
        assertEquals(70.0, LightTemperatureProtectionUiContract.MAXIMUM_THRESHOLD_C, 0.0)
        assertTrue(LightTemperatureProtectionUiContract.isAllowedThreshold(50.0))
        assertTrue(LightTemperatureProtectionUiContract.isAllowedThreshold(70.0))
    }

    @Test
    fun `accepts localized decimal input only inside firmware limits`() {
        assertEquals(
            60.5,
            LightTemperatureProtectionUiContract.parseAllowedThreshold("60,5") ?: Double.NaN,
            0.0
        )
        assertEquals(
            60.5,
            LightTemperatureProtectionUiContract.parseAllowedThreshold("60.5") ?: Double.NaN,
            0.0
        )
        assertNull(LightTemperatureProtectionUiContract.parseAllowedThreshold("49.9"))
        assertNull(LightTemperatureProtectionUiContract.parseAllowedThreshold("70.1"))
        assertNull(LightTemperatureProtectionUiContract.parseAllowedThreshold("not-a-number"))
    }

    @Test
    fun `formats preview values without unnecessary trailing zeroes`() {
        assertEquals(
            "60",
            LightTemperatureProtectionUiContract.formatDisplay(60.0, Locale.US)
        )
        assertEquals(
            "60,5",
            LightTemperatureProtectionUiContract.formatDisplay(
                60.5,
                Locale.forLanguageTag("tr-TR")
            )
        )
        assertEquals("60", LightTemperatureProtectionUiContract.formatInput(60.0))
    }
}

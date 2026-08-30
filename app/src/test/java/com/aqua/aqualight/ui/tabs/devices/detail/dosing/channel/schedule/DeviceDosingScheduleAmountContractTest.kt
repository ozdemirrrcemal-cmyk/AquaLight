package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingScheduleAmountContractTest {

    @Test
    fun `ml input converts exactly to microliters`() {
        assertEquals(5_000L, DeviceDosingScheduleAmountContract.parseMicroliters("5"))
        assertEquals(5_250L, DeviceDosingScheduleAmountContract.parseMicroliters("5,250"))
        assertEquals(1L, DeviceDosingScheduleAmountContract.parseMicroliters("0.001"))
    }

    @Test
    fun `invalid or over-precise input is rejected instead of rounded`() {
        assertNull(DeviceDosingScheduleAmountContract.parseMicroliters(""))
        assertNull(DeviceDosingScheduleAmountContract.parseMicroliters("0"))
        assertNull(DeviceDosingScheduleAmountContract.parseMicroliters("-1"))
        assertNull(DeviceDosingScheduleAmountContract.parseMicroliters("0.0001"))
    }

    @Test
    fun `input formatting preserves supported precision`() {
        assertEquals("5.25", DeviceDosingScheduleAmountContract.formatInput(5_250L, Locale.US))
        assertEquals(3.125, DeviceDosingScheduleAmountContract.milliliters(3_125L), 0.0)
    }

    @Test
    fun `display formatting keeps two decimals and preserves meaningful third decimal`() {
        assertEquals("2.00", DeviceDosingScheduleAmountContract.formatDisplay(2_000L, Locale.US))
        assertEquals("2.50", DeviceDosingScheduleAmountContract.formatDisplay(2_500L, Locale.US))
        assertEquals("2.125", DeviceDosingScheduleAmountContract.formatDisplay(2_125L, Locale.US))

        val turkish = Locale.forLanguageTag("tr-TR")
        assertEquals("2,00", DeviceDosingScheduleAmountContract.formatDisplay(2_000L, turkish))
        assertEquals("2,50", DeviceDosingScheduleAmountContract.formatDisplay(2_500L, turkish))
        assertEquals("2,125", DeviceDosingScheduleAmountContract.formatDisplay(2_125L, turkish))
    }

    @Test
    fun `computed display amounts use the same commercial precision`() {
        assertEquals("0.60", DeviceDosingScheduleAmountContract.formatDisplay(0.6, Locale.US))
        assertEquals("0.125", DeviceDosingScheduleAmountContract.formatDisplay(0.125, Locale.US))
        assertEquals("0.333", DeviceDosingScheduleAmountContract.formatDisplay(1.0 / 3.0, Locale.US))

        val turkish = Locale.forLanguageTag("tr-TR")
        assertEquals("0,60", DeviceDosingScheduleAmountContract.formatDisplay(0.6, turkish))
        assertEquals("0,125", DeviceDosingScheduleAmountContract.formatDisplay(0.125, turkish))
        assertEquals("0,333", DeviceDosingScheduleAmountContract.formatDisplay(1.0 / 3.0, turkish))
    }
}

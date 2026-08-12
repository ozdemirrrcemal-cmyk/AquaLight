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
}

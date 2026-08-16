package com.aqua.aqualight.application.devices.dosing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingManualDoseDraftPolicyTest {
    @Test
    fun `manual dose parsing stays in application and preserves exact microliters`() {
        assertEquals(2_500L, DeviceDosingManualDoseDraftPolicy.parseMicroliters("2.500"))
        assertEquals(2_500L, DeviceDosingManualDoseDraftPolicy.parseMicroliters(" 2,500 "))
        assertEquals(1L, DeviceDosingManualDoseDraftPolicy.parseMicroliters("0.001"))
    }

    @Test
    fun `manual dose draft rejects blank non positive and unsupported precision`() {
        assertNull(DeviceDosingManualDoseDraftPolicy.parseMicroliters(""))
        assertNull(DeviceDosingManualDoseDraftPolicy.parseMicroliters("0"))
        assertNull(DeviceDosingManualDoseDraftPolicy.parseMicroliters("-1"))
        assertNull(DeviceDosingManualDoseDraftPolicy.parseMicroliters("0.0001"))
        assertNull(DeviceDosingManualDoseDraftPolicy.parseMicroliters("not-a-number"))
    }
}

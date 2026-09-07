package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoolingPercentPresentationTest {

    @Test
    fun `continuous firmware percent is rounded only for display`() {
        assertEquals(36, 35.95.toCoolingDisplayPercentOrNull())
        assertEquals(38, 37.77778.toCoolingDisplayPercentOrNull())
    }

    @Test
    fun `missing or non finite runtime percent stays unavailable`() {
        assertNull(null.toCoolingDisplayPercentOrNull())
        assertNull(Double.NaN.toCoolingDisplayPercentOrNull())
        assertNull(Double.POSITIVE_INFINITY.toCoolingDisplayPercentOrNull())
        assertNull((-0.01).toCoolingDisplayPercentOrNull())
        assertNull(100.01.toCoolingDisplayPercentOrNull())
    }
}

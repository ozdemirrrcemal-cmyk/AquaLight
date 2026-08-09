package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingPumpCountResolverTest {

    @Test
    fun `uses exact two channel catalog metadata`() {
        assertEquals(2, resolveDosingPumpCount(channelCount = 2))
    }

    @Test
    fun `uses exact four channel catalog metadata`() {
        assertEquals(4, resolveDosingPumpCount(channelCount = 4))
    }

    @Test
    fun `unresolved catalog metadata does not guess a product`() {
        assertEquals(0, resolveDosingPumpCount(channelCount = 0))
    }

    @Test
    fun `unsupported channel count fails closed`() {
        assertEquals(0, resolveDosingPumpCount(channelCount = 3))
    }

    @Test
    fun `final root composition accepts only exact two pump product`() {
        assertEquals(2, exactDosingPumpCountOrNull(2))
    }

    @Test
    fun `final root composition accepts only exact four pump product`() {
        assertEquals(4, exactDosingPumpCountOrNull(4))
    }

    @Test
    fun `final root composition rejects unsupported pump count`() {
        assertNull(exactDosingPumpCountOrNull(3))
    }
}

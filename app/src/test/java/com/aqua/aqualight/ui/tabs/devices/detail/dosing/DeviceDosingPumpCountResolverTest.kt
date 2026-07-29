package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDosingPumpCountResolverTest {

    @Test
    fun `uses explicit two channel metadata`() {
        assertEquals(2, resolveDosingPumpCount(channelCount = 2, modelLabel = "dose-pro-4"))
    }

    @Test
    fun `uses explicit four channel metadata`() {
        assertEquals(4, resolveDosingPumpCount(channelCount = 4, modelLabel = "dose-pro-2"))
    }

    @Test
    fun `falls back to Pro 2 model identity`() {
        assertEquals(2, resolveDosingPumpCount(channelCount = 0, modelLabel = "Dosing Pro 2"))
    }

    @Test
    fun `defaults unknown dosing models to four pumps`() {
        assertEquals(4, resolveDosingPumpCount(channelCount = 0, modelLabel = "Dosing"))
    }

    @Test
    fun `keeps two pump Compose layout`() {
        assertEquals(2, normalizeDosingPumpCount(2))
    }

    @Test
    fun `normalizes unsupported Compose layouts to four pumps`() {
        assertEquals(4, normalizeDosingPumpCount(3))
    }
}

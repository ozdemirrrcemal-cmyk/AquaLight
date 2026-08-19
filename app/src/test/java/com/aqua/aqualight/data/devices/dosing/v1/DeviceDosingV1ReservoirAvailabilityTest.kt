package com.aqua.aqualight.data.devices.dosing.v1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1ReservoirAvailabilityTest {

    @Test
    fun `firmware unavailable remaining sentinel survives as explicit application availability`() {
        val channel = DeviceDosingV1TestFixtures.channelStatus()
        channel.getJSONObject("channel")
            .getJSONObject("reservoir")
            .put("remainingMl", -1.0)
            .put("remainingPercent", -1.0)
            .put("accountingCertain", true)

        val detail = DeviceDosingV1StatusParser.parseChannel(channel).channel
        val mapped = DeviceDosingV1ChannelSnapshotMapper.reservoir(
            detail = detail,
            lowLevelAlertEnabled = false
        )

        assertTrue(mapped.accountingCertain)
        assertFalse(mapped.remainingAvailable)
        assertEquals(0L, mapped.remainingMicroliters)
    }

    @Test
    fun `firmware zero remaining remains a trustworthy zero when sentinel is absent`() {
        val channel = DeviceDosingV1TestFixtures.channelStatus()
        channel.getJSONObject("channel")
            .getJSONObject("reservoir")
            .put("remainingMl", 0.0)
            .put("remainingPercent", 0.0)
            .put("accountingCertain", true)
            .put("lowLevelActive", true)

        val detail = DeviceDosingV1StatusParser.parseChannel(channel).channel
        val mapped = DeviceDosingV1ChannelSnapshotMapper.reservoir(
            detail = detail,
            lowLevelAlertEnabled = false
        )

        assertTrue(mapped.remainingAvailable)
        assertEquals(0L, mapped.remainingMicroliters)
        assertTrue(mapped.lowLevelActive)
    }
}

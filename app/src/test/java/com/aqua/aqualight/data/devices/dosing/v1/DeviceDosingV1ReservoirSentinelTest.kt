package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceDosingV1ReservoirSentinelTest {
    @Test
    fun `firmware unavailable reservoir sentinel does not invalidate central snapshot mapping`() {
        val globalJson = DeviceDosingV1TestFixtures.globalStatus().also { status ->
            status.getJSONArray("channels")
                .getJSONObject(0)
                .getJSONObject("reservoir")
                .put("trackingEnabled", false)
                .put("remainingMl", -1.0)
                .put("accountingCertain", true)
                .put("lowLevelActive", false)
        }
        val channelJson = DeviceDosingV1TestFixtures.channelStatus().also { status ->
            status.getJSONObject("channel")
                .getJSONObject("reservoir")
                .put("trackingEnabled", false)
                .put("capacityMl", -1.0)
                .put("remainingMl", -1.0)
                .put("accountingCertain", true)
                .put("lowLevelActive", false)
                .put("remainingPercent", -1.0)
        }

        val mapped = DeviceDosingV1SnapshotMapper.map(
            DeviceDosingV1SnapshotDocuments(
                deviceUid = DeviceUid("AQL-DOSING-FACTORY-STATE"),
                slotId = "dosing:channel1",
                global = DeviceDosingV1StatusParser.parseGlobal(globalJson),
                channelStatus = DeviceDosingV1StatusParser.parseChannel(channelJson),
                progressStatus = DeviceDosingV1StatusParser.parseProgress(
                    DeviceDosingV1TestFixtures.progressStatus()
                ),
                lowLevelAlertEnabled = false
            )
        )

        assertFalse(mapped.channel.reservoir.trackingEnabled)
        assertEquals(0L, mapped.channel.reservoir.capacityMicroliters)
        assertEquals(0L, mapped.channel.reservoir.remainingMicroliters)
    }

    @Test
    fun `negative reservoir values other than firmware sentinel still fail closed`() {
        val detail = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus().also { status ->
                status.getJSONObject("channel")
                    .getJSONObject("reservoir")
                    .put("capacityMl", -0.5)
            }
        ).channel

        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1ChannelSnapshotMapper.reservoir(detail, lowLevelAlertEnabled = false)
        }
    }
}

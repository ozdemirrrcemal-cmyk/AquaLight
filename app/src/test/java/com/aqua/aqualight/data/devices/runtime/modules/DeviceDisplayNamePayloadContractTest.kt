package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingFanConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingChannelConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerChannelConfig
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerConfigApplyPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDisplayNamePayloadContractTest {

    @Test
    fun `timer blank display name serializes as null and duplicate keys fail`() {
        val item = DeviceTimerChannelConfig(
            channelKey = " relay1 ",
            displayName = "   "
        ).toJson()

        assertEquals("relay1", item.getString("channelKey"))
        assertTrue(item.isNull("displayName"))
        assertTrue(
            runCatching {
                DeviceTimerConfigApplyPayload(
                    channels = listOf(
                        DeviceTimerChannelConfig("relay1", displayName = "A"),
                        DeviceTimerChannelConfig(" relay1 ", displayName = "B")
                    )
                )
            }.isFailure
        )
    }

    @Test
    fun `dosing blank display name serializes as null and duplicate keys fail`() {
        val item = DeviceDosingChannelConfig(
            channelKey = " pump1 ",
            displayName = ""
        ).toJson()

        assertEquals("pump1", item.getString("channelKey"))
        assertTrue(item.isNull("displayName"))
        assertTrue(
            runCatching {
                DeviceDosingConfigApplyPayload(
                    channels = listOf(
                        DeviceDosingChannelConfig("pump1", displayName = "A"),
                        DeviceDosingChannelConfig("pump1", displayName = "B")
                    )
                )
            }.isFailure
        )
    }

    @Test
    fun `cooling fan item has exact key and nullable display-name fields`() {
        val json = DeviceCoolingConfigApplyPayload(
            fans = listOf(
                DeviceCoolingFanConfig(" FAN1 ", "Sol Fan"),
                DeviceCoolingFanConfig("fan2", "  ")
            )
        ).toJson()
        val fans = json.getJSONArray("fans")

        assertEquals("fan1", fans.getJSONObject(0).getString("fanKey"))
        assertEquals("Sol Fan", fans.getJSONObject(0).getString("displayName"))
        assertEquals("fan2", fans.getJSONObject(1).getString("fanKey"))
        assertTrue(fans.getJSONObject(1).isNull("displayName"))
        assertTrue(json.getBoolean("save"))
    }

    @Test
    fun `all commercial channel names enforce 32 UTF-8 bytes`() {
        val overLimit = "ş".repeat(17)

        assertTrue(
            runCatching {
                DeviceTimerChannelConfig("relay1", displayName = overLimit)
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceDosingChannelConfig("pump1", displayName = overLimit)
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceCoolingFanConfig("fan1", overLimit)
            }.isFailure
        )
    }

    @Test
    fun `cooling rejects duplicate fan keys before serialization`() {
        assertTrue(
            runCatching {
                DeviceCoolingConfigApplyPayload(
                    fans = listOf(
                        DeviceCoolingFanConfig("fan1", "A"),
                        DeviceCoolingFanConfig("FAN1", "B")
                    )
                )
            }.isFailure
        )
    }
}

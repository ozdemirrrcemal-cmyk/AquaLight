package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightLibraryDescriptorMappingTest {

    @Test
    fun `firmware descriptors change card presentation without changing stored scene values`() {
        val parsed = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        val status = parsed.copy(
            channels = parsed.channels.mapIndexed { index, descriptor ->
                descriptor.copy(
                    displayName = "Firmware channel $index",
                    displayColorRgb = CUSTOM_COLORS[index]
                )
            }
        )

        val target = status.toLibraryTarget(DeviceUid(DEVICE_UID))
        val entry = storedManual().toApplicationEntry()
        val scene = (entry.payload as DeviceLightLibraryPayload.Manual).scene

        assertEquals(
            listOf("Firmware channel 0", "Firmware channel 1", "Firmware channel 2", "Firmware channel 3"),
            target.channelDescriptors.map { descriptor -> descriptor.displayName }
        )
        assertEquals(CUSTOM_COLORS, target.channelDescriptors.map { it.displayColorRgb })
        assertEquals(20, scene.channels.getValue(DeviceLightLibraryChannel.RED))
        assertEquals(30, scene.channels.getValue(DeviceLightLibraryChannel.GREEN))
        assertEquals(40, scene.channels.getValue(DeviceLightLibraryChannel.BLUE))
        assertEquals(50, scene.channels.getValue(DeviceLightLibraryChannel.WHITE))
    }

    @Test
    fun `custom entry maps its persisted curve without runtime installation state`() {
        val entry = storedCustom().toApplicationEntry()
        val payload = entry.payload as DeviceLightLibraryPayload.Custom

        assertEquals(WEEKDAYS_MASK, payload.weekdaysMask)
        assertEquals(CUSTOM_POINT_TIME_MS, payload.points.single().timeMs)
        assertEquals(
            PERCENTS,
            DeviceLightLibraryChannel.entries.map { channel ->
                payload.points.single().scene.channels.getValue(channel)
            }
        )
    }

    private fun storedManual(): StoredDeviceLightLibraryEntry =
        StoredDeviceLightLibraryEntry.newBuilder()
            .setId("manual-scene")
            .setOwnerUid("owner")
            .setDisplayName("Scene")
            .setNormalizedName("scene")
            .setProductKey("LIGHT_WRGB_PRO_ELITE")
            .addAllChannelKeys(CHANNEL_KEYS)
            .setCreatedAtMillis(1L)
            .setUpdatedAtMillis(1L)
            .setKind(StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_MANUAL)
            .setManual(
                StoredDeviceLightManualScene.newBuilder()
                    .addAllChannels(
                        CHANNEL_KEYS.zip(PERCENTS).map { (key, percent) ->
                            StoredDeviceLightChannelValue.newBuilder()
                                .setChannelKey(key)
                                .setPercent(percent)
                                .build()
                        }
                    )
            )
            .build()

    private fun storedCustom(): StoredDeviceLightLibraryEntry =
        StoredDeviceLightLibraryEntry.newBuilder()
            .setId("custom-curve")
            .setOwnerUid("owner")
            .setDisplayName("Curve")
            .setNormalizedName("curve")
            .setProductKey("LIGHT_WRGB_PRO_ELITE")
            .addAllChannelKeys(CHANNEL_KEYS)
            .setCreatedAtMillis(1L)
            .setUpdatedAtMillis(1L)
            .setKind(StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_CUSTOM)
            .setCustom(
                StoredDeviceLightCustomCurve.newBuilder()
                    .setWeekdaysMask(WEEKDAYS_MASK)
                    .addPoints(
                        StoredDeviceLightCustomPoint.newBuilder()
                            .setTimeMs(CUSTOM_POINT_TIME_MS)
                            .addAllChannels(
                                CHANNEL_KEYS.zip(PERCENTS).map { (key, percent) ->
                                    StoredDeviceLightChannelValue.newBuilder()
                                        .setChannelKey(key)
                                        .setPercent(percent)
                                        .build()
                                }
                            )
                    )
            )
            .build()

    private companion object {
        const val DEVICE_UID = "descriptor-light"
        val CHANNEL_KEYS = listOf("redPercent", "greenPercent", "bluePercent", "whitePercent")
        val PERCENTS = listOf(20, 30, 40, 50)
        val CUSTOM_COLORS = listOf(0xA10000, 0x00A100, 0x0000A1, 0xA1A1A1)
        const val WEEKDAYS_MASK = 31
        const val CUSTOM_POINT_TIME_MS = 43_200_000L
    }
}

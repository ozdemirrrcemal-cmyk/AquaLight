package com.aqua.aqualight.data.devices.light.manual

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceLightManualRuntimeProjectionTest {

    @Test
    fun `manual snapshot projects firmware channel metadata in firmware order`() {
        val status = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())

        val snapshot = status.toManualSnapshot(
            uid = DeviceUid(DEVICE_UID),
            firmwareWriteAuthoritative = false
        )

        assertEquals(
            listOf(
                DeviceLightManualChannel.RED,
                DeviceLightManualChannel.GREEN,
                DeviceLightManualChannel.BLUE,
                DeviceLightManualChannel.WHITE
            ),
            snapshot.channelDescriptors.map { descriptor -> descriptor.channel }
        )
        assertEquals(
            status.channels.map { descriptor -> descriptor.displayName },
            snapshot.channelDescriptors.map { descriptor -> descriptor.displayName }
        )
        assertEquals(
            status.channels.map { descriptor -> descriptor.displayColorRgb },
            snapshot.channelDescriptors.map { descriptor -> descriptor.displayColorRgb }
        )
        assertEquals(
            status.manual.scene.percents.values.toList(),
            snapshot.scene.channels.values.toList()
        )
        val displayRgb = checkNotNull(status.color.displayRgb)
        assertEquals(
            (displayRgb.red shl RED_SHIFT) or
                (displayRgb.green shl GREEN_SHIFT) or
                displayRgb.blue,
            snapshot.estimatedPowerDisplayColorRgb
        )
        assertFalse(snapshot.firmwareWriteAuthoritative)
    }

    private companion object {
        const val DEVICE_UID = "manual-projection-light"
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}

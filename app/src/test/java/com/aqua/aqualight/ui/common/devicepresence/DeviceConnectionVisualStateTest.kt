package com.aqua.aqualight.ui.common.devicepresence

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceConnectionVisualStateTest {

    @Test
    fun `online uses the shared positive connection contract`() {
        val state = DeviceConnectionVisualState.ONLINE

        assertEquals(R.color.aqua_device_connection_online, state.tintColorRes)
        assertEquals(R.string.device_online, state.statusLabelRes)
        assertEquals(
            R.string.device_connection_online_content_description,
            state.accessibilityLabelRes
        )
    }

    @Test
    fun `all non ready states use the same commercial offline presentation`() {
        listOf(
            DeviceConnectionVisualState.CONNECTING,
            DeviceConnectionVisualState.WARNING,
            DeviceConnectionVisualState.OFFLINE
        ).forEach { state ->
            assertEquals(R.color.aqua_device_connection_offline, state.tintColorRes)
            assertEquals(R.string.device_offline, state.statusLabelRes)
            assertEquals(
                R.string.device_connection_offline_content_description,
                state.accessibilityLabelRes
            )
        }
    }
}

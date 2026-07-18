package com.aqua.aqualight.ui.common.devicepresence

import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePresencePresentationMapperTest {

    @Test
    fun `authenticated runtime is the only normal online control state`() {
        assertEquals(
            R.string.device_online,
            DevicePresencePresentationMapper.availabilityLabelRes(DeviceOnlineState.AUTHENTICATED)
        )
        assertTrue(
            DevicePresencePresentationMapper.isReachable(DeviceOnlineState.AUTHENTICATED)
        )
    }

    @Test
    fun `fresh UDP presence remains visually offline until authentication`() {
        assertEquals(
            R.string.device_offline,
            DevicePresencePresentationMapper.availabilityLabelRes(DeviceOnlineState.ONLINE_LAN)
        )
        assertTrue(
            DevicePresencePresentationMapper.isConnecting(DeviceOnlineState.ONLINE_LAN)
        )
        assertFalse(
            DevicePresencePresentationMapper.isReachable(DeviceOnlineState.ONLINE_LAN)
        )
    }

    @Test
    fun `websocket handshake is internal and never exposes connecting label`() {
        assertEquals(
            R.string.device_offline,
            DevicePresencePresentationMapper.availabilityLabelRes(DeviceOnlineState.CONNECTING_WS)
        )
        assertTrue(
            DevicePresencePresentationMapper.isConnecting(DeviceOnlineState.CONNECTING_WS)
        )
        assertFalse(
            DevicePresencePresentationMapper.isReachable(DeviceOnlineState.CONNECTING_WS)
        )
    }

    @Test
    fun `terminal and local network failures remain offline`() {
        listOf(
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.ERROR
        ).forEach { state ->
            assertEquals(
                R.string.device_offline,
                DevicePresencePresentationMapper.availabilityLabelRes(state)
            )
            assertFalse(DevicePresencePresentationMapper.isReachable(state))
            assertFalse(DevicePresencePresentationMapper.isConnecting(state))
        }
    }
}

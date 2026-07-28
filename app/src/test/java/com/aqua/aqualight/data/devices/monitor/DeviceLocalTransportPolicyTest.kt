package com.aqua.aqualight.data.devices.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLocalTransportPolicyTest {

    @Test
    fun `wifi or ethernet provides a local device path`() {
        assertTrue(
            DeviceLocalTransportPolicy.isLocalTransport(
                hasWifi = true,
                hasEthernet = false,
                isNotVpn = true
            )
        )
        assertTrue(
            DeviceLocalTransportPolicy.isLocalTransport(
                hasWifi = false,
                hasEthernet = true,
                isNotVpn = true
            )
        )
    }

    @Test
    fun `cellular without wifi is not treated as a local device path`() {
        assertFalse(
            DeviceLocalTransportPolicy.isLocalTransport(
                hasWifi = false,
                hasEthernet = false,
                isNotVpn = true
            )
        )
    }

    @Test
    fun `vpn path is rejected even when it reports wifi as an underlying transport`() {
        assertFalse(
            DeviceLocalTransportPolicy.isLocalTransport(
                hasWifi = true,
                hasEthernet = false,
                isNotVpn = false
            )
        )
    }

    @Test
    fun `android blocked local path is unusable`() {
        assertFalse(
            DeviceLocalTransportPolicy.isUsableLocalPath(
                hasLocalTransport = true,
                isBlocked = true
            )
        )
        assertTrue(
            DeviceLocalTransportPolicy.isUsableLocalPath(
                hasLocalTransport = true,
                isBlocked = false
            )
        )
    }
}

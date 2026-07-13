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
                hasEthernet = false
            )
        )
        assertTrue(
            DeviceLocalTransportPolicy.isLocalTransport(
                hasWifi = false,
                hasEthernet = true
            )
        )
    }

    @Test
    fun `cellular or vpn without wifi is not treated as a local device path`() {
        assertFalse(
            DeviceLocalTransportPolicy.isLocalTransport(
                hasWifi = false,
                hasEthernet = false
            )
        )
    }
}

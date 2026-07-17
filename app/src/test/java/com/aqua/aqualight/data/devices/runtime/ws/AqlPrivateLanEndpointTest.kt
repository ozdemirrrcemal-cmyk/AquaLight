package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.net.UnknownHostException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AqlPrivateLanEndpointTest {

    @Test
    fun `route hides validated private ip behind app-owned hostname`() {
        val route = requireNotNull(
            AqlPrivateLanEndpoint.route(
                DeviceUid("AQL-WPE-336172"),
                DeviceRuntimeEndpoint(ip = "192.168.1.42", wsPort = 80)
            )
        )

        assertTrue(route.url.startsWith("ws://aql-"))
        assertTrue(route.url.endsWith(".device.aql.local:80/aql/v2/ws"))
        assertFalse(route.url.contains("192.168.1.42"))
        assertArrayEquals(
            byteArrayOf(192.toByte(), 168.toByte(), 1, 42),
            AqlPrivateLanDns(route).lookup(route.syntheticHostname).single().address
        )

        try {
            AqlPrivateLanDns(route).lookup("attacker.example")
            fail("Expected DNS isolation failure")
        } catch (_: UnknownHostException) {
            Unit
        }
    }

    @Test
    fun `public addresses and legacy protocol endpoints are rejected`() {
        val deviceUid = DeviceUid("AQL-WPE-336172")
        assertFalse(
            DeviceRuntimeEndpoint(ip = "8.8.8.8", wsPort = 80).hasWebSocketEndpoint
        )
        assertFalse(
            DeviceRuntimeEndpoint(
                ip = "192.168.1.42",
                wsPort = 80,
                wsPath = "/aql/v1/ws"
            ).hasWebSocketEndpoint
        )
        assertFalse(
            DeviceRuntimeEndpoint(
                ip = "192.168.1.42",
                wsPort = 80,
                wsProtocol = "aql.ws.v1",
                wsProtocolVersion = 1
            ).hasWebSocketEndpoint
        )
        assertTrue(
            AqlPrivateLanEndpoint.route(
                deviceUid,
                DeviceRuntimeEndpoint(
                    ip = "10.0.0.4",
                    wsPort = 8080,
                    wsPath = AqlWsContract.DEFAULT_PATH
                )
            ) != null
        )
    }
}

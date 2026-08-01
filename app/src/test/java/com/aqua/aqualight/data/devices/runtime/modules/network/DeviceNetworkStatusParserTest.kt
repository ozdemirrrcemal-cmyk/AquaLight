package com.aqua.aqualight.data.devices.runtime.modules.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNetworkStatusParserTest {
    @Test
    fun `parses current firmware network status exactly`() {
        val status = DeviceNetworkStatusParser.parse(statusJson())

        assertEquals("client_and_setup_ap", status.wifiMode)
        assertTrue(status.client.connected)
        assertEquals("192.168.1.42", status.discovery.currentIp)
        assertEquals(80, status.runtime.wsPort)
    }

    @Test
    fun `rejects stale legacy network payload`() {
        val legacy = JSONObject()
            .put("connected", true)
            .put("mode", 1)
            .put("rssi", -50)
            .put("channel", 6)
            .put("staHostname", "aqualight")

        assertTrue(runCatching { DeviceNetworkStatusParser.parse(legacy) }.isFailure)
    }

    @Test
    fun `rejects mode flags that disagree with firmware mode code`() {
        val invalid = statusJson().put("stationEnabled", false)

        assertTrue(runCatching { DeviceNetworkStatusParser.parse(invalid) }.isFailure)
    }

    private fun statusJson(): JSONObject = JSONObject()
        .put("ip", "192.168.1.42")
        .put("macAddress", "AA:BB:CC:DD:EE:FF")
        .put("wifiModeCode", 3)
        .put("wifiMode", "client_and_setup_ap")
        .put("stationEnabled", true)
        .put("setupApEnabled", true)
        .put("clientConnected", true)
        .put("setupApActive", true)
        .put("uptimeMs", 12_000L)
        .put(
            "client",
            JSONObject()
                .put("enabled", true)
                .put("configured", true)
                .put("ssid", "Home WiFi")
                .put("bssidConfigured", false)
                .put("channel", 6)
                .put("connected", true)
                .put("state", "connected")
                .put("wifiStatus", 3)
                .put("ip", "192.168.1.42")
                .put("gateway", "192.168.1.1")
                .put("subnet", "255.255.255.0")
                .put("dns", "192.168.1.1")
                .put("rssi", -51)
                .put("lastWifiEvent", 7)
                .put("lastDisconnectReason", 0)
                .put("lastDisconnectReasonName", "none")
                .put("lastDisconnectAgeMs", 0L)
                .put("lastGotIpAgeMs", 3_000L)
                .put("nextRetryRemainingMs", 0L)
                .put("connectionInProgress", false)
        )
        .put(
            "setupAp",
            JSONObject()
                .put("enabled", true)
                .put("active", true)
                .put("ssid", "AquaLight-Setup")
                .put("ip", "192.168.4.1")
                .put("stationCount", 0)
        )
        .put(
            "discovery",
            JSONObject()
                .put("ready", true)
                .put("port", 45_454)
                .put("broadcastIp", "192.168.1.255")
                .put("currentIp", "192.168.1.42")
                .put("payloadSize", 512)
                .put("lastRefreshMs", 10_000L)
                .put("lastPacketRejectedMs", 0L)
                .put("rejectedPacketCount", 0L)
        )
        .put(
            "runtime",
            JSONObject()
                .put("transport", "websocket")
                .put("wsPort", 80)
                .put("wsPath", "/aql/v1/ws")
                .put("wsProtocol", "aql.ws.v1")
                .put("wsProtocolVersion", 1)
        )
}

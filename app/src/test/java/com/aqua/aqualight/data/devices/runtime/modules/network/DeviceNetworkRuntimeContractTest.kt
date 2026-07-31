package com.aqua.aqualight.data.devices.runtime.modules.network

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNetworkRuntimeContractTest {

    @Test
    fun `parses exact connected client status without using UDP mode names`() {
        val status = DeviceNetworkStatusParser.parse(connectedStatus())

        assertEquals(DeviceNetworkWifiMode.CLIENT, status.wifiMode)
        assertEquals(DeviceNetworkClientState.GOT_IP, status.client.state)
        assertEquals(DeviceNetworkDisconnectReason.NONE, status.client.lastDisconnectReasonName)
        assertTrue(status.clientConnected)
        assertTrue(status.discovery.ready)
        assertEquals("192.168.1.42", status.ip)
    }

    @Test
    fun `parses setup AP status as a separate WebSocket network mode`() {
        val status = DeviceNetworkStatusParser.parse(setupApStatus())

        assertEquals(DeviceNetworkWifiMode.SETUP_AP, status.wifiMode)
        assertFalse(status.stationEnabled)
        assertTrue(status.setupApEnabled)
        assertTrue(status.setupApActive)
        assertEquals("192.168.4.1", status.ip)
    }

    @Test
    fun `discovery not ready uses unspecified discovery IP without changing active client IP`() {
        val data = connectedStatus().apply {
            getJSONObject("discovery")
                .put("ready", false)
                .put("broadcastIp", "255.255.255.255")
                .put("currentIp", "0.0.0.0")
        }

        val status = DeviceNetworkStatusParser.parse(data)
        assertFalse(status.discovery.ready)
        assertEquals("192.168.1.42", status.ip)
        assertEquals("0.0.0.0", status.discovery.currentIp)
    }

    @Test
    fun `rejects UDP enum coercion unknown fields and inconsistent mode flags`() {
        val udpMode = connectedStatus().put("wifiMode", "sta")
        val coercedBoolean = connectedStatus().put("stationEnabled", "true")
        val unknownRoot = connectedStatus().put("legacyNetwork", true)
        val wrongCode = connectedStatus().put("wifiModeCode", 3)
        val mismatchedConnected = connectedStatus().put("clientConnected", false)

        listOf(udpMode, coercedBoolean, unknownRoot, wrongCode, mismatchedConnected).forEach { invalid ->
            assertTrue(runCatching { DeviceNetworkStatusParser.parse(invalid) }.isFailure)
        }
    }

    @Test
    fun `rejects invalid runtime discovery and address fields`() {
        val wrongPath = connectedStatus().apply {
            getJSONObject("runtime").put("wsPath", "/ws")
        }
        val oversizedDiscovery = connectedStatus().apply {
            getJSONObject("discovery").put("payloadSize", 769)
        }
        val invalidIp = connectedStatus().put("ip", "192.168.001.42")
        val invalidMac = connectedStatus().put("macAddress", "not-a-mac")
        val readyIpMismatch = connectedStatus().apply {
            getJSONObject("discovery").put("currentIp", "192.168.1.99")
        }

        listOf(wrongPath, oversizedDiscovery, invalidIp, invalidMac, readyIpMismatch).forEach { invalid ->
            assertTrue(runCatching { DeviceNetworkStatusParser.parse(invalid) }.isFailure)
        }
    }

    @Test
    fun `typed network repository sends an empty request and parses the exact response`() =
        runBlocking {
            val gateway = RespondingGateway(connectedStatus())
            val repository = DeviceNetworkRuntimeRepository(gateway)

            val outcome = repository.requestStatus(DEVICE_UID)
                as DeviceRuntimeCommandOutcome.Success

            assertEquals(0, gateway.encodedData?.length())
            assertEquals(DeviceNetworkWifiMode.CLIENT, outcome.value.wifiMode)
            assertEquals(GENERATION, outcome.generation)
        }

    private class RespondingGateway(
        private val responseData: JSONObject
    ) : DeviceRuntimeCommandGateway {
        var encodedData: JSONObject? = null
            private set

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            encodedData = command.encodeData()
            val response = AqlWsIncomingMessage.Response(
                id = "network-status-test",
                type = AqlWsContract.TYPE_RESPONSE,
                module = command.module,
                action = command.action,
                data = JSONObject(responseData.toString()),
                ok = true,
                statusCode = 200
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = response.id,
                generation = GENERATION,
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }
    }

    private fun connectedStatus(): JSONObject = JSONObject()
        .put("ip", "192.168.1.42")
        .put("macAddress", "AA:BB:CC:DD:EE:FF")
        .put("wifiModeCode", 1)
        .put("wifiMode", "client")
        .put("stationEnabled", true)
        .put("setupApEnabled", false)
        .put("clientConnected", true)
        .put("setupApActive", false)
        .put("uptimeMs", 123_456)
        .put(
            "client",
            JSONObject()
                .put("enabled", true)
                .put("configured", true)
                .put("ssid", "Aqua LAN")
                .put("bssidConfigured", false)
                .put("channel", 6)
                .put("connected", true)
                .put("state", "gotIp")
                .put("wifiStatus", 3)
                .put("ip", "192.168.1.42")
                .put("gateway", "192.168.1.1")
                .put("subnet", "255.255.255.0")
                .put("dns", "192.168.1.1")
                .put("rssi", -55)
                .put("lastWifiEvent", 7)
                .put("lastDisconnectReason", 0)
                .put("lastDisconnectReasonName", "none")
                .put("lastDisconnectAgeMs", 0)
                .put("lastGotIpAgeMs", 12_000)
                .put("nextRetryRemainingMs", 0)
                .put("connectionInProgress", false)
        )
        .put(
            "setupAp",
            JSONObject()
                .put("enabled", false)
                .put("active", false)
                .put("ssid", "AquaLight-Setup")
                .put("ip", "0.0.0.0")
                .put("stationCount", 0)
        )
        .put(
            "discovery",
            JSONObject()
                .put("ready", true)
                .put("port", 10_888)
                .put("broadcastIp", "192.168.1.255")
                .put("currentIp", "192.168.1.42")
                .put("payloadSize", 420)
                .put("lastRefreshMs", 120_000)
                .put("lastPacketRejectedMs", 0)
                .put("rejectedPacketCount", 0)
        )
        .put("runtime", runtimeJson())

    private fun setupApStatus(): JSONObject = JSONObject()
        .put("ip", "192.168.4.1")
        .put("macAddress", "AA:BB:CC:DD:EE:FF")
        .put("wifiModeCode", 2)
        .put("wifiMode", "setup_ap")
        .put("stationEnabled", false)
        .put("setupApEnabled", true)
        .put("clientConnected", false)
        .put("setupApActive", true)
        .put("uptimeMs", 2_000)
        .put(
            "client",
            JSONObject()
                .put("enabled", false)
                .put("configured", false)
                .put("ssid", "")
                .put("bssidConfigured", false)
                .put("channel", 0)
                .put("connected", false)
                .put("state", "setupApOnly")
                .put("wifiStatus", 6)
                .put("ip", "0.0.0.0")
                .put("gateway", "0.0.0.0")
                .put("subnet", "0.0.0.0")
                .put("dns", "0.0.0.0")
                .put("rssi", 0)
                .put("lastWifiEvent", -1)
                .put("lastDisconnectReason", 0)
                .put("lastDisconnectReasonName", "none")
                .put("lastDisconnectAgeMs", 0)
                .put("lastGotIpAgeMs", 0)
                .put("nextRetryRemainingMs", 0)
                .put("connectionInProgress", false)
        )
        .put(
            "setupAp",
            JSONObject()
                .put("enabled", true)
                .put("active", true)
                .put("ssid", "AquaLight-Setup")
                .put("ip", "192.168.4.1")
                .put("stationCount", 1)
        )
        .put(
            "discovery",
            JSONObject()
                .put("ready", true)
                .put("port", 10_888)
                .put("broadcastIp", "192.168.4.255")
                .put("currentIp", "192.168.4.1")
                .put("payloadSize", 410)
                .put("lastRefreshMs", 1_900)
                .put("lastPacketRejectedMs", 0)
                .put("rejectedPacketCount", 0)
        )
        .put("runtime", runtimeJson())

    private fun runtimeJson(): JSONObject = JSONObject()
        .put("transport", "websocket")
        .put("wsPort", 80)
        .put("wsPath", "/aql/v1/ws")
        .put("wsProtocol", "aql.ws.v1")
        .put("wsProtocolVersion", 1)

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-NETWORK-000001")
        val GENERATION = DeviceRuntimeConnectionGeneration(9L)
    }
}

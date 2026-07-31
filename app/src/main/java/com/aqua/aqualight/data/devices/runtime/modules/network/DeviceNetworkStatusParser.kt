package com.aqua.aqualight.data.devices.runtime.modules.network

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.parsing.requireExactKeys
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBoolean
import com.aqua.aqualight.data.devices.runtime.parsing.requiredInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeLong
import com.aqua.aqualight.data.devices.runtime.parsing.requiredObject
import com.aqua.aqualight.data.devices.runtime.parsing.requiredPort
import com.aqua.aqualight.data.devices.runtime.parsing.requiredString
import com.aqua.aqualight.data.devices.runtime.parsing.requiredStringAllowEmpty
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeDiscoveryStatus
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeNetworkClientStatus
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeNetworkStatus
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeSetupApStatus
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeTransportStatus
import org.json.JSONObject

object DeviceNetworkStatusParser {

    fun parse(data: JSONObject): Result<DeviceRuntimeNetworkStatus> = runCatching {
        data.requireExactKeys(NETWORK_STATUS_KEYS, "network.status.get.data")
        DeviceRuntimeNetworkStatus(
            ip = data.requiredStringAllowEmpty("ip"),
            macAddress = data.requiredString("macAddress"),
            wifiModeCode = data.requiredInt("wifiModeCode"),
            wifiMode = data.requiredString("wifiMode"),
            stationEnabled = data.requiredBoolean("stationEnabled"),
            setupApEnabled = data.requiredBoolean("setupApEnabled"),
            clientConnected = data.requiredBoolean("clientConnected"),
            setupApActive = data.requiredBoolean("setupApActive"),
            uptimeMs = data.requiredNonNegativeLong("uptimeMs"),
            client = parseClient(data.requiredObject("client")),
            setupAp = parseSetupAp(data.requiredObject("setupAp")),
            discovery = parseDiscovery(data.requiredObject("discovery")),
            runtime = parseRuntime(data.requiredObject("runtime"))
        )
    }

    private fun parseClient(client: JSONObject): DeviceRuntimeNetworkClientStatus {
        client.requireExactKeys(NETWORK_CLIENT_KEYS, "network.status.get.data.client")
        return DeviceRuntimeNetworkClientStatus(
            enabled = client.requiredBoolean("enabled"),
            configured = client.requiredBoolean("configured"),
            ssid = client.requiredStringAllowEmpty("ssid"),
            bssidConfigured = client.requiredBoolean("bssidConfigured"),
            channel = client.requiredNonNegativeInt("channel"),
            connected = client.requiredBoolean("connected"),
            state = client.requiredString("state"),
            wifiStatus = client.requiredInt("wifiStatus"),
            ip = client.requiredStringAllowEmpty("ip"),
            gateway = client.requiredStringAllowEmpty("gateway"),
            subnet = client.requiredStringAllowEmpty("subnet"),
            dns = client.requiredStringAllowEmpty("dns"),
            rssi = client.requiredInt("rssi"),
            lastWifiEvent = client.requiredInt("lastWifiEvent"),
            lastDisconnectReason = client.requiredInt("lastDisconnectReason"),
            lastDisconnectReasonName = client.requiredStringAllowEmpty(
                "lastDisconnectReasonName"
            ),
            lastDisconnectAgeMs = client.requiredNonNegativeLong("lastDisconnectAgeMs"),
            lastGotIpAgeMs = client.requiredNonNegativeLong("lastGotIpAgeMs"),
            nextRetryRemainingMs = client.requiredNonNegativeLong("nextRetryRemainingMs"),
            connectionInProgress = client.requiredBoolean("connectionInProgress")
        )
    }

    private fun parseSetupAp(setupAp: JSONObject): DeviceRuntimeSetupApStatus {
        setupAp.requireExactKeys(SETUP_AP_KEYS, "network.status.get.data.setupAp")
        return DeviceRuntimeSetupApStatus(
            enabled = setupAp.requiredBoolean("enabled"),
            active = setupAp.requiredBoolean("active"),
            ssid = setupAp.requiredStringAllowEmpty("ssid"),
            ip = setupAp.requiredStringAllowEmpty("ip"),
            stationCount = setupAp.requiredNonNegativeInt("stationCount")
        )
    }

    private fun parseDiscovery(discovery: JSONObject): DeviceRuntimeDiscoveryStatus {
        discovery.requireExactKeys(DISCOVERY_KEYS, "network.status.get.data.discovery")
        return DeviceRuntimeDiscoveryStatus(
            ready = discovery.requiredBoolean("ready"),
            port = discovery.requiredPort("port"),
            broadcastIp = discovery.requiredStringAllowEmpty("broadcastIp"),
            currentIp = discovery.requiredStringAllowEmpty("currentIp"),
            payloadSize = discovery.requiredNonNegativeInt("payloadSize"),
            lastRefreshMs = discovery.requiredNonNegativeLong("lastRefreshMs"),
            lastPacketRejectedMs = discovery.requiredNonNegativeLong("lastPacketRejectedMs"),
            rejectedPacketCount = discovery.requiredNonNegativeLong("rejectedPacketCount")
        )
    }

    private fun parseRuntime(runtime: JSONObject): DeviceRuntimeTransportStatus {
        runtime.requireExactKeys(NETWORK_RUNTIME_KEYS, "network.status.get.data.runtime")
        val protocol = runtime.requiredString("wsProtocol").also {
            require(it == AqlWsContract.SCHEMA)
        }
        return DeviceRuntimeTransportStatus(
            transport = runtime.requiredString("transport").also {
                require(it == WEBSOCKET_TRANSPORT)
            },
            wsSchema = protocol,
            wsPath = runtime.requiredString("wsPath").also {
                require(it == AqlWsContract.DEFAULT_PATH)
            },
            wsPort = runtime.requiredPort("wsPort"),
            wsProtocol = protocol,
            wsProtocolVersion = runtime.requiredInt("wsProtocolVersion").also {
                require(it == AqlWsContract.PROTOCOL_VERSION)
            }
        )
    }

    private val NETWORK_STATUS_KEYS = setOf(
        "ip", "macAddress", "wifiModeCode", "wifiMode", "stationEnabled", "setupApEnabled",
        "clientConnected", "setupApActive", "uptimeMs", "client", "setupAp", "discovery",
        "runtime"
    )
    private val NETWORK_CLIENT_KEYS = setOf(
        "enabled", "configured", "ssid", "bssidConfigured", "channel", "connected", "state",
        "wifiStatus", "ip", "gateway", "subnet", "dns", "rssi", "lastWifiEvent",
        "lastDisconnectReason", "lastDisconnectReasonName", "lastDisconnectAgeMs",
        "lastGotIpAgeMs", "nextRetryRemainingMs", "connectionInProgress"
    )
    private val SETUP_AP_KEYS = setOf("enabled", "active", "ssid", "ip", "stationCount")
    private val DISCOVERY_KEYS = setOf(
        "ready", "port", "broadcastIp", "currentIp", "payloadSize", "lastRefreshMs",
        "lastPacketRejectedMs", "rejectedPacketCount"
    )
    private val NETWORK_RUNTIME_KEYS = setOf(
        "transport", "wsPort", "wsPath", "wsProtocol", "wsProtocolVersion"
    )

    private const val WEBSOCKET_TRANSPORT = "websocket"
}

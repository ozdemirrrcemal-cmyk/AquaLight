package com.aqua.aqualight.data.devices.runtime.modules.network

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

internal object DeviceNetworkStatusParser {
    fun parse(data: JSONObject): DeviceNetworkStatus {
        DeviceRuntimeJson.requireExactKeys(data, ROOT_KEYS, ROOT_LABEL)
        val status = DeviceNetworkStatus(
            ip = DeviceRuntimeJson.stringValue(data, "ip"),
            macAddress = DeviceRuntimeJson.stringValue(data, "macAddress"),
            wifiModeCode = DeviceRuntimeJson.intValue(data, "wifiModeCode"),
            wifiMode = DeviceRuntimeJson.stringValue(data, "wifiMode"),
            stationEnabled = DeviceRuntimeJson.booleanValue(data, "stationEnabled"),
            setupApEnabled = DeviceRuntimeJson.booleanValue(data, "setupApEnabled"),
            clientConnected = DeviceRuntimeJson.booleanValue(data, "clientConnected"),
            setupApActive = DeviceRuntimeJson.booleanValue(data, "setupApActive"),
            uptimeMs = nonNegativeLong(data, "uptimeMs"),
            client = parseClient(DeviceRuntimeJson.objectValue(data, "client")),
            setupAp = parseSetupAp(DeviceRuntimeJson.objectValue(data, "setupAp")),
            discovery = parseDiscovery(DeviceRuntimeJson.objectValue(data, "discovery")),
            runtime = parseRuntime(DeviceRuntimeJson.objectValue(data, "runtime"))
        )
        validateMode(status)
        require(status.clientConnected == status.client.connected)
        require(status.setupApActive == status.setupAp.active)
        return status
    }

    private fun parseClient(data: JSONObject): DeviceNetworkClientStatus {
        DeviceRuntimeJson.requireExactKeys(data, CLIENT_KEYS, "$ROOT_LABEL.client")
        return DeviceNetworkClientStatus(
            enabled = DeviceRuntimeJson.booleanValue(data, "enabled"),
            configured = DeviceRuntimeJson.booleanValue(data, "configured"),
            ssid = DeviceRuntimeJson.stringAllowEmpty(data, "ssid"),
            bssidConfigured = DeviceRuntimeJson.booleanValue(data, "bssidConfigured"),
            channel = nonNegativeInt(data, "channel"),
            connected = DeviceRuntimeJson.booleanValue(data, "connected"),
            state = DeviceRuntimeJson.stringValue(data, "state"),
            wifiStatus = DeviceRuntimeJson.intValue(data, "wifiStatus"),
            ip = DeviceRuntimeJson.stringValue(data, "ip"),
            gateway = DeviceRuntimeJson.stringValue(data, "gateway"),
            subnet = DeviceRuntimeJson.stringValue(data, "subnet"),
            dns = DeviceRuntimeJson.stringValue(data, "dns"),
            rssi = DeviceRuntimeJson.intValue(data, "rssi"),
            lastWifiEvent = DeviceRuntimeJson.intValue(data, "lastWifiEvent"),
            lastDisconnectReason = DeviceRuntimeJson.intValue(data, "lastDisconnectReason"),
            lastDisconnectReasonName = DeviceRuntimeJson.stringValue(
                data,
                "lastDisconnectReasonName"
            ),
            lastDisconnectAgeMs = nonNegativeLong(data, "lastDisconnectAgeMs"),
            lastGotIpAgeMs = nonNegativeLong(data, "lastGotIpAgeMs"),
            nextRetryRemainingMs = nonNegativeLong(data, "nextRetryRemainingMs"),
            connectionInProgress = DeviceRuntimeJson.booleanValue(data, "connectionInProgress")
        )
    }

    private fun parseSetupAp(data: JSONObject): DeviceNetworkSetupApStatus {
        DeviceRuntimeJson.requireExactKeys(data, SETUP_AP_KEYS, "$ROOT_LABEL.setupAp")
        return DeviceNetworkSetupApStatus(
            enabled = DeviceRuntimeJson.booleanValue(data, "enabled"),
            active = DeviceRuntimeJson.booleanValue(data, "active"),
            ssid = DeviceRuntimeJson.stringAllowEmpty(data, "ssid"),
            ip = DeviceRuntimeJson.stringValue(data, "ip"),
            stationCount = nonNegativeInt(data, "stationCount")
        )
    }

    private fun parseDiscovery(data: JSONObject): DeviceNetworkDiscoveryStatus {
        DeviceRuntimeJson.requireExactKeys(data, DISCOVERY_KEYS, "$ROOT_LABEL.discovery")
        val port = DeviceRuntimeJson.intValue(data, "port")
        require(port in 0..65_535) { "network discovery port is invalid." }
        return DeviceNetworkDiscoveryStatus(
            ready = DeviceRuntimeJson.booleanValue(data, "ready"),
            port = port,
            broadcastIp = DeviceRuntimeJson.stringValue(data, "broadcastIp"),
            currentIp = DeviceRuntimeJson.stringValue(data, "currentIp"),
            payloadSize = nonNegativeInt(data, "payloadSize"),
            lastRefreshMs = nonNegativeLong(data, "lastRefreshMs"),
            lastPacketRejectedMs = nonNegativeLong(data, "lastPacketRejectedMs"),
            rejectedPacketCount = nonNegativeLong(data, "rejectedPacketCount")
        )
    }

    private fun parseRuntime(data: JSONObject): DeviceNetworkRuntimeStatus {
        DeviceRuntimeJson.requireExactKeys(data, RUNTIME_KEYS, "$ROOT_LABEL.runtime")
        return DeviceNetworkRuntimeStatus(
            transport = DeviceRuntimeJson.stringValue(data, "transport"),
            wsPort = DeviceRuntimeJson.intValue(data, "wsPort"),
            wsPath = DeviceRuntimeJson.stringValue(data, "wsPath"),
            wsProtocol = DeviceRuntimeJson.stringValue(data, "wsProtocol"),
            wsProtocolVersion = DeviceRuntimeJson.intValue(data, "wsProtocolVersion")
        ).also { runtime ->
            require(runtime.transport == "websocket")
            require(runtime.wsPort == 80)
            require(runtime.wsPath == AqlWsContract.DEFAULT_PATH)
            require(runtime.wsProtocol == AqlWsContract.SCHEMA)
            require(runtime.wsProtocolVersion == AqlWsContract.PROTOCOL_VERSION)
        }
    }

    private fun validateMode(status: DeviceNetworkStatus) {
        val expected = when (status.wifiModeCode) {
            0 -> Mode("off", station = false, setupAp = false)
            1 -> Mode("client", station = true, setupAp = false)
            2 -> Mode("setup_ap", station = false, setupAp = true)
            3 -> Mode("client_and_setup_ap", station = true, setupAp = true)
            else -> error("Unknown firmware Wi-Fi mode code: ${status.wifiModeCode}")
        }
        require(status.wifiMode == expected.name)
        require(status.stationEnabled == expected.station)
        require(status.setupApEnabled == expected.setupAp)
    }

    private fun nonNegativeInt(data: JSONObject, key: String): Int =
        DeviceRuntimeJson.intValue(data, key).also { require(it >= 0) { "$key must not be negative." } }

    private fun nonNegativeLong(data: JSONObject, key: String): Long =
        DeviceRuntimeJson.longValue(data, key).also { require(it >= 0L) { "$key must not be negative." } }

    private data class Mode(
        val name: String,
        val station: Boolean,
        val setupAp: Boolean
    )

    private const val ROOT_LABEL = "network.status.get.data"
    private val ROOT_KEYS = setOf(
        "ip", "macAddress", "wifiModeCode", "wifiMode", "stationEnabled",
        "setupApEnabled", "clientConnected", "setupApActive", "uptimeMs",
        "client", "setupAp", "discovery", "runtime"
    )
    private val CLIENT_KEYS = setOf(
        "enabled", "configured", "ssid", "bssidConfigured", "channel", "connected",
        "state", "wifiStatus", "ip", "gateway", "subnet", "dns", "rssi",
        "lastWifiEvent", "lastDisconnectReason", "lastDisconnectReasonName",
        "lastDisconnectAgeMs", "lastGotIpAgeMs", "nextRetryRemainingMs",
        "connectionInProgress"
    )
    private val SETUP_AP_KEYS = setOf("enabled", "active", "ssid", "ip", "stationCount")
    private val DISCOVERY_KEYS = setOf(
        "ready", "port", "broadcastIp", "currentIp", "payloadSize", "lastRefreshMs",
        "lastPacketRejectedMs", "rejectedPacketCount"
    )
    private val RUNTIME_KEYS = setOf(
        "transport", "wsPort", "wsPath", "wsProtocol", "wsProtocolVersion"
    )
}

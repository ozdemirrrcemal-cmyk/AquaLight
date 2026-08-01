package com.aqua.aqualight.data.devices.runtime.modules.network

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

internal object DeviceNetworkStatusParser {
    fun parse(data: JSONObject): DeviceNetworkStatus {
        DeviceRuntimeJson.requireExactKeys(data, ROOT_KEYS, ROOT_LABEL)
        val channel = DeviceRuntimeJson.intValue(data, "channel")
        require(channel >= 0) { "network.status.get channel must not be negative." }
        return DeviceNetworkStatus(
            connected = DeviceRuntimeJson.booleanValue(data, "connected"),
            mode = DeviceRuntimeJson.intValue(data, "mode"),
            rssi = DeviceRuntimeJson.intValue(data, "rssi"),
            channel = channel,
            staHostname = DeviceRuntimeJson.stringAllowEmpty(data, "staHostname"),
            client = parseClient(DeviceRuntimeJson.objectValue(data, "client")),
            setupAp = parseSetupAp(DeviceRuntimeJson.objectValue(data, "setupAp")),
            discovery = parseDiscovery(DeviceRuntimeJson.objectValue(data, "discovery")),
            runtime = parseRuntime(DeviceRuntimeJson.objectValue(data, "runtime"))
        )
    }

    private fun parseClient(data: JSONObject): DeviceNetworkClientStatus {
        DeviceRuntimeJson.requireExactKeys(data, CLIENT_KEYS, "$ROOT_LABEL.client")
        return DeviceNetworkClientStatus(
            connected = DeviceRuntimeJson.booleanValue(data, "connected"),
            configured = DeviceRuntimeJson.booleanValue(data, "configured"),
            ssid = DeviceRuntimeJson.stringAllowEmpty(data, "ssid"),
            ip = DeviceRuntimeJson.stringAllowEmpty(data, "ip"),
            gateway = DeviceRuntimeJson.stringAllowEmpty(data, "gateway"),
            subnet = DeviceRuntimeJson.stringAllowEmpty(data, "subnet"),
            dns = DeviceRuntimeJson.stringAllowEmpty(data, "dns"),
            mac = DeviceRuntimeJson.stringAllowEmpty(data, "mac"),
            hostname = DeviceRuntimeJson.stringAllowEmpty(data, "hostname"),
            reconnectPolicy = DeviceRuntimeJson.stringValue(data, "reconnectPolicy"),
            disconnectEraseAffects = DeviceRuntimeJson.stringValue(
                data,
                "disconnectEraseAffects"
            ),
            setupApFallback = DeviceRuntimeJson.booleanValue(data, "setupApFallback")
        )
    }

    private fun parseSetupAp(data: JSONObject): DeviceNetworkSetupApStatus {
        DeviceRuntimeJson.requireExactKeys(data, SETUP_AP_KEYS, "$ROOT_LABEL.setupAp")
        return DeviceNetworkSetupApStatus(
            active = DeviceRuntimeJson.booleanValue(data, "active"),
            ssid = DeviceRuntimeJson.stringAllowEmpty(data, "ssid"),
            ip = DeviceRuntimeJson.stringAllowEmpty(data, "ip"),
            gateway = DeviceRuntimeJson.stringAllowEmpty(data, "gateway"),
            subnet = DeviceRuntimeJson.stringAllowEmpty(data, "subnet"),
            mac = DeviceRuntimeJson.stringAllowEmpty(data, "mac"),
            passwordProtected = DeviceRuntimeJson.booleanValue(data, "passwordProtected"),
            passwordSource = DeviceRuntimeJson.stringValue(data, "passwordSource"),
            provisioningContract = DeviceRuntimeJson.stringValue(data, "provisioningContract"),
            provisioningTransport = DeviceRuntimeJson.stringValue(data, "provisioningTransport")
        )
    }

    private fun parseDiscovery(data: JSONObject): DeviceNetworkDiscoveryStatus {
        DeviceRuntimeJson.requireExactKeys(data, DISCOVERY_KEYS, "$ROOT_LABEL.discovery")
        val port = DeviceRuntimeJson.intValue(data, "udpPort")
        require(port in 1..65_535) { "network discovery UDP port is invalid." }
        val status = DeviceNetworkDiscoveryStatus(
            udpBroadcast = DeviceRuntimeJson.booleanValue(data, "udpBroadcast"),
            udpPort = port,
            registeredOnly = DeviceRuntimeJson.booleanValue(data, "registeredOnly"),
            authenticatedWebSocketRequired = DeviceRuntimeJson.booleanValue(
                data,
                "authenticatedWebSocketRequired"
            )
        )
        require(status.udpBroadcast && status.registeredOnly && status.authenticatedWebSocketRequired) {
            "network discovery security contract is incompatible."
        }
        return status
    }

    private fun parseRuntime(data: JSONObject): DeviceNetworkRuntimeStatus {
        DeviceRuntimeJson.requireExactKeys(data, RUNTIME_KEYS, "$ROOT_LABEL.runtime")
        return DeviceNetworkRuntimeStatus(
            transport = DeviceRuntimeJson.stringValue(data, "transport"),
            wsPath = DeviceRuntimeJson.stringValue(data, "wsPath"),
            wsPort = DeviceRuntimeJson.intValue(data, "wsPort"),
            wsProtocol = DeviceRuntimeJson.stringValue(data, "wsProtocol"),
            wsProtocolVersion = DeviceRuntimeJson.intValue(data, "wsProtocolVersion")
        ).also { runtime ->
            require(runtime.transport == "websocket")
            require(runtime.wsPath == AqlWsContract.DEFAULT_PATH)
            require(runtime.wsPort == 80)
            require(runtime.wsProtocol == AqlWsContract.SCHEMA)
            require(runtime.wsProtocolVersion == AqlWsContract.PROTOCOL_VERSION)
        }
    }

    private const val ROOT_LABEL = "network.status.get.data"
    private val ROOT_KEYS = setOf(
        "connected", "mode", "rssi", "channel", "staHostname",
        "client", "setupAp", "discovery", "runtime"
    )
    private val CLIENT_KEYS = setOf(
        "connected", "configured", "ssid", "ip", "gateway", "subnet", "dns", "mac",
        "hostname", "reconnectPolicy", "disconnectEraseAffects", "setupApFallback"
    )
    private val SETUP_AP_KEYS = setOf(
        "active", "ssid", "ip", "gateway", "subnet", "mac", "passwordProtected",
        "passwordSource", "provisioningContract", "provisioningTransport"
    )
    private val DISCOVERY_KEYS = setOf(
        "udpBroadcast", "udpPort", "registeredOnly", "authenticatedWebSocketRequired"
    )
    private val RUNTIME_KEYS = setOf(
        "transport", "wsPath", "wsPort", "wsProtocol", "wsProtocolVersion"
    )
}

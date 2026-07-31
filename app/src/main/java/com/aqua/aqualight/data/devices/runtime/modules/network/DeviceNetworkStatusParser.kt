package com.aqua.aqualight.data.devices.runtime.modules.network

import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal object DeviceNetworkStatusParser {

    fun parse(data: JSONObject): DeviceNetworkStatus {
        data.requireExactKeys(ROOT_KEYS, "network.status.get.data")
        val clientJson = data.requiredObject("client")
        val setupApJson = data.requiredObject("setupAp")
        val discoveryJson = data.requiredObject("discovery")
        val runtimeJson = data.requiredObject("runtime")
        clientJson.requireExactKeys(CLIENT_KEYS, "network.status.get.data.client")
        setupApJson.requireExactKeys(SETUP_AP_KEYS, "network.status.get.data.setupAp")
        discoveryJson.requireExactKeys(DISCOVERY_KEYS, "network.status.get.data.discovery")
        runtimeJson.requireExactKeys(RUNTIME_KEYS, "network.status.get.data.runtime")

        val wifiModeCode = data.requiredInt("wifiModeCode")
        val wifiMode = requireNotNull(
            DeviceNetworkWifiMode.fromWireExact(data.requiredNonBlankString("wifiMode"))
        ) { "Unknown firmware wifiMode." }
        validateWifiMode(wifiMode, wifiModeCode)

        val client = parseClient(clientJson)
        val setupAp = parseSetupAp(setupApJson)
        val discovery = parseDiscovery(discoveryJson)
        val runtime = parseRuntime(runtimeJson)
        val stationEnabled = data.requiredBoolean("stationEnabled")
        val setupApEnabled = data.requiredBoolean("setupApEnabled")
        val clientConnected = data.requiredBoolean("clientConnected")
        val setupApActive = data.requiredBoolean("setupApActive")
        validateModeFlags(wifiMode, stationEnabled, setupApEnabled)
        require(clientConnected == client.connected)
        require(setupApActive == setupAp.active)
        require(!clientConnected || stationEnabled)

        val ip = data.requiredIpv4("ip")
        if (discovery.ready) {
            require(ip == discovery.currentIp) {
                "Ready discovery currentIp differs from the active network IP."
            }
        } else {
            require(discovery.currentIp == UNSPECIFIED_IPV4)
        }
        if (clientConnected) {
            require(ip == client.ip)
        } else {
            require(ip == setupAp.ip)
        }

        return DeviceNetworkStatus(
            ip = ip,
            macAddress = data.requiredMacAddress("macAddress"),
            wifiModeCode = wifiModeCode,
            wifiMode = wifiMode,
            stationEnabled = stationEnabled,
            setupApEnabled = setupApEnabled,
            clientConnected = clientConnected,
            setupApActive = setupApActive,
            uptimeMs = data.requiredUnsigned32("uptimeMs"),
            client = client,
            setupAp = setupAp,
            discovery = discovery,
            runtime = runtime
        )
    }

    private fun parseClient(json: JSONObject): DeviceNetworkClientStatus {
        val connected = json.requiredBoolean("connected")
        val rssi = json.requiredInt("rssi")
        if (connected) {
            require(rssi in MIN_RSSI..0)
        } else {
            require(rssi == 0)
        }
        return DeviceNetworkClientStatus(
            enabled = json.requiredBoolean("enabled"),
            configured = json.requiredBoolean("configured"),
            ssid = json.requiredSsid("ssid"),
            bssidConfigured = json.requiredBoolean("bssidConfigured"),
            channel = json.requiredInt("channel").also { require(it in 0..MAX_WIFI_CHANNEL) },
            connected = connected,
            state = requireNotNull(
                DeviceNetworkClientState.fromWireExact(json.requiredNonBlankString("state"))
            ) { "Unknown firmware client state." },
            wifiStatus = json.requiredInt("wifiStatus"),
            ip = json.requiredIpv4("ip"),
            gateway = json.requiredIpv4("gateway"),
            subnet = json.requiredIpv4("subnet"),
            dns = json.requiredIpv4("dns"),
            rssi = rssi,
            lastWifiEvent = json.requiredInt("lastWifiEvent"),
            lastDisconnectReason = json.requiredInt("lastDisconnectReason")
                .also { require(it >= 0) },
            lastDisconnectReasonName = requireNotNull(
                DeviceNetworkDisconnectReason.fromWireExact(
                    json.requiredNonBlankString("lastDisconnectReasonName")
                )
            ) { "Unknown firmware disconnect reason name." },
            lastDisconnectAgeMs = json.requiredUnsigned32("lastDisconnectAgeMs"),
            lastGotIpAgeMs = json.requiredUnsigned32("lastGotIpAgeMs"),
            nextRetryRemainingMs = json.requiredUnsigned32("nextRetryRemainingMs"),
            connectionInProgress = json.requiredBoolean("connectionInProgress")
        )
    }

    private fun parseSetupAp(json: JSONObject): DeviceNetworkSetupApStatus =
        DeviceNetworkSetupApStatus(
            enabled = json.requiredBoolean("enabled"),
            active = json.requiredBoolean("active"),
            ssid = json.requiredNonBlankString("ssid"),
            ip = json.requiredIpv4("ip"),
            stationCount = json.requiredInt("stationCount").also { require(it >= 0) }
        )

    private fun parseDiscovery(json: JSONObject): DeviceNetworkDiscoveryStatus =
        DeviceNetworkDiscoveryStatus(
            ready = json.requiredBoolean("ready"),
            port = json.requiredInt("port").also {
                require(it == AqlDiscoveryContract.PORT)
            },
            broadcastIp = json.requiredIpv4("broadcastIp"),
            currentIp = json.requiredIpv4("currentIp"),
            payloadSize = json.requiredInt("payloadSize").also {
                require(it in 0..AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES)
            },
            lastRefreshMs = json.requiredUnsigned32("lastRefreshMs"),
            lastPacketRejectedMs = json.requiredUnsigned32("lastPacketRejectedMs"),
            rejectedPacketCount = json.requiredUnsigned32("rejectedPacketCount")
        )

    private fun parseRuntime(json: JSONObject): DeviceNetworkRuntimeTransport =
        DeviceNetworkRuntimeTransport(
            transport = json.requiredNonBlankString("transport").also {
                require(it == "websocket")
            },
            wsPort = json.requiredInt("wsPort").also { require(it == 80) },
            wsPath = json.requiredNonBlankString("wsPath").also {
                require(it == AqlWsContract.DEFAULT_PATH)
            },
            wsProtocol = json.requiredNonBlankString("wsProtocol").also {
                require(it == AqlWsContract.SCHEMA)
            },
            wsProtocolVersion = json.requiredInt("wsProtocolVersion").also {
                require(it == AqlWsContract.PROTOCOL_VERSION)
            }
        )

    private fun validateWifiMode(mode: DeviceNetworkWifiMode, code: Int) {
        val expectedCode = mode.code
        if (expectedCode == null) {
            require(code !in 0..3)
        } else {
            require(code == expectedCode)
        }
    }

    private fun validateModeFlags(
        mode: DeviceNetworkWifiMode,
        stationEnabled: Boolean,
        setupApEnabled: Boolean
    ) {
        val expected = when (mode) {
            DeviceNetworkWifiMode.OFF -> false to false
            DeviceNetworkWifiMode.CLIENT -> true to false
            DeviceNetworkWifiMode.SETUP_AP -> false to true
            DeviceNetworkWifiMode.CLIENT_AND_SETUP_AP -> true to true
            DeviceNetworkWifiMode.UNKNOWN -> false to false
        }
        require(stationEnabled == expected.first)
        require(setupApEnabled == expected.second)
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) {
            "$label keys differ from firmware; expected=$expected actual=$actual"
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject =
        get(key) as? JSONObject ?: error("$key must be a JSON object.")

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: error("$key must be a boolean.")

    private fun JSONObject.requiredInt(key: String): Int {
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return asLong.toInt()
    }

    private fun JSONObject.requiredUnsigned32(key: String): Long {
        val number = get(key) as? Number ?: error("$key must be an unsigned integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
        require(asLong in 0L..UINT32_MAX)
        return asLong
    }

    private fun JSONObject.requiredNonBlankString(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.isNotEmpty())
        require(value == value.trim())
        require(value.none(Char::isISOControl))
        return value
    }

    private fun JSONObject.requiredSsid(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_SSID_BYTES)
        return value
    }

    private fun JSONObject.requiredIpv4(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        val parts = value.split('.')
        require(parts.size == 4)
        val octets = parts.map { part ->
            require(part.isNotEmpty() && part.all(Char::isDigit))
            part.toInt().also { require(it in 0..255) }
        }
        require(octets.joinToString(".") == value)
        return value
    }

    private fun JSONObject.requiredMacAddress(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(MAC_ADDRESS_REGEX.matches(value))
        return value
    }

    private const val UINT32_MAX = 4_294_967_295L
    private const val MAX_SSID_BYTES = 32
    private const val MAX_WIFI_CHANNEL = 14
    private const val MIN_RSSI = -127
    private const val UNSPECIFIED_IPV4 = "0.0.0.0"
    private val MAC_ADDRESS_REGEX = Regex("^[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}$")

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

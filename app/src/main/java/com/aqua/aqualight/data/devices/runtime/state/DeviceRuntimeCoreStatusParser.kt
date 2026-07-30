package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import org.json.JSONObject

object DeviceRuntimeCoreStatusParser {

    fun parseDeviceStatus(data: JSONObject): Result<DeviceRuntimeDeviceStatus> = runCatching {
        data.requireExactKeys(DEVICE_STATUS_KEYS, "device.status.get.data")
        val device = data.requiredObject("device")
        val product = data.requiredObject("product")
        val runtime = data.requiredObject("runtime")
        val modules = data.requiredObject("modules")

        device.requireExactKeys(NAME_STATUS_KEYS, "device.status.get.data.device")
        product.requireExactKeys(PRODUCT_STATUS_KEYS, "device.status.get.data.product")
        runtime.requireExactKeys(DEVICE_RUNTIME_KEYS, "device.status.get.data.runtime")
        modules.requireExactKeys(MODULE_STATUS_KEYS, "device.status.get.data.modules")

        DeviceRuntimeDeviceStatus(
            state = data.requiredString("state").also { require(it == "booted") },
            authenticated = data.requiredBoolean("authenticated").also { require(it) },
            uptimeMs = data.requiredNonNegativeLong("uptimeMs"),
            device = parseNameStatusExact(device),
            product = DeviceRuntimeProductStatus(
                productKey = product.requiredString("productKey"),
                family = product.requiredString("family"),
                model = product.requiredString("model"),
                displayName = product.requiredString("displayName")
            ),
            runtime = DeviceRuntimeTransportStatus(
                transport = runtime.requiredString("transport").also {
                    require(it == "websocket")
                },
                wsSchema = runtime.requiredString("wsSchema").also {
                    require(it == AqlWsContract.SCHEMA)
                },
                wsPath = runtime.requiredString("wsPath").also {
                    require(it == AqlWsContract.DEFAULT_PATH)
                },
                wsPort = runtime.requiredPort("wsPort")
            ),
            modules = DeviceRuntimeCompiledModules(
                light = modules.requiredBoolean("light"),
                cooling = modules.requiredBoolean("cooling"),
                temperature = modules.requiredBoolean("temperature"),
                timerApi = modules.requiredBoolean("timerApi"),
                timerEngine = modules.requiredBoolean("timerEngine"),
                dosing = modules.requiredBoolean("dosing"),
                network = modules.requiredBoolean("network"),
                discovery = modules.requiredBoolean("discovery"),
                firmware = modules.requiredBoolean("firmware"),
                system = modules.requiredBoolean("system")
            )
        )
    }

    fun parseNameStatus(data: JSONObject): Result<DeviceRuntimeNameStatus> =
        runCatching {
            data.requireExactKeys(NAME_STATUS_KEYS, "device name status")
            parseNameStatusExact(data)
        }

    fun parseSecurityStatus(data: JSONObject): Result<DeviceRuntimeSecurityStatus> = runCatching {
        data.requireAllowedAndRequiredKeys(
            allowed = SECURITY_STATUS_KEYS + SECURITY_DYNAMIC_KEYS,
            required = SECURITY_STATUS_KEYS,
            label = "security.status.get.data"
        )

        DeviceRuntimeSecurityStatus(
            tokenGateEnabled = data.requiredBoolean("tokenGateEnabled"),
            dynamicPairingEnabled = data.requiredBoolean("dynamicPairingEnabled"),
            paired = data.requiredBoolean("paired"),
            runtimeTransport = data.requiredString("runtimeTransport").also {
                require(it == "websocket")
            },
            runtimeAuthMessageType = data.requiredString("runtimeAuthMessageType").also {
                require(it == AqlWsContract.TYPE_AUTH)
            },
            runtimeAuthScheme = data.requiredString("runtimeAuthScheme").also {
                require(it == AqlWsContract.AUTH_SCHEME)
            },
            runtimeCredentialSerialized = data.requiredBoolean("runtimeCredentialSerialized")
                .also { require(!it) },
            runtimeReplayProtection = data.requiredString("runtimeReplayProtection"),
            initialOwnershipTransport = data.requiredString("initialOwnershipTransport"),
            firstTokenTransport = data.requiredString("firstTokenTransport"),
            webSocketPairingCommand = data.requiredString("webSocketPairingCommand"),
            webSocketPairingCommandAuth = data.requiredString("webSocketPairingCommandAuth"),
            webSocketPairingPurpose = data.requiredString("webSocketPairingPurpose"),
            publicFirstPairingSupported = data.requiredBoolean("publicFirstPairingSupported")
                .also { require(!it) },
            mutatingCommandsRequireAuth = data.requiredBoolean("mutatingCommandsRequireAuth"),
            tokenReturnedByStatus = data.requiredBoolean("tokenReturnedByStatus")
                .also { require(!it) },
            tokenStorageBackend = data.requiredString("tokenStorageBackend"),
            tokenStorageFormat = data.requiredString("tokenStorageFormat"),
            tokenStoredPlaintext = data.requiredBoolean("tokenStoredPlaintext")
                .also { require(!it) },
            tokenFormat = data.requiredString("tokenFormat"),
            tokenHexLength = data.requiredInt("tokenHexLength").also { require(it == 64) },
            deviceUid = data.requiredString("deviceUid"),
            shortId = data.requiredString("shortId"),
            serialNumber = data.requiredString("serialNumber"),
            tokenVersion = data.optionalNonNegativeLong("tokenVersion"),
            pairedAtMs = data.optionalNonNegativeLong("pairedAtMs"),
            lastRotatedAtMs = data.optionalNonNegativeLong("lastRotatedAtMs"),
            provisioningTokenPending = data.optionalBoolean("provisioningTokenPending")
        )
    }

    fun parseNetworkStatus(data: JSONObject): Result<DeviceRuntimeNetworkStatus> = runCatching {
        data.requireExactKeys(NETWORK_STATUS_KEYS, "network.status.get.data")
        val client = data.requiredObject("client")
        val setupAp = data.requiredObject("setupAp")
        val discovery = data.requiredObject("discovery")
        val runtime = data.requiredObject("runtime")

        client.requireExactKeys(NETWORK_CLIENT_KEYS, "network.status.get.data.client")
        setupAp.requireExactKeys(SETUP_AP_KEYS, "network.status.get.data.setupAp")
        discovery.requireExactKeys(DISCOVERY_KEYS, "network.status.get.data.discovery")
        runtime.requireExactKeys(NETWORK_RUNTIME_KEYS, "network.status.get.data.runtime")

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
            client = DeviceRuntimeNetworkClientStatus(
                enabled = client.requiredBoolean("enabled"),
                configured = client.requiredBoolean("configured"),
                ssid = client.requiredStringAllowEmpty("ssid"),
                bssidConfigured = client.requiredBoolean("bssidConfigured"),
                channel = client.requiredInt("channel").also { require(it >= 0) },
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
            ),
            setupAp = DeviceRuntimeSetupApStatus(
                enabled = setupAp.requiredBoolean("enabled"),
                active = setupAp.requiredBoolean("active"),
                ssid = setupAp.requiredStringAllowEmpty("ssid"),
                ip = setupAp.requiredStringAllowEmpty("ip"),
                stationCount = setupAp.requiredInt("stationCount").also { require(it >= 0) }
            ),
            discovery = DeviceRuntimeDiscoveryStatus(
                ready = discovery.requiredBoolean("ready"),
                port = discovery.requiredPort("port"),
                broadcastIp = discovery.requiredStringAllowEmpty("broadcastIp"),
                currentIp = discovery.requiredStringAllowEmpty("currentIp"),
                payloadSize = discovery.requiredInt("payloadSize").also { require(it >= 0) },
                lastRefreshMs = discovery.requiredNonNegativeLong("lastRefreshMs"),
                lastPacketRejectedMs = discovery.requiredNonNegativeLong("lastPacketRejectedMs"),
                rejectedPacketCount = discovery.requiredNonNegativeLong("rejectedPacketCount")
            ),
            runtime = DeviceRuntimeTransportStatus(
                transport = runtime.requiredString("transport").also {
                    require(it == "websocket")
                },
                wsSchema = runtime.requiredString("wsProtocol").also {
                    require(it == AqlWsContract.SCHEMA)
                },
                wsPath = runtime.requiredString("wsPath").also {
                    require(it == AqlWsContract.DEFAULT_PATH)
                },
                wsPort = runtime.requiredPort("wsPort"),
                wsProtocol = runtime.requiredString("wsProtocol"),
                wsProtocolVersion = runtime.requiredInt("wsProtocolVersion").also {
                    require(it == AqlWsContract.PROTOCOL_VERSION)
                }
            )
        )
    }

    private fun parseNameStatusExact(data: JSONObject): DeviceRuntimeNameStatus =
        DeviceRuntimeNameStatus(
            productDisplayName = data.requiredString("productDisplayName"),
            customName = data.requiredStringAllowEmpty("customName"),
            effectiveDisplayName = data.requiredString("effectiveDisplayName"),
            editable = data.requiredBoolean("editable"),
            maxBytes = data.requiredInt("maxBytes").also { require(it in 1..256) }
        )

    private val DEVICE_STATUS_KEYS = setOf(
        "state", "authenticated", "uptimeMs", "device", "product", "runtime", "modules"
    )
    private val NAME_STATUS_KEYS = setOf(
        "productDisplayName", "customName", "effectiveDisplayName", "editable", "maxBytes"
    )
    private val PRODUCT_STATUS_KEYS = setOf("productKey", "family", "model", "displayName")
    private val DEVICE_RUNTIME_KEYS = setOf("transport", "wsSchema", "wsPath", "wsPort")
    private val MODULE_STATUS_KEYS = setOf(
        "light", "cooling", "temperature", "timerApi", "timerEngine", "dosing",
        "network", "discovery", "firmware", "system"
    )
    private val SECURITY_STATUS_KEYS = setOf(
        "tokenGateEnabled", "dynamicPairingEnabled", "paired", "runtimeTransport",
        "runtimeAuthMessageType", "runtimeAuthScheme", "runtimeCredentialSerialized",
        "runtimeReplayProtection", "initialOwnershipTransport", "firstTokenTransport",
        "webSocketPairingCommand", "webSocketPairingCommandAuth", "webSocketPairingPurpose",
        "publicFirstPairingSupported", "mutatingCommandsRequireAuth", "tokenReturnedByStatus",
        "tokenStorageBackend", "tokenStorageFormat", "tokenStoredPlaintext", "tokenFormat",
        "tokenHexLength", "deviceUid", "shortId", "serialNumber"
    )
    private val SECURITY_DYNAMIC_KEYS = setOf(
        "tokenVersion", "pairedAtMs", "lastRotatedAtMs", "provisioningTokenPending"
    )
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
}

private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = keySet()
    require(actual == expected) { "$label keys differ from the firmware contract: $actual" }
}

private fun JSONObject.requireAllowedAndRequiredKeys(
    allowed: Set<String>,
    required: Set<String>,
    label: String
) {
    val actual = keySet()
    require(actual.all(allowed::contains)) { "$label contains unknown keys: ${actual - allowed}" }
    require(actual.containsAll(required)) { "$label is missing keys: ${required - actual}" }
}

private fun JSONObject.keySet(): Set<String> = buildSet {
    val iterator = keys()
    while (iterator.hasNext()) add(iterator.next())
}

private fun JSONObject.requiredObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

private fun JSONObject.requiredString(key: String): String =
    requiredStringAllowEmpty(key).also { require(it.isNotEmpty()) { "$key must not be empty." } }

private fun JSONObject.requiredStringAllowEmpty(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.none(Char::isISOControl)) { "$key contains control characters." }
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$key must not contain leading/trailing whitespace."
    }
    return value
}

private fun JSONObject.requiredBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

private fun JSONObject.optionalBoolean(key: String): Boolean? =
    if (!has(key)) null else requiredBoolean(key)

private fun JSONObject.requiredInt(key: String): Int {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    val doubleValue = number.toDouble()
    val longValue = number.toLong()
    require(doubleValue.isFinite() && doubleValue == longValue.toDouble()) {
        "$key must be an exact integer."
    }
    require(longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        "$key is outside Int range."
    }
    return longValue.toInt()
}

private fun JSONObject.requiredNonNegativeLong(key: String): Long {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    val doubleValue = number.toDouble()
    val longValue = number.toLong()
    require(doubleValue.isFinite() && doubleValue == longValue.toDouble() && longValue >= 0L) {
        "$key must be a non-negative exact integer."
    }
    return longValue
}

private fun JSONObject.optionalNonNegativeLong(key: String): Long? =
    if (!has(key)) null else requiredNonNegativeLong(key)

private fun JSONObject.requiredPort(key: String): Int =
    requiredInt(key).also { require(it in 1..65_535) { "$key must be a valid port." } }

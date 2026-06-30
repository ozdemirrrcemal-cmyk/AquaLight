package com.aqua.aqualight.data.devices.discovery.udp

import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Strict parser for firmware UDP discovery v2 packets.
 *
 * The parser intentionally rejects legacy discovery packets. Android must not silently accept
 * AP/HTTP/UDP-v1 era payloads because that would bring the old device data path back.
 *
 * Firmware discovery is a LAN handoff only: it exposes identity, product family and the
 * WebSocket endpoint. Capabilities, limits and detailed UI metadata are optional here and must
 * be refreshed from authenticated WebSocket runtime commands when available.
 */
object AqlDiscoveryParser {

    fun parseDeviceAnnounce(
        rawPayload: String,
        sourceIp: String = "",
        receivedAtMillis: Long = System.currentTimeMillis()
    ): ParseResult {
        val payload = rawPayload.trim()
        if (payload.isBlank()) return ParseResult.Invalid(ParseError.EMPTY_PAYLOAD)
        if (payload.length > AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES) {
            return ParseResult.Invalid(ParseError.PACKET_TOO_LARGE)
        }

        val root = try {
            JSONObject(payload)
        } catch (_: JSONException) {
            return ParseResult.Invalid(ParseError.INVALID_JSON)
        }

        val schema = root.stringOrBlank("schema")
        if (schema != AqlDiscoveryContract.SCHEMA) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_SCHEMA)
        }

        val messageType = root.stringOrBlank("messageType")
        if (messageType != AqlDiscoveryContract.MESSAGE_DEVICE_ANNOUNCE) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_MESSAGE_TYPE)
        }

        val udpVersion = root.intOrZero("udpVersion")
        if (udpVersion < AqlDiscoveryContract.UDP_VERSION) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_UDP_VERSION)
        }

        val device = root.optJSONObject("device")
            ?: return ParseResult.Invalid(ParseError.MISSING_DEVICE)
        val product = root.optJSONObject("product")
            ?: return ParseResult.Invalid(ParseError.MISSING_PRODUCT)
        val network = root.optJSONObject("network")
            ?: return ParseResult.Invalid(ParseError.MISSING_NETWORK)
        val runtime = root.optJSONObject("runtime")

        val deviceUid = device.stringOrBlank("uid")
        if (deviceUid.isBlank()) {
            return ParseResult.Invalid(ParseError.MISSING_DEVICE_UID)
        }

        val familyRaw = product.stringOrBlank("family")
        val family = DeviceFamily.fromWire(familyRaw)
        if (family == DeviceFamily.UNKNOWN) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_PRODUCT_FAMILY)
        }

        val runtimeTransport = runtime.stringOrBlankOrFallback(
            primary = "transport",
            fallback = network.stringOrBlank("runtimeTransport")
        )
        if (runtimeTransport != AqlDiscoveryContract.RUNTIME_TRANSPORT_WEBSOCKET) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_RUNTIME_TRANSPORT)
        }

        val wsProtocol = runtime.stringOrBlankOrFallback(
            primary = "wsProtocol",
            fallback = network.stringOrBlank("wsProtocol")
        )
        if (wsProtocol != AqlWsContract.DEFAULT_PROTOCOL) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_WS_PROTOCOL)
        }

        val wsProtocolVersion = runtime.intOrZeroOrFallback(
            primary = "wsProtocolVersion",
            fallback = network.intOrZero("wsProtocolVersion")
        )
        if (wsProtocolVersion < AqlWsContract.PROTOCOL_VERSION) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_WS_PROTOCOL_VERSION)
        }

        val endpointIp = network.stringOrBlank("ip").ifBlank { sourceIp }
        if (endpointIp.isBlank()) {
            return ParseResult.Invalid(ParseError.MISSING_RUNTIME_ENDPOINT)
        }

        val wsPort = runtime.intOrZeroOrFallback(
            primary = "wsPort",
            fallback = network.intOrZero("wsPort")
        )
        val wsPath = runtime.stringOrBlankOrFallback(
            primary = "wsPath",
            fallback = network.stringOrBlank("wsPath")
        )
        if (wsPort <= 0 || wsPath.isBlank()) {
            return ParseResult.Invalid(ParseError.MISSING_RUNTIME_ENDPOINT)
        }

        val firmware = root.optJSONObject("firmware")
        val capabilities = root.optJSONObject("capabilities")
        val limits = root.optJSONObject("limits")

        val snapshot = DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(deviceUid),
                shortId = device.stringOrBlank("shortId"),
                chipId = device.stringOrBlank("chipId"),
                espChipId = device.stringOrBlank("espChipId"),
                efuseMac = device.stringOrBlank("efuseMac"),
                macAddress = device.stringOrBlank("macAddress"),
                serialNumber = device.stringOrBlank("serialNumber"),
                firmwareSerial = device.stringOrBlank("firmwareSerial"),
                displayName = device.stringOrBlank("displayName"),
                customName = device.stringOrBlank("customName"),
                setupCode = device.stringOrBlank("setupCode"),
                setupSsid = device.stringOrBlank("setupSsid")
            ),
            product = DeviceProduct(
                brand = product.stringOrBlank("brand"),
                productId = product.stringOrBlank("productId"),
                productKey = product.stringOrBlank("productKey"),
                family = family,
                familyRaw = familyRaw,
                line = product.stringOrBlank("line"),
                model = product.stringOrBlank("model"),
                displayName = product.stringOrBlank("displayName"),
                skuId = product.stringOrBlank("skuId"),
                skuCode = product.stringOrBlank("skuCode"),
                setupCode = product.stringOrBlank("setupCode"),
                hardwareRevision = product.stringOrBlank("hardwareRevision")
            ),
            firmwareVersion = firmware?.stringOrBlank("version").orEmpty(),
            firmwareBuild = firmware?.stringOrBlank("build").orEmpty(),
            apiVersion = firmware?.stringOrBlank("apiVersion").orEmpty(),
            protocolVersion = firmware?.stringOrBlank("protocolVersion").orEmpty(),
            endpoint = DeviceRuntimeEndpoint(
                ip = endpointIp,
                wifiMode = network.stringOrBlank("wifiMode"),
                wifiConnected = network.booleanOrFalse("wifiConnected"),
                setupApActive = network.booleanOrFalse("setupApActive"),
                runtimeTransport = runtimeTransport,
                wsPort = wsPort,
                wsPath = wsPath,
                wsProtocol = wsProtocol,
                wsProtocolVersion = wsProtocolVersion,
                discoveryPort = network.intOrZero("discoveryPort")
            ),
            capabilities = DeviceCapabilities(
                light = capabilities.booleanOrFalse("light"),
                manualLight = capabilities.booleanOrFalse("manualLight"),
                lightProgram = capabilities.booleanOrFalse("lightProgram"),
                lightPresets = capabilities.booleanOrFalse("lightPresets"),
                lightSimulation = capabilities.booleanOrFalse("lightSimulation"),
                fan = capabilities.booleanOrFalse("fan"),
                cooling = capabilities.booleanOrFalse("cooling"),
                temperature = capabilities.booleanOrFalse("temperature"),
                standaloneTimer = capabilities.booleanOrFalse("standaloneTimer"),
                dosing = capabilities.booleanOrFalse("dosing"),
                timeSync = capabilities.booleanOrFalse("timeSync"),
                ota = capabilities.booleanOrFalse("ota")
            ),
            limits = DeviceLimits(
                lightChannelCount = limits.intOrZero("lightChannelCount"),
                fanOutputCount = limits.intOrZero("fanOutputCount"),
                fanChannelCount = limits.intOrZero("fanChannelCount").takeIf { it > 0 }
                    ?: limits.intOrZero("fanOutputCount"),
                temperatureSensorCount = limits.intOrZero("temperatureSensorCount"),
                timerChannelCount = limits.intOrZero("timerChannelCount"),
                dosingChannelCount = limits.intOrZero("dosingChannelCount")
            ),
            supportedFeatures = root.stringListFrom("supportedFeatures")
                .ifEmpty { product.stringListFrom("supportedFeatures") },
            supportedScreens = root.stringListFrom("supportedScreens")
                .ifEmpty { product.stringListFrom("supportedScreens") },
            modules = root.stringListFrom("modules"),
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.ONLINE_LAN,
                lastUdpSeenAtMillis = receivedAtMillis
            ),
            lastSeenAtMillis = receivedAtMillis
        )

        return ParseResult.Valid(
            AqlDiscoveredDevice(
                snapshot = snapshot,
                sourceIp = sourceIp,
                receivedAtMillis = receivedAtMillis
            )
        )
    }

    sealed class ParseResult {
        data class Valid(val device: AqlDiscoveredDevice) : ParseResult()
        data class Invalid(val error: ParseError) : ParseResult()
    }

    enum class ParseError {
        EMPTY_PAYLOAD,
        PACKET_TOO_LARGE,
        INVALID_JSON,
        UNSUPPORTED_SCHEMA,
        UNSUPPORTED_MESSAGE_TYPE,
        UNSUPPORTED_UDP_VERSION,
        MISSING_DEVICE,
        MISSING_PRODUCT,
        MISSING_NETWORK,
        MISSING_DEVICE_UID,
        UNSUPPORTED_PRODUCT_FAMILY,
        UNSUPPORTED_RUNTIME_TRANSPORT,
        UNSUPPORTED_WS_PROTOCOL,
        UNSUPPORTED_WS_PROTOCOL_VERSION,
        MISSING_RUNTIME_ENDPOINT
    }
}

private fun JSONObject?.stringOrBlankOrFallback(
    primary: String,
    fallback: String
): String {
    return this?.stringOrBlank(primary).orEmpty().ifBlank { fallback }
}

private fun JSONObject?.intOrZeroOrFallback(
    primary: String,
    fallback: Int
): Int {
    return this?.intOrZero(primary)?.takeIf { value -> value > 0 } ?: fallback
}

private fun JSONObject?.stringOrBlank(name: String): String = this?.let { json ->
    when (val value = json.opt(name)) {
        null, JSONObject.NULL -> ""
        is String -> value.trim()
        else -> value.toString().trim()
    }
}.orEmpty()

private fun JSONObject?.intOrZero(name: String): Int = this?.let { json ->
    when (val value = json.opt(name)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull() ?: 0
        else -> 0
    }
} ?: 0

private fun JSONObject?.booleanOrFalse(name: String): Boolean = this?.let { json ->
    when (val value = json.opt(name)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.trim().equals("true", ignoreCase = true) || value.trim() == "1"
        else -> false
    }
} ?: false

private fun JSONObject?.stringListFrom(name: String): List<String> =
    this?.optJSONArray(name)?.toStringList().orEmpty()

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        val value = opt(index)
        if (value != null && value != JSONObject.NULL) {
            val text = value.toString().trim()
            if (text.isNotBlank()) add(text)
        }
    }
}

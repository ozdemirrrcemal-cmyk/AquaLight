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
import org.json.JSONException
import org.json.JSONObject

/**
 * Strict parser for the final AquaLight UDP discovery packet produced by
 * src/network/AqlDiscoveryService.hpp BuildDiscoveryJson().
 *
 * UDP discovery is only the LAN WebSocket endpoint handoff. Android accepts exactly one
 * commercial contract shape and rejects alternate packet layouts.
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

        if (root.stringOrBlank("schema") != AqlDiscoveryContract.SCHEMA) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_SCHEMA)
        }

        if (root.stringOrBlank("type") != AqlDiscoveryContract.TYPE_DEVICE_ANNOUNCE) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_MESSAGE_TYPE)
        }

        if (root.intOrNull("version") != AqlDiscoveryContract.VERSION) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_UDP_VERSION)
        }

        val device = root.optJSONObject("device")
            ?: return ParseResult.Invalid(ParseError.MISSING_DEVICE)
        val product = root.optJSONObject("product")
            ?: return ParseResult.Invalid(ParseError.MISSING_PRODUCT)
        val network = root.optJSONObject("network")
            ?: return ParseResult.Invalid(ParseError.MISSING_NETWORK)
        val runtime = root.optJSONObject("runtime")
            ?: return ParseResult.Invalid(ParseError.MISSING_RUNTIME)

        val deviceUid = device.stringOrBlank("uid")
        if (deviceUid.isBlank()) {
            return ParseResult.Invalid(ParseError.MISSING_DEVICE_UID)
        }

        val familyRaw = product.stringOrBlank("family")
        val family = DeviceFamily.fromWire(familyRaw)
        if (family == DeviceFamily.UNKNOWN) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_PRODUCT_FAMILY)
        }

        val runtimeTransport = runtime.stringOrBlank("transport")
        if (runtimeTransport != AqlDiscoveryContract.RUNTIME_TRANSPORT_WEBSOCKET) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_RUNTIME_TRANSPORT)
        }

        val endpointIp = runtime.stringOrBlank("host")
        val wsPort = runtime.intOrNull("port") ?: 0
        val wsPath = runtime.stringOrBlank("path")
        if (endpointIp.isBlank() || wsPort <= 0 || wsPath.isBlank()) {
            return ParseResult.Invalid(ParseError.MISSING_RUNTIME_ENDPOINT)
        }

        val wsProtocol = runtime.stringOrBlank("protocol")
        if (wsProtocol != AqlWsContract.DEFAULT_PROTOCOL) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_WS_PROTOCOL)
        }

        val wsProtocolVersion = runtime.intOrNull("protocolVersion") ?: 0
        if (wsProtocolVersion != AqlWsContract.PROTOCOL_VERSION) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_WS_PROTOCOL_VERSION)
        }

        val snapshot = DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(deviceUid),
                shortId = device.stringOrBlank("shortId"),
                displayName = device.stringOrBlank("name"),
                customName = ""
            ),
            product = DeviceProduct(
                family = family,
                familyRaw = familyRaw,
                model = product.stringOrBlank("model"),
                displayName = product.stringOrBlank("name")
            ),
            endpoint = DeviceRuntimeEndpoint(
                ip = endpointIp,
                wifiMode = network.stringOrBlank("mode"),
                wifiConnected = network.booleanOrNull("connected") == true,
                setupApActive = false,
                runtimeTransport = runtimeTransport,
                wsPort = wsPort,
                wsPath = wsPath,
                wsProtocol = wsProtocol,
                wsProtocolVersion = wsProtocolVersion,
                discoveryPort = AqlDiscoveryContract.PORT
            ),
            capabilities = DeviceCapabilities(),
            limits = DeviceLimits(),
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
        MISSING_RUNTIME,
        MISSING_DEVICE_UID,
        UNSUPPORTED_PRODUCT_FAMILY,
        UNSUPPORTED_RUNTIME_TRANSPORT,
        UNSUPPORTED_WS_PROTOCOL,
        UNSUPPORTED_WS_PROTOCOL_VERSION,
        MISSING_RUNTIME_ENDPOINT
    }
}

private fun JSONObject.stringOrBlank(name: String): String =
    when (val value = opt(name)) {
        null, JSONObject.NULL -> ""
        is String -> value.trim()
        else -> value.toString().trim()
    }

private fun JSONObject.intOrNull(name: String): Int? =
    when (val value = opt(name)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }

private fun JSONObject.booleanOrNull(name: String): Boolean? =
    when (val value = opt(name)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        else -> null
    }

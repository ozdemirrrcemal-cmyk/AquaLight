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
 * Exact parser for the final AquaLight UDP discovery packet produced by
 * src/network/AqlDiscoveryService.hpp BuildDiscoveryJson().
 *
 * UDP discovery is only the LAN WebSocket endpoint handoff. Android accepts exactly one
 * unpublished commercial contract shape and rejects aliases, coercion, normalization,
 * extra fields and legacy/alternate packet layouts.
 */
object AqlDiscoveryParser {

    @Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun parseDeviceAnnounce(
        rawPayload: String,
        sourceIp: String = "",
        receivedAtMillis: Long = System.currentTimeMillis()
    ): ParseResult {
        if (rawPayload.isBlank()) return ParseResult.Invalid(ParseError.EMPTY_PAYLOAD)
        if (rawPayload != rawPayload.trim()) {
            return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        }
        if (rawPayload.toByteArray(Charsets.UTF_8).size >
            AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES
        ) {
            return ParseResult.Invalid(ParseError.PACKET_TOO_LARGE)
        }

        val root = try {
            JSONObject(rawPayload)
        } catch (_: JSONException) {
            return ParseResult.Invalid(ParseError.INVALID_JSON)
        }

        if (!root.hasExactKeys(ROOT_KEYS) || root.exactLongOrNull("sentAtMs") == null) {
            return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        }
        if (root.exactStringOrNull("schema") != AqlDiscoveryContract.SCHEMA) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_SCHEMA)
        }
        if (root.exactStringOrNull("type") != AqlDiscoveryContract.TYPE_DEVICE_ANNOUNCE) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_MESSAGE_TYPE)
        }
        if (root.exactIntOrNull("version") != AqlDiscoveryContract.VERSION) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_UDP_VERSION)
        }

        val device = root.exactObjectOrNull("device")
            ?: return ParseResult.Invalid(ParseError.MISSING_DEVICE)
        val product = root.exactObjectOrNull("product")
            ?: return ParseResult.Invalid(ParseError.MISSING_PRODUCT)
        val network = root.exactObjectOrNull("network")
            ?: return ParseResult.Invalid(ParseError.MISSING_NETWORK)
        val runtime = root.exactObjectOrNull("runtime")
            ?: return ParseResult.Invalid(ParseError.MISSING_RUNTIME)
        if (
            !device.hasExactKeys(DEVICE_KEYS) ||
            !product.hasExactKeys(PRODUCT_KEYS) ||
            !network.hasExactKeys(NETWORK_KEYS) ||
            !runtime.hasExactKeys(RUNTIME_KEYS)
        ) {
            return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        }

        val deviceUid = device.exactStringOrNull("uid")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        val shortId = device.exactStringOrNull("shortId")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        val deviceName = device.exactStringOrNull("name")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        if (deviceUid.isBlank()) {
            return ParseResult.Invalid(ParseError.MISSING_DEVICE_UID)
        }

        val familyRaw = product.exactStringOrNull("family")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        val family = DeviceFamily.fromWireExact(familyRaw)
            ?: return ParseResult.Invalid(ParseError.UNSUPPORTED_PRODUCT_FAMILY)
        val productModel = product.exactStringOrNull("model")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        val productName = product.exactStringOrNull("name")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)

        val networkMode = network.exactStringOrNull("mode")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        val networkConnected = network.exactBooleanOrNull("connected")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)

        val runtimeTransport = runtime.exactStringOrNull("transport")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        if (runtimeTransport != AqlDiscoveryContract.RUNTIME_TRANSPORT_WEBSOCKET) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_RUNTIME_TRANSPORT)
        }

        val endpointIp = runtime.exactStringOrNull("host")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        val wsPort = runtime.exactIntOrNull("port")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        val wsPath = runtime.exactStringOrNull("path")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        if (endpointIp.isBlank() || wsPort <= 0 || wsPath.isBlank()) {
            return ParseResult.Invalid(ParseError.MISSING_RUNTIME_ENDPOINT)
        }

        val wsProtocol = runtime.exactStringOrNull("protocol")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        if (wsProtocol != AqlWsContract.DEFAULT_PROTOCOL) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_WS_PROTOCOL)
        }

        val wsProtocolVersion = runtime.exactIntOrNull("protocolVersion")
            ?: return ParseResult.Invalid(ParseError.INVALID_CONTRACT_SHAPE)
        if (wsProtocolVersion != AqlWsContract.PROTOCOL_VERSION) {
            return ParseResult.Invalid(ParseError.UNSUPPORTED_WS_PROTOCOL_VERSION)
        }

        val snapshot = DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(deviceUid),
                shortId = shortId,
                displayName = deviceName,
                customName = ""
            ),
            product = DeviceProduct(
                family = family,
                familyRaw = familyRaw,
                model = productModel,
                displayName = productName
            ),
            endpoint = DeviceRuntimeEndpoint(
                ip = endpointIp,
                wifiMode = networkMode,
                wifiConnected = networkConnected,
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
        INVALID_CONTRACT_SHAPE,
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

    private val ROOT_KEYS = setOf(
        "schema", "type", "version", "sentAtMs", "device", "product", "network", "runtime"
    )
    private val DEVICE_KEYS = setOf("uid", "shortId", "name")
    private val PRODUCT_KEYS = setOf("family", "model", "name")
    private val NETWORK_KEYS = setOf("mode", "connected")
    private val RUNTIME_KEYS = setOf(
        "transport", "host", "port", "path", "protocol", "protocolVersion"
    )
}

private fun JSONObject.hasExactKeys(expected: Set<String>): Boolean {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    return actual == expected
}

private fun JSONObject.exactObjectOrNull(name: String): JSONObject? =
    opt(name) as? JSONObject

private fun JSONObject.exactStringOrNull(name: String): String? =
    opt(name) as? String

private fun JSONObject.exactBooleanOrNull(name: String): Boolean? =
    opt(name) as? Boolean

private fun JSONObject.exactIntOrNull(name: String): Int? {
    val value = opt(name) as? Number ?: return null
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    val valid = asDouble.isFinite() &&
        asDouble == asLong.toDouble() &&
        asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    return asLong.toInt().takeIf { valid }
}

private fun JSONObject.exactLongOrNull(name: String): Long? {
    val value = opt(name) as? Number ?: return null
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    return asLong.takeIf { asDouble.isFinite() && asDouble == asLong.toDouble() }
}

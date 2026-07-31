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
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import org.json.JSONException
import org.json.JSONObject

/**
 * Fail-closed parser for `aql.discovery.v1`.
 *
 * UDP is only an untrusted LAN endpoint hint. The packet must exactly match the bounded firmware
 * contract, route only to the datagram source on a private IPv4 address, and is never accepted as
 * proof of authenticated control or commercial metadata.
 */
object AqlDiscoveryParser {

    fun parseDeviceAnnounce(
        rawPayload: String,
        sourceIp: String = "",
        receivedAtMillis: Long = System.currentTimeMillis()
    ): ParseResult {
        if (rawPayload.isBlank()) return ParseResult.Invalid(ParseError.EMPTY_PAYLOAD)
        if (rawPayload.toByteArray(Charsets.UTF_8).size > AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES) {
            return ParseResult.Invalid(ParseError.PACKET_TOO_LARGE)
        }

        when (scanJsonStructure(rawPayload)) {
            JsonScanResult.Valid -> Unit
            JsonScanResult.DuplicateField ->
                return ParseResult.Invalid(ParseError.DUPLICATE_FIELD)
            JsonScanResult.Invalid ->
                return ParseResult.Invalid(ParseError.INVALID_JSON)
        }

        val root = try {
            JSONObject(rawPayload)
        } catch (_: JSONException) {
            return ParseResult.Invalid(ParseError.INVALID_JSON)
        }

        return try {
            root.requireExactKeys(ROOT_KEYS, ParseError.UNEXPECTED_FIELD)
            requireWire(root.requireString("schema") == AqlDiscoveryContract.SCHEMA) {
                ParseError.UNSUPPORTED_SCHEMA
            }
            requireWire(
                root.requireString("type") == AqlDiscoveryContract.TYPE_DEVICE_ANNOUNCE
            ) { ParseError.UNSUPPORTED_MESSAGE_TYPE }
            requireWire(root.requireInt("version") == AqlDiscoveryContract.VERSION) {
                ParseError.UNSUPPORTED_UDP_VERSION
            }
            root.requireNonNegativeLong("sentAtMs")

            val device = root.requireObject("device", ParseError.MISSING_DEVICE)
            val product = root.requireObject("product", ParseError.MISSING_PRODUCT)
            val network = root.requireObject("network", ParseError.MISSING_NETWORK)
            val runtime = root.requireObject("runtime", ParseError.MISSING_RUNTIME)
            device.requireExactKeys(DEVICE_KEYS, ParseError.UNEXPECTED_FIELD)
            product.requireExactKeys(PRODUCT_KEYS, ParseError.UNEXPECTED_FIELD)
            network.requireExactKeys(NETWORK_KEYS, ParseError.UNEXPECTED_FIELD)
            runtime.requireExactKeys(RUNTIME_KEYS, ParseError.UNEXPECTED_FIELD)

            val deviceUid = device.requireString("uid")
            requireWire(deviceUid.isNotEmpty()) { ParseError.MISSING_DEVICE_UID }
            val shortId = device.requireString("shortId")
            val effectiveDisplayName = device.requireString("name")

            val familyRaw = product.requireString("family")
            val family = DeviceFamily.fromWireExact(familyRaw)
                ?: throw ContractViolation(ParseError.UNSUPPORTED_PRODUCT_FAMILY)
            val model = product.requireString("model")
            val productDisplayName = product.requireString("name")

            val networkMode = network.requireString("mode")
            requireWire(networkMode in NETWORK_MODES) {
                ParseError.UNSUPPORTED_NETWORK_MODE
            }
            val networkConnected = network.requireBoolean("connected")

            val runtimeTransport = runtime.requireString("transport")
            requireWire(runtimeTransport == AqlDiscoveryContract.RUNTIME_TRANSPORT_WEBSOCKET) {
                ParseError.UNSUPPORTED_RUNTIME_TRANSPORT
            }
            val endpointIp = runtime.requireString("host")
            val wsPort = runtime.requireInt("port")
            val wsPath = runtime.requireString("path")
            val wsProtocol = runtime.requireString("protocol")
            val wsProtocolVersion = runtime.requireInt("protocolVersion")

            requireWire(wsPort == AqlWsContract.DEFAULT_PORT &&
                wsPath == AqlWsContract.DEFAULT_PATH) {
                ParseError.MISSING_RUNTIME_ENDPOINT
            }
            requireWire(wsProtocol == AqlWsContract.DEFAULT_PROTOCOL) {
                ParseError.UNSUPPORTED_WS_PROTOCOL
            }
            requireWire(wsProtocolVersion == AqlWsContract.PROTOCOL_VERSION) {
                ParseError.UNSUPPORTED_WS_PROTOCOL_VERSION
            }
            requireWire(sourceIp.isBlank() || endpointIp == sourceIp.trim()) {
                ParseError.SOURCE_HOST_MISMATCH
            }

            val endpoint = DeviceRuntimeEndpoint(
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
            )
            requireWire(endpoint.hasWebSocketEndpoint) { ParseError.UNSAFE_RUNTIME_ENDPOINT }

            val customName = if (effectiveDisplayName == productDisplayName) {
                ""
            } else {
                effectiveDisplayName
            }
            val snapshot = DeviceSnapshot(
                identity = DeviceIdentity(
                    uid = DeviceUid(deviceUid),
                    shortId = shortId,
                    displayName = productDisplayName,
                    customName = customName
                ),
                product = DeviceProduct(
                    family = family,
                    familyRaw = familyRaw,
                    model = model,
                    displayName = productDisplayName
                ),
                endpoint = endpoint,
                capabilities = DeviceCapabilities(),
                limits = DeviceLimits(),
                connectionState = DeviceConnectionState(
                    onlineState = DeviceOnlineState.ONLINE_LAN,
                    lastUdpSeenAtMillis = receivedAtMillis
                ),
                lastSeenAtMillis = receivedAtMillis
            )

            ParseResult.Valid(
                AqlDiscoveredDevice(
                    snapshot = snapshot,
                    sourceIp = sourceIp.trim(),
                    receivedAtMillis = receivedAtMillis
                )
            )
        } catch (violation: ContractViolation) {
            ParseResult.Invalid(violation.error)
        } catch (_: JSONException) {
            ParseResult.Invalid(ParseError.INVALID_FIELD_TYPE)
        } catch (_: IllegalArgumentException) {
            ParseResult.Invalid(ParseError.INVALID_FIELD_TYPE)
        }
    }

    sealed class ParseResult {
        data class Valid(val device: AqlDiscoveredDevice) : ParseResult()
        data class Invalid(val error: ParseError) : ParseResult()
    }

    enum class ParseError {
        EMPTY_PAYLOAD,
        PACKET_TOO_LARGE,
        INVALID_JSON,
        DUPLICATE_FIELD,
        UNEXPECTED_FIELD,
        INVALID_FIELD_TYPE,
        UNSUPPORTED_SCHEMA,
        UNSUPPORTED_MESSAGE_TYPE,
        UNSUPPORTED_UDP_VERSION,
        MISSING_DEVICE,
        MISSING_PRODUCT,
        MISSING_NETWORK,
        MISSING_RUNTIME,
        MISSING_DEVICE_UID,
        UNSUPPORTED_PRODUCT_FAMILY,
        UNSUPPORTED_NETWORK_MODE,
        UNSUPPORTED_RUNTIME_TRANSPORT,
        UNSUPPORTED_WS_PROTOCOL,
        UNSUPPORTED_WS_PROTOCOL_VERSION,
        MISSING_RUNTIME_ENDPOINT,
        SOURCE_HOST_MISMATCH,
        UNSAFE_RUNTIME_ENDPOINT
    }

    private val ROOT_KEYS = setOf(
        "schema", "type", "version", "sentAtMs", "device", "product", "network", "runtime"
    )
    private val DEVICE_KEYS = setOf("uid", "shortId", "name")
    private val PRODUCT_KEYS = setOf("family", "model", "name")
    private val NETWORK_KEYS = setOf("mode", "connected")
    private val NETWORK_MODES = setOf("off", "sta", "ap", "ap_sta", "unknown")
    private val RUNTIME_KEYS = setOf(
        "transport", "host", "port", "path", "protocol", "protocolVersion"
    )
}

private class ContractViolation(val error: AqlDiscoveryParser.ParseError) : RuntimeException()

private inline fun requireWire(
    condition: Boolean,
    error: () -> AqlDiscoveryParser.ParseError
) {
    if (!condition) throw ContractViolation(error())
}

private fun JSONObject.requireExactKeys(
    expected: Set<String>,
    error: AqlDiscoveryParser.ParseError
) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    if (actual != expected) throw ContractViolation(error)
}

private fun JSONObject.requireObject(
    key: String,
    missingError: AqlDiscoveryParser.ParseError
): JSONObject {
    if (!has(key) || isNull(key)) throw ContractViolation(missingError)
    return get(key) as? JSONObject
        ?: throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
}

private fun JSONObject.requireString(key: String): String {
    if (!has(key) || isNull(key)) {
        throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    }
    val value = get(key) as? String
        ?: throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    if (value.isEmpty() || value.first().isWhitespace() || value.last().isWhitespace() ||
        value.any(Char::isISOControl)) {
        throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    }
    return value
}

private fun JSONObject.requireBoolean(key: String): Boolean {
    if (!has(key) || isNull(key)) {
        throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    }
    return get(key) as? Boolean
        ?: throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
}

private fun JSONObject.requireInt(key: String): Int {
    val value = requireIntegerNumber(key)
    if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    }
    return value.toInt()
}

private fun JSONObject.requireNonNegativeLong(key: String): Long {
    val value = requireIntegerNumber(key)
    if (value < 0L) throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    return value
}

private fun JSONObject.requireIntegerNumber(key: String): Long {
    if (!has(key) || isNull(key)) {
        throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    }
    val value = get(key) as? Number
        ?: throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    val asLong = value.toLong()
    if (!value.toDouble().isFinite() || value.toDouble() != asLong.toDouble()) {
        throw ContractViolation(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE)
    }
    return asLong
}

private sealed interface JsonScanResult {
    data object Valid : JsonScanResult
    data object DuplicateField : JsonScanResult
    data object Invalid : JsonScanResult
}

private fun scanJsonStructure(raw: String): JsonScanResult = try {
    JsonReader(StringReader(raw)).use { reader ->
        reader.isLenient = false
        scanJsonValue(reader)
        if (reader.peek() != JsonToken.END_DOCUMENT) JsonScanResult.Invalid else JsonScanResult.Valid
    }
} catch (_: DuplicateJsonFieldException) {
    JsonScanResult.DuplicateField
} catch (_: Exception) {
    JsonScanResult.Invalid
}

private fun scanJsonValue(reader: JsonReader) {
    when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            reader.beginObject()
            val fields = hashSetOf<String>()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (!fields.add(name)) throw DuplicateJsonFieldException()
                scanJsonValue(reader)
            }
            reader.endObject()
        }
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            while (reader.hasNext()) scanJsonValue(reader)
            reader.endArray()
        }
        JsonToken.STRING -> reader.nextString()
        JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.NULL -> reader.nextNull()
        else -> throw IllegalArgumentException("Invalid JSON token")
    }
}

private class DuplicateJsonFieldException : RuntimeException()

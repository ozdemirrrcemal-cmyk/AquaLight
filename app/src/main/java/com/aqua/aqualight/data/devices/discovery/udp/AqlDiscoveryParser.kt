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
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

/** Exact fail-closed decoder for the firmware UDP v1 device announce packet. */
@Suppress("TooManyFunctions")
object AqlDiscoveryParser {

    fun parseDeviceAnnounce(
        rawPayload: String,
        sourceIp: String = "",
        receivedAtMillis: Long = System.currentTimeMillis()
    ): ParseResult {
        if (rawPayload.isEmpty()) return ParseResult.Invalid(ParseError.EMPTY_PAYLOAD)
        if (rawPayload.toByteArray(StandardCharsets.UTF_8).size >
            AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES
        ) {
            return ParseResult.Invalid(ParseError.PACKET_TOO_LARGE)
        }

        val root = try {
            validateJsonStructure(rawPayload)
            JSONObject(rawPayload)
        } catch (error: DiscoveryParseException) {
            return ParseResult.Invalid(error.parseError)
        } catch (_: JSONException) {
            return ParseResult.Invalid(ParseError.INVALID_JSON)
        } catch (_: Throwable) {
            return ParseResult.Invalid(ParseError.INVALID_JSON)
        }

        return try {
            parseExact(root, sourceIp, receivedAtMillis)
        } catch (error: DiscoveryParseException) {
            ParseResult.Invalid(error.parseError)
        } catch (_: Throwable) {
            ParseResult.Invalid(ParseError.INVALID_FIELD_VALUE)
        }
    }

    private fun parseExact(
        root: JSONObject,
        sourceIp: String,
        receivedAtMillis: Long
    ): ParseResult.Valid {
        root.requireExactKeys(ROOT_KEYS)

        if (root.requiredText("schema") != AqlDiscoveryContract.SCHEMA) {
            fail(ParseError.UNSUPPORTED_SCHEMA)
        }
        if (root.requiredText("type") != AqlDiscoveryContract.TYPE_DEVICE_ANNOUNCE) {
            fail(ParseError.UNSUPPORTED_MESSAGE_TYPE)
        }
        if (root.requiredInt("version") != AqlDiscoveryContract.VERSION) {
            fail(ParseError.UNSUPPORTED_UDP_VERSION)
        }
        root.requiredLong("sentAtMs").also { sentAtMs ->
            if (sentAtMs !in 0L..MAX_FIRMWARE_MILLIS) fail(ParseError.INVALID_FIELD_VALUE)
        }

        val device = root.requiredObject("device", ParseError.MISSING_DEVICE).also {
            it.requireExactKeys(DEVICE_KEYS)
        }
        val product = root.requiredObject("product", ParseError.MISSING_PRODUCT).also {
            it.requireExactKeys(PRODUCT_KEYS)
        }
        val network = root.requiredObject("network", ParseError.MISSING_NETWORK).also {
            it.requireExactKeys(NETWORK_KEYS)
        }
        val runtime = root.requiredObject("runtime", ParseError.MISSING_RUNTIME).also {
            it.requireExactKeys(RUNTIME_KEYS)
        }

        val deviceUidText = device.requiredText("uid")
        val shortId = device.requiredText("shortId")
        val deviceName = device.requiredText("name")
        val familyRaw = product.requiredText("family")
        val family = DeviceFamily.fromWireExact(familyRaw)
            ?: fail(ParseError.UNSUPPORTED_PRODUCT_FAMILY)
        val productModel = product.requiredText("model")
        val productName = product.requiredText("name")

        val networkMode = network.requiredText("mode")
        if (networkMode !in NETWORK_MODES) fail(ParseError.UNSUPPORTED_NETWORK_MODE)
        val networkConnected = network.requiredBoolean("connected")
        if (networkConnected && networkMode !in CONNECTED_NETWORK_MODES) {
            fail(ParseError.INVALID_NETWORK_STATE)
        }

        val runtimeTransport = runtime.requiredText("transport")
        if (runtimeTransport != AqlDiscoveryContract.RUNTIME_TRANSPORT_WEBSOCKET) {
            fail(ParseError.UNSUPPORTED_RUNTIME_TRANSPORT)
        }

        val runtimeHost = runtime.requiredText("host")
        if (!runtimeHost.isCanonicalPrivateLanIpv4()) {
            fail(ParseError.INVALID_RUNTIME_HOST)
        }
        val canonicalSourceIp = sourceIp.takeIf(String::isNotEmpty)?.also { value ->
            if (!value.isCanonicalPrivateLanIpv4()) fail(ParseError.INVALID_SOURCE_IP)
            if (value != runtimeHost) fail(ParseError.RUNTIME_HOST_SOURCE_MISMATCH)
        }.orEmpty()

        val wsPort = runtime.requiredInt("port")
        if (wsPort !in MIN_PORT..MAX_PORT) fail(ParseError.MISSING_RUNTIME_ENDPOINT)

        val wsPath = runtime.requiredText("path")
        if (wsPath != AqlWsContract.DEFAULT_PATH) {
            fail(ParseError.UNSUPPORTED_WS_PATH)
        }
        val wsProtocol = runtime.requiredText("protocol")
        if (wsProtocol != AqlWsContract.DEFAULT_PROTOCOL) {
            fail(ParseError.UNSUPPORTED_WS_PROTOCOL)
        }
        val wsProtocolVersion = runtime.requiredInt("protocolVersion")
        if (wsProtocolVersion != AqlWsContract.PROTOCOL_VERSION) {
            fail(ParseError.UNSUPPORTED_WS_PROTOCOL_VERSION)
        }

        val endpoint = DeviceRuntimeEndpoint(
            ip = runtimeHost,
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
        if (!endpoint.hasWebSocketEndpoint) fail(ParseError.MISSING_RUNTIME_ENDPOINT)

        val snapshot = DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(deviceUidText),
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
            endpoint = endpoint,
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
                sourceIp = canonicalSourceIp,
                receivedAtMillis = receivedAtMillis
            )
        )
    }

    private fun validateJsonStructure(raw: String) {
        try {
            JsonReader(StringReader(raw)).use { reader ->
                reader.isLenient = false
                readStrictValue(reader, depth = 1, counter = JsonCounter())
                if (reader.peek() != JsonToken.END_DOCUMENT) fail(ParseError.INVALID_JSON)
            }
        } catch (error: DiscoveryParseException) {
            throw error
        } catch (_: Throwable) {
            fail(ParseError.INVALID_JSON)
        }
    }

    private fun readStrictValue(
        reader: JsonReader,
        depth: Int,
        counter: JsonCounter
    ) {
        if (depth > MAX_JSON_DEPTH) fail(ParseError.JSON_LIMIT_EXCEEDED)
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val names = mutableSetOf<String>()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (!names.add(name)) fail(ParseError.DUPLICATE_FIELD)
                    if (name.toByteArray(StandardCharsets.UTF_8).size > MAX_JSON_KEY_BYTES) {
                        fail(ParseError.JSON_LIMIT_EXCEEDED)
                    }
                    counter.keyCount += 1
                    if (counter.keyCount > MAX_JSON_KEYS) fail(ParseError.JSON_LIMIT_EXCEEDED)
                    readStrictValue(reader, depth + 1, counter)
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) readStrictValue(reader, depth + 1, counter)
                reader.endArray()
            }
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> reader.nextNull()
            else -> fail(ParseError.INVALID_JSON)
        }
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>) {
        val actual = keys().asSequence().toSet()
        if (actual != expected) {
            fail(
                if (actual.containsAll(expected)) ParseError.UNEXPECTED_FIELD
                else ParseError.MISSING_REQUIRED_FIELD
            )
        }
    }

    private fun JSONObject.requiredObject(name: String, error: ParseError): JSONObject =
        (opt(name) as? JSONObject) ?: fail(error)

    private fun JSONObject.requiredText(name: String): String {
        val value = opt(name) as? String ?: fail(ParseError.INVALID_FIELD_TYPE)
        if (
            value.isEmpty() ||
            value != value.trim() ||
            value.hasControlCharacter()
        ) {
            fail(ParseError.INVALID_FIELD_VALUE)
        }
        return value
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean =
        (opt(name) as? Boolean) ?: fail(ParseError.INVALID_FIELD_TYPE)

    private fun JSONObject.requiredInt(name: String): Int {
        val number = opt(name) as? Number ?: fail(ParseError.INVALID_FIELD_TYPE)
        val longValue = number.toLong()
        if (
            !number.toDouble().isFinite() ||
            number.toDouble() != longValue.toDouble() ||
            longValue !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        ) {
            fail(ParseError.INVALID_FIELD_VALUE)
        }
        return longValue.toInt()
    }

    private fun JSONObject.requiredLong(name: String): Long {
        val number = opt(name) as? Number ?: fail(ParseError.INVALID_FIELD_TYPE)
        val longValue = number.toLong()
        if (!number.toDouble().isFinite() || number.toDouble() != longValue.toDouble()) {
            fail(ParseError.INVALID_FIELD_VALUE)
        }
        return longValue
    }

    private fun String.hasControlCharacter(): Boolean = any { character ->
        character.code < 0x20 || character.code == 0x7f
    }

    private fun String.isCanonicalPrivateLanIpv4(): Boolean {
        val octets = split('.')
        if (octets.size != IPV4_OCTET_COUNT) return false
        val values = octets.map { octet ->
            if (octet.isEmpty() || octet.length > IPV4_MAX_OCTET_DIGITS) return false
            if (octet.any { character -> character !in '0'..'9' }) return false
            val value = octet.toIntOrNull()?.takeIf { it in IPV4_OCTET_RANGE } ?: return false
            if (octet != value.toString()) return false
            value
        }
        val first = values[0]
        val second = values[1]
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }

    private fun fail(error: ParseError): Nothing = throw DiscoveryParseException(error)

    sealed class ParseResult {
        data class Valid(val device: AqlDiscoveredDevice) : ParseResult()
        data class Invalid(val error: ParseError) : ParseResult()
    }

    enum class ParseError {
        EMPTY_PAYLOAD,
        PACKET_TOO_LARGE,
        INVALID_JSON,
        DUPLICATE_FIELD,
        JSON_LIMIT_EXCEEDED,
        UNEXPECTED_FIELD,
        MISSING_REQUIRED_FIELD,
        INVALID_FIELD_TYPE,
        INVALID_FIELD_VALUE,
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
        INVALID_NETWORK_STATE,
        UNSUPPORTED_RUNTIME_TRANSPORT,
        INVALID_RUNTIME_HOST,
        INVALID_SOURCE_IP,
        RUNTIME_HOST_SOURCE_MISMATCH,
        UNSUPPORTED_WS_PATH,
        UNSUPPORTED_WS_PROTOCOL,
        UNSUPPORTED_WS_PROTOCOL_VERSION,
        MISSING_RUNTIME_ENDPOINT
    }

    private class DiscoveryParseException(
        val parseError: ParseError
    ) : IllegalArgumentException(parseError.name)

    private class JsonCounter(var keyCount: Int = 0)

    private const val MAX_FIRMWARE_MILLIS = 4_294_967_295L
    private const val MAX_JSON_DEPTH = 4
    private const val MAX_JSON_KEYS = 32
    private const val MAX_JSON_KEY_BYTES = 32
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535
    private const val IPV4_OCTET_COUNT = 4
    private const val IPV4_MAX_OCTET_DIGITS = 3
    private val IPV4_OCTET_RANGE = 0..255
    private val NETWORK_MODES = setOf("off", "sta", "ap", "ap_sta", "unknown")
    private val CONNECTED_NETWORK_MODES = setOf("sta", "ap_sta")
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

package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONException
import org.json.JSONObject

internal sealed interface AqlWsDecodedFrame {
    data class Hello(val challenge: AqlWsHelloChallenge) : AqlWsDecodedFrame
    data class Authenticated(val secureSession: AqlWsSecureSession) : AqlWsDecodedFrame
    data class AuthRejected(val message: String) : AqlWsDecodedFrame
    data class Runtime(val message: AqlWsIncomingMessage) : AqlWsDecodedFrame
}

internal enum class AqlWsProtocolError(val safeMessage: String) {
    MESSAGE_TOO_LARGE("WebSocket message exceeds the protocol limit."),
    MALFORMED_JSON("WebSocket message is not valid JSON."),
    DUPLICATE_FIELD("WebSocket message contains a duplicate field."),
    JSON_LIMIT_EXCEEDED("WebSocket JSON exceeds the protocol limits."),
    UNEXPECTED_FIELD("WebSocket message contains an unexpected field."),
    MISSING_FIELD("WebSocket message is missing a required field."),
    INVALID_FIELD("WebSocket message contains an invalid field."),
    UNSUPPORTED_TYPE("WebSocket message type is not supported."),
    INCOMPATIBLE_PROTOCOL("Device WebSocket protocol is incompatible."),
    DEVICE_IDENTITY_MISMATCH("WebSocket device identity does not match the selected device."),
    AUTHENTICATION_OUT_OF_SEQUENCE("WebSocket authentication is out of sequence."),
    AUTHENTICATION_FAILED("Device authentication failed."),
    SECURITY_SESSION_REQUIRED("Authenticated WebSocket security session is required."),
    REPLAY_OR_INVALID_MAC("WebSocket message authentication or sequence validation failed."),
    INVALID_DATA("WebSocket data payload is invalid.")
}

internal class AqlWsProtocolException(
    val protocolError: AqlWsProtocolError
) : Exception(protocolError.safeMessage)

/**
 * Sole JSON wire encoder/decoder for the AquaLight WebSocket runtime.
 *
 * The codec mirrors the pinned firmware transport boundary: exact envelopes, duplicate/unknown
 * field rejection, bounded JSON structure, strict UTF-8, exact active event routes, HMAC and
 * monotonic sequence verification.
 */
internal class AqlWsWireCodec {

    fun decode(
        raw: String,
        expectedDeviceUid: String,
        pendingAuthentication: AqlWsPendingAuthentication?,
        secureSession: AqlWsSecureSession?
    ): AqlWsDecodedFrame {
        val root = parseRoot(raw)
        val type = requiredText(
            root,
            AqlWsContract.Field.TYPE,
            AqlWsContract.Limit.TYPE_CHARS
        )

        return when (type) {
            AqlWsContract.TYPE_HELLO -> decodeHello(root, expectedDeviceUid)
            AqlWsContract.TYPE_RESPONSE -> {
                if (root.isAuthenticationFrame()) {
                    decodeAuthenticationResponse(root, pendingAuthentication)
                } else {
                    decodeRuntimeResponse(root, requireSecureSession(secureSession))
                }
            }
            AqlWsContract.TYPE_ERROR -> {
                if (root.isAuthenticationFrame()) {
                    decodeAuthenticationError(root, pendingAuthentication)
                } else {
                    decodeRuntimeError(root, requireSecureSession(secureSession))
                }
            }
            AqlWsContract.TYPE_EVENT ->
                decodeRuntimeEvent(root, requireSecureSession(secureSession))
            else -> fail(AqlWsProtocolError.UNSUPPORTED_TYPE)
        }
    }

    fun prepareAuthentication(
        hello: AqlWsHelloChallenge,
        runtimeToken: String,
        requestId: String = AqlWsOutgoingMessage.nextId(prefix = "auth"),
        clientNonce: String = AqlWsCrypto.randomNonceHex()
    ): AqlWsPendingAuthentication = try {
        validateIdentifier(requestId, AqlWsContract.Limit.ID_CHARS)
        validateHex(clientNonce, AqlWsContract.Limit.NONCE_HEX_CHARS)
        AqlWsCrypto.prepareAuthentication(
            hello = hello,
            runtimeToken = runtimeToken,
            requestId = requestId,
            clientNonce = clientNonce
        )
    } catch (error: AqlWsProtocolException) {
        throw error
    } catch (_: Throwable) {
        fail(AqlWsProtocolError.AUTHENTICATION_FAILED)
    }

    fun encodeAuthenticationRequest(pending: AqlWsPendingAuthentication): String {
        val data = JSONObject()
            .put(AqlWsContract.Field.AUTH_SCHEME, AqlWsContract.AUTH_SCHEME)
            .put(AqlWsContract.Field.DEVICE_UID, pending.hello.deviceUid)
            .put(AqlWsContract.Field.SESSION_ID, pending.hello.sessionId)
            .put(AqlWsContract.Field.SERVER_NONCE, pending.hello.serverNonce)
            .put(AqlWsContract.Field.CLIENT_NONCE, pending.clientNonce)
            .put(AqlWsContract.Field.CLIENT_PROOF, pending.clientProof)
        return checkedWireJson(
            JSONObject()
                .put(AqlWsContract.Field.ID, pending.requestId)
                .put(AqlWsContract.Field.TYPE, AqlWsContract.TYPE_AUTH)
                .put(AqlWsContract.Field.MODULE, AqlWsContract.MODULE_SECURITY)
                .put(AqlWsContract.Field.ACTION, AqlWsContract.ACTION_SESSION_AUTHENTICATE)
                .put(AqlWsContract.Field.DATA, data)
        )
    }

    fun encode(
        message: AqlWsOutgoingMessage,
        secureSession: AqlWsSecureSession?
    ): String = when (message) {
        is AqlWsOutgoingMessage.Command -> encodeCommand(
            message = message,
            secureSession = requireSecureSession(secureSession)
        )
    }

    private fun encodeCommand(
        message: AqlWsOutgoingMessage.Command,
        secureSession: AqlWsSecureSession
    ): String {
        val id = checkedId(message.id)
        val module = checkedModule(message.module)
        val action = checkedAction(message.action)
        if (!AqlWsContract.isRegisteredCommand(module, action)) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }

        val encodedData = encodeData(message.data)
        val frame = AqlWsMacFrame(
            id = id,
            type = AqlWsContract.TYPE_COMMAND,
            module = module,
            action = action,
            dataBase64Url = encodedData
        )
        val security = secureSession.signClient(frame)
        return checkedWireJson(
            JSONObject()
                .put(AqlWsContract.Field.ID, id)
                .put(AqlWsContract.Field.TYPE, AqlWsContract.TYPE_COMMAND)
                .put(AqlWsContract.Field.MODULE, module)
                .put(AqlWsContract.Field.ACTION, action)
                .put(AqlWsContract.Field.DATA, encodedData)
                .put(AqlWsContract.Field.SECURITY, security.toJson())
        )
    }

    private fun decodeHello(
        root: JSONObject,
        expectedDeviceUid: String
    ): AqlWsDecodedFrame.Hello {
        requireExactKeys(
            root,
            setOf(
                AqlWsContract.Field.ID,
                AqlWsContract.Field.TYPE,
                AqlWsContract.Field.MODULE,
                AqlWsContract.Field.ACTION,
                AqlWsContract.Field.DATA,
                AqlWsContract.Field.META
            )
        )
        val id = checkedId(
            requiredText(root, AqlWsContract.Field.ID, AqlWsContract.Limit.ID_CHARS)
        )
        requireEquals(
            requiredText(root, AqlWsContract.Field.MODULE, AqlWsContract.Limit.MODULE_CHARS),
            AqlWsContract.MODULE_SECURITY
        )
        requireEquals(
            requiredText(root, AqlWsContract.Field.ACTION, AqlWsContract.Limit.ACTION_CHARS),
            AqlWsContract.ACTION_SESSION_CHALLENGE
        )

        val data = requiredObject(root, AqlWsContract.Field.DATA)
        requireExactKeys(
            data,
            setOf(
                AqlWsContract.Field.SCHEMA,
                AqlWsContract.Field.PROTOCOL_VERSION,
                AqlWsContract.Field.DEVICE_UID,
                AqlWsContract.Field.SESSION_ID,
                AqlWsContract.Field.SERVER_NONCE,
                AqlWsContract.Field.AUTH_SCHEME,
                AqlWsContract.Field.MAX_MESSAGE_BYTES
            )
        )
        requireEquals(
            requiredText(data, AqlWsContract.Field.SCHEMA, 32),
            AqlWsContract.SCHEMA,
            AqlWsProtocolError.INCOMPATIBLE_PROTOCOL
        )
        requireEquals(
            requiredInt(data, AqlWsContract.Field.PROTOCOL_VERSION),
            AqlWsContract.PROTOCOL_VERSION,
            AqlWsProtocolError.INCOMPATIBLE_PROTOCOL
        )
        requireEquals(
            requiredText(data, AqlWsContract.Field.AUTH_SCHEME, 32),
            AqlWsContract.AUTH_SCHEME,
            AqlWsProtocolError.INCOMPATIBLE_PROTOCOL
        )
        requireEquals(
            requiredInt(data, AqlWsContract.Field.MAX_MESSAGE_BYTES),
            AqlWsContract.Limit.MESSAGE_BYTES,
            AqlWsProtocolError.INCOMPATIBLE_PROTOCOL
        )

        val deviceUid = requiredText(
            data,
            AqlWsContract.Field.DEVICE_UID,
            AqlWsContract.Limit.DEVICE_UID_CHARS
        ).also(::validateIdentifier)
        if (!deviceUid.equals(expectedDeviceUid.trim(), ignoreCase = true)) {
            fail(AqlWsProtocolError.DEVICE_IDENTITY_MISMATCH)
        }
        val sessionId = requiredText(
            data,
            AqlWsContract.Field.SESSION_ID,
            AqlWsContract.Limit.SESSION_ID_CHARS
        ).also(::validateIdentifier)
        val serverNonce = requiredText(
            data,
            AqlWsContract.Field.SERVER_NONCE,
            AqlWsContract.Limit.NONCE_HEX_CHARS
        ).also { validateHex(it, AqlWsContract.Limit.NONCE_HEX_CHARS) }
        val firmwareVersion = validateMeta(requiredObject(root, AqlWsContract.Field.META))

        return AqlWsDecodedFrame.Hello(
            AqlWsHelloChallenge(
                id = id,
                deviceUid = deviceUid,
                sessionId = sessionId,
                serverNonce = serverNonce,
                firmwareVersion = firmwareVersion
            )
        )
    }

    private fun decodeAuthenticationResponse(
        root: JSONObject,
        pending: AqlWsPendingAuthentication?
    ): AqlWsDecodedFrame.Authenticated {
        val expected = pending ?: fail(AqlWsProtocolError.AUTHENTICATION_OUT_OF_SEQUENCE)
        requireExactKeys(
            root,
            setOf(
                AqlWsContract.Field.ID,
                AqlWsContract.Field.TYPE,
                AqlWsContract.Field.MODULE,
                AqlWsContract.Field.ACTION,
                AqlWsContract.Field.OK,
                AqlWsContract.Field.STATUS,
                AqlWsContract.Field.DATA,
                AqlWsContract.Field.META
            )
        )
        requireEquals(
            checkedId(
                requiredText(root, AqlWsContract.Field.ID, AqlWsContract.Limit.ID_CHARS)
            ),
            expected.requestId
        )
        if (!requiredBoolean(root, AqlWsContract.Field.OK)) {
            fail(AqlWsProtocolError.AUTHENTICATION_FAILED)
        }
        requireEquals(
            requiredInt(root, AqlWsContract.Field.STATUS),
            200,
            AqlWsProtocolError.AUTHENTICATION_FAILED
        )
        validateMeta(requiredObject(root, AqlWsContract.Field.META))

        val data = requiredObject(root, AqlWsContract.Field.DATA)
        requireExactKeys(
            data,
            setOf(
                AqlWsContract.Field.AUTH_SCHEME,
                AqlWsContract.Field.DEVICE_UID,
                AqlWsContract.Field.SESSION_ID,
                AqlWsContract.Field.SERVER_NONCE,
                AqlWsContract.Field.CLIENT_NONCE,
                AqlWsContract.Field.SERVER_PROOF
            )
        )
        requireEquals(
            requiredText(data, AqlWsContract.Field.AUTH_SCHEME, 32),
            AqlWsContract.AUTH_SCHEME
        )
        requireEquals(
            requiredText(
                data,
                AqlWsContract.Field.DEVICE_UID,
                AqlWsContract.Limit.DEVICE_UID_CHARS
            ),
            expected.hello.deviceUid
        )
        requireEquals(
            requiredText(
                data,
                AqlWsContract.Field.SESSION_ID,
                AqlWsContract.Limit.SESSION_ID_CHARS
            ),
            expected.hello.sessionId
        )
        requireEquals(
            requiredText(
                data,
                AqlWsContract.Field.SERVER_NONCE,
                AqlWsContract.Limit.NONCE_HEX_CHARS
            ),
            expected.hello.serverNonce
        )
        requireEquals(
            requiredText(
                data,
                AqlWsContract.Field.CLIENT_NONCE,
                AqlWsContract.Limit.NONCE_HEX_CHARS
            ),
            expected.clientNonce
        )
        val serverProof = requiredText(
            data,
            AqlWsContract.Field.SERVER_PROOF,
            AqlWsContract.Limit.MAC_HEX_CHARS
        ).also { validateHex(it, AqlWsContract.Limit.MAC_HEX_CHARS) }
        if (!AqlWsCrypto.serverProofMatches(expected, serverProof)) {
            fail(AqlWsProtocolError.AUTHENTICATION_FAILED)
        }
        return AqlWsDecodedFrame.Authenticated(AqlWsSecureSession.from(expected))
    }

    private fun decodeAuthenticationError(
        root: JSONObject,
        pending: AqlWsPendingAuthentication?
    ): AqlWsDecodedFrame.AuthRejected {
        val expected = pending ?: fail(AqlWsProtocolError.AUTHENTICATION_OUT_OF_SEQUENCE)
        requireExactKeys(
            root,
            setOf(
                AqlWsContract.Field.ID,
                AqlWsContract.Field.TYPE,
                AqlWsContract.Field.MODULE,
                AqlWsContract.Field.ACTION,
                AqlWsContract.Field.OK,
                AqlWsContract.Field.STATUS,
                AqlWsContract.Field.ERROR,
                AqlWsContract.Field.META
            )
        )
        requireEquals(
            checkedId(
                requiredText(root, AqlWsContract.Field.ID, AqlWsContract.Limit.ID_CHARS)
            ),
            expected.requestId
        )
        requireEquals(requiredBoolean(root, AqlWsContract.Field.OK), false)
        validateStatus(requiredInt(root, AqlWsContract.Field.STATUS))
        validateMeta(requiredObject(root, AqlWsContract.Field.META))
        decodeError(requiredObject(root, AqlWsContract.Field.ERROR))
        return AqlWsDecodedFrame.AuthRejected(
            AqlWsProtocolError.AUTHENTICATION_FAILED.safeMessage
        )
    }

    private fun decodeRuntimeResponse(
        root: JSONObject,
        secureSession: AqlWsSecureSession
    ): AqlWsDecodedFrame.Runtime {
        requireExactKeys(root, RUNTIME_RESPONSE_KEYS)
        validateMeta(requiredObject(root, AqlWsContract.Field.META))
        val id = checkedId(
            requiredText(root, AqlWsContract.Field.ID, AqlWsContract.Limit.ID_CHARS)
        )
        val module = checkedModule(
            requiredText(root, AqlWsContract.Field.MODULE, AqlWsContract.Limit.MODULE_CHARS)
        )
        val action = checkedAction(
            requiredText(root, AqlWsContract.Field.ACTION, AqlWsContract.Limit.ACTION_CHARS)
        )
        if (!AqlWsContract.isRegisteredCommand(module, action)) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
        val encodedData = requiredText(
            root,
            AqlWsContract.Field.DATA,
            MAX_ENCODED_DATA_CHARS
        )
        val ok = requiredBoolean(root, AqlWsContract.Field.OK)
        requireEquals(ok, true)
        val status = requiredInt(root, AqlWsContract.Field.STATUS).also(::validateStatus)
        val frame = AqlWsMacFrame(
            id,
            AqlWsContract.TYPE_RESPONSE,
            module,
            action,
            encodedData,
            status,
            ok
        )
        verifySecurity(root, secureSession, frame)
        return AqlWsDecodedFrame.Runtime(
            AqlWsIncomingMessage.Response(
                id = id,
                type = AqlWsContract.TYPE_RESPONSE,
                module = module,
                action = action,
                data = decodeData(encodedData),
                ok = ok,
                statusCode = status
            )
        )
    }

    private fun decodeRuntimeEvent(
        root: JSONObject,
        secureSession: AqlWsSecureSession
    ): AqlWsDecodedFrame.Runtime {
        requireExactKeys(root, RUNTIME_EVENT_KEYS)
        validateMeta(requiredObject(root, AqlWsContract.Field.META))
        val id = checkedId(
            requiredText(root, AqlWsContract.Field.ID, AqlWsContract.Limit.ID_CHARS)
        )
        val module = checkedModule(
            requiredText(root, AqlWsContract.Field.MODULE, AqlWsContract.Limit.MODULE_CHARS)
        )
        val action = checkedAction(
            requiredText(root, AqlWsContract.Field.ACTION, AqlWsContract.Limit.ACTION_CHARS)
        )
        if (!AqlWsContract.isActiveEvent(module, action)) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
        val encodedData = requiredText(
            root,
            AqlWsContract.Field.DATA,
            MAX_ENCODED_DATA_CHARS
        )
        val frame = AqlWsMacFrame(
            id,
            AqlWsContract.TYPE_EVENT,
            module,
            action,
            encodedData
        )
        verifySecurity(root, secureSession, frame)
        return AqlWsDecodedFrame.Runtime(
            AqlWsIncomingMessage.Event(
                id = id,
                type = AqlWsContract.TYPE_EVENT,
                module = module,
                action = action,
                data = decodeData(encodedData)
            )
        )
    }

    private fun decodeRuntimeError(
        root: JSONObject,
        secureSession: AqlWsSecureSession
    ): AqlWsDecodedFrame.Runtime {
        requireExactKeys(root, RUNTIME_ERROR_KEYS)
        validateMeta(requiredObject(root, AqlWsContract.Field.META))
        val id = checkedId(
            requiredText(root, AqlWsContract.Field.ID, AqlWsContract.Limit.ID_CHARS)
        )
        val module = checkedModule(
            requiredText(root, AqlWsContract.Field.MODULE, AqlWsContract.Limit.MODULE_CHARS)
        )
        val action = checkedAction(
            requiredText(root, AqlWsContract.Field.ACTION, AqlWsContract.Limit.ACTION_CHARS)
        )
        if (!AqlWsContract.isRegisteredCommand(module, action)) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
        val encodedData = requiredText(
            root,
            AqlWsContract.Field.DATA,
            MAX_ENCODED_DATA_CHARS
        )
        val status = requiredInt(root, AqlWsContract.Field.STATUS).also(::validateStatus)
        requireEquals(requiredBoolean(root, AqlWsContract.Field.OK), false)
        val error = decodeError(requiredObject(root, AqlWsContract.Field.ERROR))
        val frame = AqlWsMacFrame(
            id = id,
            type = AqlWsContract.TYPE_ERROR,
            module = module,
            action = action,
            dataBase64Url = encodedData,
            status = status,
            ok = false,
            errorCode = error.code,
            errorField = error.field,
            errorMessage = error.message
        )
        verifySecurity(root, secureSession, frame)
        return AqlWsDecodedFrame.Runtime(
            AqlWsIncomingMessage.Error(
                id = id,
                type = AqlWsContract.TYPE_ERROR,
                module = module,
                action = action,
                data = decodeData(encodedData),
                message = error.message,
                statusCode = status,
                code = error.code,
                field = error.field
            )
        )
    }

    private fun verifySecurity(
        root: JSONObject,
        secureSession: AqlWsSecureSession,
        frame: AqlWsMacFrame
    ) {
        val securityObject = requiredObject(root, AqlWsContract.Field.SECURITY)
        requireExactKeys(
            securityObject,
            setOf(
                AqlWsContract.Field.SESSION_ID,
                AqlWsContract.Field.SEQUENCE,
                AqlWsContract.Field.MAC
            )
        )
        val sequence = requiredLong(securityObject, AqlWsContract.Field.SEQUENCE)
        val security = AqlWsSecurityEnvelope(
            sessionId = requiredText(
                securityObject,
                AqlWsContract.Field.SESSION_ID,
                AqlWsContract.Limit.SESSION_ID_CHARS
            ),
            sequence = sequence,
            mac = requiredText(
                securityObject,
                AqlWsContract.Field.MAC,
                AqlWsContract.Limit.MAC_HEX_CHARS
            ).also { validateHex(it, AqlWsContract.Limit.MAC_HEX_CHARS) }
        )
        if (!secureSession.verifyDevice(security, frame)) {
            fail(AqlWsProtocolError.REPLAY_OR_INVALID_MAC)
        }
    }

    private fun validateMeta(meta: JSONObject): String {
        requireExactKeys(
            meta,
            setOf(
                AqlWsContract.Field.SCHEMA,
                AqlWsContract.Field.SCHEMA_VERSION,
                AqlWsContract.Field.PROTOCOL_VERSION,
                AqlWsContract.Field.FIRMWARE_VERSION
            )
        )
        requireEquals(
            requiredText(meta, AqlWsContract.Field.SCHEMA, 32),
            AqlWsContract.SCHEMA,
            AqlWsProtocolError.INCOMPATIBLE_PROTOCOL
        )
        requireEquals(
            requiredInt(meta, AqlWsContract.Field.SCHEMA_VERSION),
            AqlWsContract.SCHEMA_VERSION,
            AqlWsProtocolError.INCOMPATIBLE_PROTOCOL
        )
        requireEquals(
            requiredInt(meta, AqlWsContract.Field.PROTOCOL_VERSION),
            AqlWsContract.PROTOCOL_VERSION,
            AqlWsProtocolError.INCOMPATIBLE_PROTOCOL
        )
        return requiredText(
            meta,
            AqlWsContract.Field.FIRMWARE_VERSION,
            AqlWsContract.Limit.FIRMWARE_VERSION_CHARS
        )
    }

    private fun parseRoot(raw: String): JSONObject {
        if (raw.toByteArray(StandardCharsets.UTF_8).size > AqlWsContract.Limit.MESSAGE_BYTES) {
            fail(AqlWsProtocolError.MESSAGE_TOO_LARGE)
        }
        return try {
            validateJsonStructure(raw)
            JSONObject(raw)
        } catch (error: AqlWsProtocolException) {
            throw error
        } catch (_: JSONException) {
            fail(AqlWsProtocolError.MALFORMED_JSON)
        } catch (_: Throwable) {
            fail(AqlWsProtocolError.MALFORMED_JSON)
        }
    }

    private fun validateJsonStructure(raw: String) {
        try {
            JsonReader(StringReader(raw)).use { reader ->
                reader.isLenient = false
                readStrictValue(
                    reader = reader,
                    depth = 1,
                    counter = JsonStructureCounter()
                )
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    fail(AqlWsProtocolError.MALFORMED_JSON)
                }
            }
        } catch (error: AqlWsProtocolException) {
            throw error
        } catch (_: Throwable) {
            fail(AqlWsProtocolError.MALFORMED_JSON)
        }
    }

    private fun readStrictValue(
        reader: JsonReader,
        depth: Int,
        counter: JsonStructureCounter
    ) {
        if (depth > AqlWsContract.Limit.JSON_DEPTH) {
            fail(AqlWsProtocolError.JSON_LIMIT_EXCEEDED)
        }
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val names = mutableSetOf<String>()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (!names.add(name)) {
                        fail(AqlWsProtocolError.DUPLICATE_FIELD)
                    }
                    if (name.toByteArray(StandardCharsets.UTF_8).size >
                        AqlWsContract.Limit.JSON_KEY_BYTES
                    ) {
                        fail(AqlWsProtocolError.JSON_LIMIT_EXCEEDED)
                    }
                    counter.keyCount += 1
                    if (counter.keyCount > AqlWsContract.Limit.JSON_KEYS) {
                        fail(AqlWsProtocolError.JSON_LIMIT_EXCEEDED)
                    }
                    readStrictValue(reader, depth + 1, counter)
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    readStrictValue(reader, depth + 1, counter)
                }
                reader.endArray()
            }
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> reader.nextNull()
            else -> fail(AqlWsProtocolError.MALFORMED_JSON)
        }
    }

    private fun encodeData(data: JSONObject): String {
        val raw = data.toString()
        validateJsonStructure(raw)
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > AqlWsContract.Limit.DATA_BYTES) {
            fail(AqlWsProtocolError.INVALID_DATA)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun decodeData(encoded: String): JSONObject {
        if (encoded.length > MAX_ENCODED_DATA_CHARS || !BASE64_URL_REGEX.matches(encoded)) {
            fail(AqlWsProtocolError.INVALID_DATA)
        }
        val decoded = try {
            Base64.getUrlDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            fail(AqlWsProtocolError.INVALID_DATA)
        }
        if (decoded.size > AqlWsContract.Limit.DATA_BYTES) {
            fail(AqlWsProtocolError.INVALID_DATA)
        }
        val raw = decodeStrictUtf8(decoded)
        return try {
            validateJsonStructure(raw)
            JSONObject(raw)
        } catch (error: AqlWsProtocolException) {
            throw error
        } catch (_: Throwable) {
            fail(AqlWsProtocolError.INVALID_DATA)
        }
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Throwable) {
        fail(AqlWsProtocolError.INVALID_DATA)
    }

    private fun checkedWireJson(json: JSONObject): String {
        val raw = json.toString()
        validateJsonStructure(raw)
        if (raw.toByteArray(StandardCharsets.UTF_8).size > AqlWsContract.Limit.MESSAGE_BYTES) {
            fail(AqlWsProtocolError.MESSAGE_TOO_LARGE)
        }
        return raw
    }

    private fun JSONObject.isAuthenticationFrame(): Boolean =
        opt(AqlWsContract.Field.MODULE) == AqlWsContract.MODULE_SECURITY &&
            opt(AqlWsContract.Field.ACTION) == AqlWsContract.ACTION_SESSION_AUTHENTICATE

    private fun AqlWsSecurityEnvelope.toJson(): JSONObject = JSONObject()
        .put(AqlWsContract.Field.SESSION_ID, sessionId)
        .put(AqlWsContract.Field.SEQUENCE, sequence)
        .put(AqlWsContract.Field.MAC, mac)

    private fun requireExactKeys(json: JSONObject, expected: Set<String>) {
        val actual = json.keys().asSequence().toSet()
        if (actual != expected) {
            fail(
                if (actual.containsAll(expected)) AqlWsProtocolError.UNEXPECTED_FIELD
                else AqlWsProtocolError.MISSING_FIELD
            )
        }
    }

    private fun requiredObject(json: JSONObject, name: String): JSONObject =
        (json.opt(name) as? JSONObject) ?: fail(AqlWsProtocolError.MISSING_FIELD)

    private fun requiredText(json: JSONObject, name: String, maxChars: Int): String {
        val value = json.opt(name) as? String ?: fail(AqlWsProtocolError.MISSING_FIELD)
        if (
            value.isBlank() ||
            value != value.trim() ||
            value.length > maxChars ||
            value.hasControlCharacter()
        ) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
        return value
    }

    private fun requiredBoolean(json: JSONObject, name: String): Boolean =
        (json.opt(name) as? Boolean) ?: fail(AqlWsProtocolError.MISSING_FIELD)

    private fun requiredInt(json: JSONObject, name: String): Int {
        val number = json.opt(name) as? Number ?: fail(AqlWsProtocolError.MISSING_FIELD)
        val longValue = number.toLong()
        if (
            number.toDouble() != longValue.toDouble() ||
            longValue !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        ) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
        return longValue.toInt()
    }

    private fun requiredLong(json: JSONObject, name: String): Long {
        val number = json.opt(name) as? Number ?: fail(AqlWsProtocolError.MISSING_FIELD)
        val longValue = number.toLong()
        if (number.toDouble() != longValue.toDouble()) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
        return longValue
    }

    private fun checkedId(value: String): String = value.also {
        validateIdentifier(it, AqlWsContract.Limit.ID_CHARS)
    }

    private fun checkedModule(value: String): String = value.also {
        if (!MODULE_REGEX.matches(it)) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
    }

    private fun checkedAction(value: String): String = value.also {
        if (!ACTION_REGEX.matches(it)) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
    }

    private fun validateIdentifier(value: String) {
        validateIdentifier(value, AqlWsContract.Limit.DEVICE_UID_CHARS)
    }

    private fun validateIdentifier(value: String, maxChars: Int) {
        if (value.length > maxChars || !IDENTIFIER_REGEX.matches(value)) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
    }

    private fun validateHex(value: String, expectedChars: Int) {
        if (value.length != expectedChars || !HEX_REGEX.matches(value)) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
    }

    private fun validateStatus(status: Int) {
        if (status !in 100..599) {
            fail(AqlWsProtocolError.INVALID_FIELD)
        }
    }

    private fun decodeError(error: JSONObject): WireError {
        val keys = error.keys().asSequence().toSet()
        if (keys !in ERROR_KEY_SETS) {
            fail(
                if (keys.containsAll(REQUIRED_ERROR_KEYS)) {
                    AqlWsProtocolError.UNEXPECTED_FIELD
                } else {
                    AqlWsProtocolError.MISSING_FIELD
                }
            )
        }
        return WireError(
            code = requiredText(
                error,
                AqlWsContract.Field.CODE,
                AqlWsContract.Limit.ERROR_CODE_CHARS
            ),
            field = (error.opt(AqlWsContract.Field.ERROR_FIELD) as? String)
                .orEmpty()
                .also { value ->
                    if (
                        value.length > AqlWsContract.Limit.ERROR_FIELD_CHARS ||
                        value.hasControlCharacter()
                    ) {
                        fail(AqlWsProtocolError.INVALID_FIELD)
                    }
                },
            message = requiredText(
                error,
                AqlWsContract.Field.MESSAGE,
                AqlWsContract.Limit.ERROR_MESSAGE_CHARS
            )
        )
    }

    private fun String.hasControlCharacter(): Boolean = any { char ->
        char.code < 0x20 || char.code == 0x7f
    }

    private fun <T> requireEquals(
        actual: T,
        expected: T,
        error: AqlWsProtocolError = AqlWsProtocolError.INVALID_FIELD
    ) {
        if (actual != expected) {
            fail(error)
        }
    }

    private fun requireSecureSession(session: AqlWsSecureSession?): AqlWsSecureSession =
        session ?: fail(AqlWsProtocolError.SECURITY_SESSION_REQUIRED)

    private data class JsonStructureCounter(
        var keyCount: Int = 0
    )

    private data class WireError(
        val code: String,
        val field: String,
        val message: String
    )

    private companion object {
        val IDENTIFIER_REGEX = Regex("^[A-Za-z0-9._:-]+$")
        val MODULE_REGEX = Regex("^[a-z][a-z0-9_-]{0,31}$")
        val ACTION_REGEX = Regex("^[a-z][a-z0-9_.-]{0,63}$")
        val HEX_REGEX = Regex("^[0-9a-fA-F]+$")
        val BASE64_URL_REGEX = Regex("^[A-Za-z0-9_-]+$")
        const val MAX_ENCODED_DATA_CHARS = 5_464

        val RUNTIME_RESPONSE_KEYS = setOf(
            AqlWsContract.Field.ID,
            AqlWsContract.Field.TYPE,
            AqlWsContract.Field.MODULE,
            AqlWsContract.Field.ACTION,
            AqlWsContract.Field.DATA,
            AqlWsContract.Field.OK,
            AqlWsContract.Field.STATUS,
            AqlWsContract.Field.SECURITY,
            AqlWsContract.Field.META
        )
        val RUNTIME_EVENT_KEYS = setOf(
            AqlWsContract.Field.ID,
            AqlWsContract.Field.TYPE,
            AqlWsContract.Field.MODULE,
            AqlWsContract.Field.ACTION,
            AqlWsContract.Field.DATA,
            AqlWsContract.Field.SECURITY,
            AqlWsContract.Field.META
        )
        val RUNTIME_ERROR_KEYS = setOf(
            AqlWsContract.Field.ID,
            AqlWsContract.Field.TYPE,
            AqlWsContract.Field.MODULE,
            AqlWsContract.Field.ACTION,
            AqlWsContract.Field.DATA,
            AqlWsContract.Field.ERROR,
            AqlWsContract.Field.OK,
            AqlWsContract.Field.STATUS,
            AqlWsContract.Field.SECURITY,
            AqlWsContract.Field.META
        )
        val REQUIRED_ERROR_KEYS = setOf(
            AqlWsContract.Field.CODE,
            AqlWsContract.Field.MESSAGE
        )
        val ERROR_KEY_SETS = setOf(
            REQUIRED_ERROR_KEYS,
            REQUIRED_ERROR_KEYS + AqlWsContract.Field.ERROR_FIELD
        )
    }
}

private fun fail(error: AqlWsProtocolError): Nothing = throw AqlWsProtocolException(error)

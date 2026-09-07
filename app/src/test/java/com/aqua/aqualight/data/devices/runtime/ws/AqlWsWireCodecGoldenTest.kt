package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AqlWsWireCodecGoldenTest {

    private val fixture: JSONObject by lazy {
        val stream = checkNotNull(
            javaClass.getResourceAsStream("/aql_ws_v1_golden.json")
        ) { "Missing shared AquaLight WebSocket golden fixture." }
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            JSONObject(reader.readText())
        }
    }

    private val codec = AqlWsWireCodec()

    @Test
    fun `shared golden handshake and signed runtime frames are interoperable`() {
        val inputs = fixture.getJSONObject("testInputs")
        val handshake = fixture.getJSONObject("handshake")
        val runtime = fixture.getJSONObject("runtime")
        val expectedDeviceUid = inputs.getString("deviceUid")

        val helloFrame = codec.decode(
            raw = handshake.getJSONObject("hello").toString(),
            expectedDeviceUid = expectedDeviceUid,
            pendingAuthentication = null,
            secureSession = null
        ) as AqlWsDecodedFrame.Hello

        val pending = codec.prepareAuthentication(
            hello = helloFrame.challenge,
            runtimeToken = inputs.getString("runtimeToken"),
            requestId = inputs.getString("authRequestId"),
            clientNonce = inputs.getString("clientNonce")
        )
        assertEquals(handshake.getString("expectedClientProof"), pending.clientProof)
        assertEquals(handshake.getString("expectedServerProof"), pending.expectedServerProof)

        val authenticationWire = codec.encodeAuthenticationRequest(pending)
        assertJsonEquals(
            handshake.getJSONObject("authRequest"),
            JSONObject(authenticationWire)
        )
        assertFalse(authenticationWire.contains(inputs.getString("runtimeToken")))

        val authenticated = codec.decode(
            raw = handshake.getJSONObject("authResponse").toString(),
            expectedDeviceUid = expectedDeviceUid,
            pendingAuthentication = pending,
            secureSession = null
        ) as AqlWsDecodedFrame.Authenticated
        pending.close()

        authenticated.secureSession.use { session ->
            val expectedCommand = runtime.getJSONObject("clientCommand")
            val commandWire = codec.encode(
                message = AqlWsOutgoingMessage.Command(
                    id = expectedCommand.getString("id"),
                    module = expectedCommand.getString("module"),
                    action = expectedCommand.getString("action"),
                    data = JSONObject()
                ),
                secureSession = session
            )
            assertJsonEquals(expectedCommand, JSONObject(commandWire))
            assertFalse(commandWire.contains(inputs.getString("runtimeToken")))

            val response = codec.decode(
                raw = runtime.getJSONObject("deviceResponse").toString(),
                expectedDeviceUid = expectedDeviceUid,
                pendingAuthentication = null,
                secureSession = session
            ) as AqlWsDecodedFrame.Runtime
            val typedResponse = response.message as AqlWsIncomingMessage.Response
            assertTrue(typedResponse.ok)
            assertEquals("192.168.1.42", typedResponse.data.getString("ip"))

            val event = codec.decode(
                raw = runtime.getJSONObject("deviceEvent").toString(),
                expectedDeviceUid = expectedDeviceUid,
                pendingAuthentication = null,
                secureSession = session
            ) as AqlWsDecodedFrame.Runtime
            val typedEvent = event.message as AqlWsIncomingMessage.Event
            assertEquals(42, typedEvent.data.getInt("progress"))

            val error = codec.decode(
                raw = runtime.getJSONObject("deviceError").toString(),
                expectedDeviceUid = expectedDeviceUid,
                pendingAuthentication = null,
                secureSession = session
            ) as AqlWsDecodedFrame.Runtime
            val typedError = error.message as AqlWsIncomingMessage.Error
            assertEquals("invalid_field", typedError.code)
            assertEquals("level", typedError.field)
            assertEquals("Command rejected.", typedError.message)

            assertProtocolError(AqlWsProtocolError.REPLAY_OR_INVALID_MAC) {
                codec.decode(
                    raw = runtime.getJSONObject("deviceResponse").toString(),
                    expectedDeviceUid = expectedDeviceUid,
                    pendingAuthentication = null,
                    secureSession = session
                )
            }
        }
    }

    @Test
    fun `shared command access matrix matches the Android contract`() {
        val access = fixture.getJSONObject("commandAccess")
        val expectedPublic = access.getJSONArray("public").asStringSet()
        val expectedAuthenticated = access.getJSONArray("authenticated").asStringSet()

        assertEquals(expectedPublic, AqlWsContract.publicCommandKeys())
        assertEquals(expectedAuthenticated, AqlWsContract.authenticatedCommandKeys())
        assertTrue(expectedPublic.isEmpty())
        assertEquals(50, expectedAuthenticated.size)
    }

    @Test
    fun `fake device identity and forged server proof are rejected`() {
        val inputs = fixture.getJSONObject("testInputs")
        val handshake = fixture.getJSONObject("handshake")

        assertProtocolError(AqlWsProtocolError.DEVICE_IDENTITY_MISMATCH) {
            codec.decode(
                raw = fixture.getJSONObject("invalid")
                    .getJSONObject("fakeDeviceHello")
                    .toString(),
                expectedDeviceUid = inputs.getString("deviceUid"),
                pendingAuthentication = null,
                secureSession = null
            )
        }

        val pending = pendingAuthentication()
        try {
            val forged = JSONObject(handshake.getJSONObject("authResponse").toString())
            forged.getJSONObject("data").put("serverProof", "00".repeat(32))
            assertProtocolError(AqlWsProtocolError.AUTHENTICATION_FAILED) {
                codec.decode(
                    raw = forged.toString(),
                    expectedDeviceUid = inputs.getString("deviceUid"),
                    pendingAuthentication = pending,
                    secureSession = null
                )
            }
        } finally {
            pending.close()
        }
    }

    @Test
    fun `unknown duplicate incomplete and oversize frames fail closed`() {
        val inputs = fixture.getJSONObject("testInputs")
        val hello = fixture.getJSONObject("handshake").getJSONObject("hello")

        assertProtocolError(AqlWsProtocolError.UNSUPPORTED_TYPE) {
            codec.decode(
                raw = "{\"id\":\"x\",\"type\":\"unknown\"}",
                expectedDeviceUid = inputs.getString("deviceUid"),
                pendingAuthentication = null,
                secureSession = null
            )
        }
        assertProtocolError(AqlWsProtocolError.DUPLICATE_FIELD) {
            codec.decode(
                raw = "{\"id\":\"first\",\"id\":\"second\",\"type\":\"hello\"}",
                expectedDeviceUid = inputs.getString("deviceUid"),
                pendingAuthentication = null,
                secureSession = null
            )
        }
        assertProtocolError(AqlWsProtocolError.UNEXPECTED_FIELD) {
            codec.decode(
                raw = JSONObject(hello.toString()).put("unexpected", true).toString(),
                expectedDeviceUid = inputs.getString("deviceUid"),
                pendingAuthentication = null,
                secureSession = null
            )
        }
        assertProtocolError(AqlWsProtocolError.MISSING_FIELD) {
            val incomplete = JSONObject(hello.toString())
            incomplete.remove(AqlWsContract.Field.META)
            codec.decode(
                raw = incomplete.toString(),
                expectedDeviceUid = inputs.getString("deviceUid"),
                pendingAuthentication = null,
                secureSession = null
            )
        }
        assertProtocolError(AqlWsProtocolError.MESSAGE_TOO_LARGE) {
            codec.decode(
                raw = "x".repeat(AqlWsContract.Limit.MESSAGE_BYTES + 1),
                expectedDeviceUid = inputs.getString("deviceUid"),
                pendingAuthentication = null,
                secureSession = null
            )
        }
    }

    @Test
    fun `invalid mac and sequence do not advance replay window`() {
        val inputs = fixture.getJSONObject("testInputs")
        val expectedDeviceUid = inputs.getString("deviceUid")
        val response = fixture.getJSONObject("runtime").getJSONObject("deviceResponse")
        val session = authenticatedSession()
        session.use {
            val wrongSequence = JSONObject(response.toString())
            wrongSequence.getJSONObject("security").put("seq", 2)
            assertProtocolError(AqlWsProtocolError.REPLAY_OR_INVALID_MAC) {
                codec.decode(wrongSequence.toString(), expectedDeviceUid, null, session)
            }

            val forged = JSONObject(response.toString())
            forged.getJSONObject("security").put("mac", "ff".repeat(32))
            assertProtocolError(AqlWsProtocolError.REPLAY_OR_INVALID_MAC) {
                codec.decode(forged.toString(), expectedDeviceUid, null, session)
            }

            val accepted = codec.decode(
                response.toString(),
                expectedDeviceUid,
                null,
                session
            )
            assertTrue(accepted is AqlWsDecodedFrame.Runtime)
        }
    }

    @Test
    fun `unsigned runtime frames and over-limit fields are rejected`() {
        val inputs = fixture.getJSONObject("testInputs")
        val expectedDeviceUid = inputs.getString("deviceUid")
        val response = JSONObject(
            fixture.getJSONObject("runtime").getJSONObject("deviceResponse").toString()
        ).also { it.remove("security") }
        val session = authenticatedSession()
        session.use {
            assertProtocolError(AqlWsProtocolError.MISSING_FIELD) {
                codec.decode(response.toString(), expectedDeviceUid, null, session)
            }
            assertProtocolError(AqlWsProtocolError.INVALID_DATA) {
                codec.encode(
                    AqlWsOutgoingMessage.Command(
                        module = AqlWsContract.MODULE_NETWORK,
                        action = AqlWsContract.ACTION_NETWORK_STATUS_GET,
                        data = JSONObject().put("blob", "x".repeat(AqlWsContract.Limit.DATA_BYTES + 1))
                    ),
                    session
                )
            }
            assertProtocolError(AqlWsProtocolError.INVALID_FIELD) {
                codec.encode(
                    AqlWsOutgoingMessage.Command(
                        id = "x".repeat(AqlWsContract.Limit.ID_CHARS + 1),
                        module = AqlWsContract.MODULE_NETWORK,
                        action = AqlWsContract.ACTION_NETWORK_STATUS_GET
                    ),
                    session
                )
            }
        }
    }

    private fun pendingAuthentication(): AqlWsPendingAuthentication {
        val inputs = fixture.getJSONObject("testInputs")
        val hello = codec.decode(
            raw = fixture.getJSONObject("handshake").getJSONObject("hello").toString(),
            expectedDeviceUid = inputs.getString("deviceUid"),
            pendingAuthentication = null,
            secureSession = null
        ) as AqlWsDecodedFrame.Hello
        return codec.prepareAuthentication(
            hello = hello.challenge,
            runtimeToken = inputs.getString("runtimeToken"),
            requestId = inputs.getString("authRequestId"),
            clientNonce = inputs.getString("clientNonce")
        )
    }

    private fun authenticatedSession(): AqlWsSecureSession {
        val inputs = fixture.getJSONObject("testInputs")
        val pending = pendingAuthentication()
        return try {
            val decoded = codec.decode(
                raw = fixture.getJSONObject("handshake").getJSONObject("authResponse").toString(),
                expectedDeviceUid = inputs.getString("deviceUid"),
                pendingAuthentication = pending,
                secureSession = null
            ) as AqlWsDecodedFrame.Authenticated
            decoded.secureSession
        } finally {
            pending.close()
        }
    }

    private fun assertProtocolError(
        expected: AqlWsProtocolError,
        block: () -> Unit
    ) {
        try {
            block()
            fail("Expected protocol error $expected")
        } catch (error: AqlWsProtocolException) {
            assertEquals(expected, error.protocolError)
        }
    }

    private fun assertJsonEquals(expected: Any?, actual: Any?) {
        when {
            expected is JSONObject && actual is JSONObject -> {
                val expectedKeys = expected.keys().asSequence().toSet()
                val actualKeys = actual.keys().asSequence().toSet()
                assertEquals(expectedKeys, actualKeys)
                expectedKeys.forEach { key ->
                    assertJsonEquals(expected.get(key), actual.get(key))
                }
            }
            expected is JSONArray && actual is JSONArray -> {
                assertEquals(expected.length(), actual.length())
                repeat(expected.length()) { index ->
                    assertJsonEquals(expected.get(index), actual.get(index))
                }
            }
            expected is Number && actual is Number ->
                assertEquals(0, BigDecimal(expected.toString()).compareTo(BigDecimal(actual.toString())))
            else -> assertEquals(expected, actual)
        }
    }

    private fun JSONArray.asStringSet(): Set<String> = buildSet {
        repeat(length()) { index -> add(getString(index)) }
    }
}

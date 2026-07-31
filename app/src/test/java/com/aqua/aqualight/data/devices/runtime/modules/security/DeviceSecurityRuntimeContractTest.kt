package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSecurityRuntimeContractTest {

    @Test
    fun `paired status requires the complete conditional token metadata group`() {
        val parsed = DeviceSecurityStatusGetCommand(DEVICE_UID).parseSuccess(
            response(
                action = AqlWsContract.ACTION_SECURITY_STATUS_GET,
                data = statusResponseJson(paired = true)
            )
        )

        assertTrue(parsed.status.paired)
        assertEquals(3L, parsed.status.tokenMetadata?.tokenVersion)
        assertEquals(64, parsed.status.tokenHexLength)

        val missingTokenVersion = statusResponseJson(paired = true).apply {
            remove("tokenVersion")
        }
        assertFails {
            DeviceSecurityStatusGetCommand(DEVICE_UID).parseSuccess(
                response(AqlWsContract.ACTION_SECURITY_STATUS_GET, missingTokenVersion)
            )
        }
    }

    @Test
    fun `unpaired status rejects stale token metadata and type coercion`() {
        val parsed = DeviceSecurityStatusGetCommand(DEVICE_UID).parseSuccess(
            response(
                action = AqlWsContract.ACTION_SECURITY_STATUS_GET,
                data = statusResponseJson(paired = false)
            )
        )
        assertFalse(parsed.status.paired)
        assertNull(parsed.status.tokenMetadata)

        val staleMetadata = statusResponseJson(paired = false)
            .put("tokenVersion", 1)
            .put("pairedAtMs", 1)
            .put("lastRotatedAtMs", 1)
        val coercedBoolean = statusResponseJson(paired = false)
            .put("runtimeCredentialSerialized", "false")
        assertFails {
            DeviceSecurityStatusGetCommand(DEVICE_UID).parseSuccess(
                response(AqlWsContract.ACTION_SECURITY_STATUS_GET, staleMetadata)
            )
        }
        assertFails {
            DeviceSecurityStatusGetCommand(DEVICE_UID).parseSuccess(
                response(AqlWsContract.ACTION_SECURITY_STATUS_GET, coercedBoolean)
            )
        }
    }

    @Test
    fun `pair is an empty ownership-status request and never returns a token`() {
        val command = DeviceSecurityPairCommand()
        assertEquals(0, command.encodeData().length())

        val parsed = command.parseSuccess(
            response(
                action = AqlWsContract.ACTION_SECURITY_PAIR,
                data = JSONObject()
                    .put("operation", "pair")
                    .put("paired", true)
                    .put("tokenReturned", false)
                    .put("credentialRotationTransport", "ble_runtime_endpoint")
                    .put("credentialSerializedOnWebSocket", false)
                    .put("runtimeTransport", "websocket")
                    .put("authMessageType", "auth")
                    .put("authScheme", "hmac-sha256")
                    .put("credentialSerialized", false)
                    .put("command", "security.pair")
                    .put("authenticatedSessionUsed", true)
            )
        )

        assertTrue(parsed.paired)
        assertFalse(parsed.tokenReturned)
        assertFalse(parsed.credentialSerializedOnWebSocket)
    }

    @Test
    fun `unpair invalidates local credential only after exact firmware success`() = runBlocking {
        var invalidationCount = 0
        val gateway = RespondingGateway { command ->
            response(
                action = command.action,
                data = ownershipResetJson(command.action)
            )
        }
        val repository = DeviceSecurityRuntimeRepository(gateway) { uid, generation ->
            assertEquals(DEVICE_UID, uid)
            assertEquals(GENERATION, generation)
            invalidationCount += 1
        }

        val outcome = repository.unpair(DEVICE_UID)
        assertTrue(outcome is DeviceRuntimeCommandOutcome.Success)
        assertEquals(1, invalidationCount)
        assertEquals(0, gateway.lastEncodedData?.length())
    }

    @Test
    fun `local credential failure is not reported as command success`() = runBlocking {
        val gateway = RespondingGateway { command ->
            response(command.action, ownershipResetJson(command.action))
        }
        val repository = DeviceSecurityRuntimeRepository(gateway) { _, _ ->
            error("credential store unavailable")
        }

        val outcome = repository.reset(DEVICE_UID)
        assertTrue(outcome is DeviceRuntimeCommandOutcome.LocalStateError)
        assertEquals("credential store unavailable", (outcome as DeviceRuntimeCommandOutcome.LocalStateError).reason)
    }

    private class RespondingGateway(
        private val responseProvider: (DeviceRuntimeCommand<*>) -> AqlWsIncomingMessage.Response
    ) : DeviceRuntimeCommandGateway {
        var lastEncodedData: JSONObject? = null
            private set

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            lastEncodedData = command.encodeData()
            val response = responseProvider(command)
            return try {
                DeviceRuntimeCommandOutcome.Success(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action,
                    messageId = response.id,
                    generation = GENERATION,
                    statusCode = response.statusCode,
                    value = command.parseSuccess(response)
                )
            } catch (error: Throwable) {
                DeviceRuntimeCommandOutcome.ProtocolError(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action,
                    messageId = response.id,
                    generation = GENERATION,
                    reason = error.message.orEmpty()
                )
            }
        }
    }

    private fun response(
        action: String,
        data: JSONObject
    ): AqlWsIncomingMessage.Response = AqlWsIncomingMessage.Response(
        id = "security-test-command",
        type = AqlWsContract.TYPE_RESPONSE,
        module = AqlWsContract.MODULE_SECURITY,
        action = action,
        data = data,
        ok = true,
        statusCode = 200
    )

    private fun statusResponseJson(paired: Boolean): JSONObject =
        baseStatusJson(paired)
            .put("authMessageType", "auth")
            .put("authScheme", "hmac-sha256")
            .put("credentialSerialized", false)

    private fun ownershipResetJson(action: String): JSONObject = JSONObject()
        .put("operation", action)
        .put("paired", false)
        .put("credentialReturned", false)
        .put("runtimeTransport", "websocket")
        .put("command", "security.$action")
        .put(
            "message",
            "runtime credential cleared; encrypted BLE ownership is required again"
        )
        .put("status", baseStatusJson(paired = false))

    private fun baseStatusJson(paired: Boolean): JSONObject = JSONObject()
        .put("tokenGateEnabled", true)
        .put("dynamicPairingEnabled", true)
        .put("paired", paired)
        .put("runtimeTransport", "websocket")
        .put("runtimeAuthMessageType", "auth")
        .put("runtimeAuthScheme", "hmac-sha256")
        .put("runtimeCredentialSerialized", false)
        .put("runtimeReplayProtection", "session_nonce_and_monotonic_sequence")
        .put("initialOwnershipTransport", "ble_qr")
        .put("firstTokenTransport", "ble_runtime_endpoint")
        .put("webSocketPairingCommand", "security.pair")
        .put("webSocketPairingCommandAuth", "authenticated")
        .put("webSocketPairingPurpose", "ownership_status_only")
        .put("publicFirstPairingSupported", false)
        .put("mutatingCommandsRequireAuth", true)
        .put("tokenReturnedByStatus", false)
        .put("tokenStorageBackend", "NVS")
        .put("tokenStorageFormat", "sha256_hash")
        .put("tokenStoredPlaintext", false)
        .put("tokenFormat", "64_hex")
        .put("tokenHexLength", 64)
        .put("deviceUid", DEVICE_UID.value)
        .put("shortId", "SEC001")
        .put("serialNumber", "AQL-SEC-000001")
        .put("provisioningTokenPending", false)
        .apply {
            if (paired) {
                put("tokenVersion", 3)
                put("pairedAtMs", 10_000)
                put("lastRotatedAtMs", 20_000)
            }
        }

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-SECURITY-000001")
        val GENERATION = DeviceRuntimeConnectionGeneration(7L)
    }
}

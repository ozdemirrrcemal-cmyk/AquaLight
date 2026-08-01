package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSecurityRuntimeContractTest {
    @Test
    fun `status parser accepts exact firmware status without exposing credential`() {
        val parsed = DeviceSecurityParser.parseStatus(pairedStatus(), DEVICE_UID)

        assertTrue(parsed.paired)
        assertEquals(7, parsed.storage.tokenVersion)
        assertTrue(!parsed.runtime.credentialSerialized)
        assertTrue(!parsed.ownership.tokenReturnedByStatus)
    }

    @Test
    fun `revocation runs local teardown only after exact firmware success`() = runBlocking {
        val teardownCalls = AtomicInteger(0)
        val repository = DeviceSecurityRuntimeRepository(
            gateway = SecurityGateway(succeed = true),
            revokeLocalCredential = {
                teardownCalls.incrementAndGet()
                Result.success(Unit)
            }
        )

        val result = repository.unpair(DEVICE_UID)

        assertTrue(result is DeviceSecurityRevocationOutcome.Completed)
        assertEquals(1, teardownCalls.get())
    }

    @Test
    fun `firmware error never clears local credential`() = runBlocking {
        val teardownCalls = AtomicInteger(0)
        val repository = DeviceSecurityRuntimeRepository(
            gateway = SecurityGateway(succeed = false),
            revokeLocalCredential = {
                teardownCalls.incrementAndGet()
                Result.success(Unit)
            }
        )

        val result = repository.reset(DEVICE_UID)

        assertTrue(result is DeviceSecurityRevocationOutcome.CommandFailed)
        assertEquals(0, teardownCalls.get())
    }

    @Test
    fun `revocation parser rejects response aliases inside embedded status`() {
        val invalid = revocation("unpair").apply {
            getJSONObject("status").put("authMessageType", "auth")
        }

        val failure = runCatching {
            DeviceSecurityParser.parseRevocation(invalid, DEVICE_UID, "unpair")
        }

        assertTrue(failure.isFailure)
    }

    private inner class SecurityGateway(
        private val succeed: Boolean
    ) : DeviceRuntimeCommandGateway {
        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            if (!succeed) {
                return DeviceRuntimeCommandOutcome.FirmwareError(
                    deviceUid = deviceUid,
                    module = command.module,
                    action = command.action,
                    messageId = "error-id",
                    generation = DeviceRuntimeConnectionGeneration(1L),
                    statusCode = 500,
                    code = "storage_error",
                    field = "security",
                    message = "reset failed"
                )
            }
            val response = AqlWsIncomingMessage.Response(
                id = "response-id",
                type = "res",
                module = command.module,
                action = command.action,
                data = revocation(command.action),
                ok = true,
                statusCode = 200
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = response.id,
                generation = DeviceRuntimeConnectionGeneration(1L),
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }
    }

    private fun pairedStatus(): JSONObject = baseStatus(paired = true)
        .put("tokenVersion", 7)
        .put("pairedAtMs", 10L)
        .put("lastRotatedAtMs", 20L)

    private fun revocation(action: String): JSONObject = JSONObject()
        .put("operation", action)
        .put("paired", false)
        .put("credentialReturned", false)
        .put("runtimeTransport", "websocket")
        .put("command", "security.$action")
        .put("message", "runtime credential cleared")
        .put("status", baseStatus(paired = false))

    private fun baseStatus(paired: Boolean): JSONObject = JSONObject()
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
        .put("shortId", "AQL-SEC")
        .put("serialNumber", "AQL-SECURITY-0001")
        .put("provisioningTokenPending", false)

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-SECURITY-DEVICE")
    }
}

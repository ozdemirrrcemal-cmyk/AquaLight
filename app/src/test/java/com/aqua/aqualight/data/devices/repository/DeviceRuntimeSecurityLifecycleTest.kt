package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeSecurityLifecycleTest {

    @Test
    fun `exact unpair success clears credential detaches session and publishes auth required`() =
        runBlocking {
            val tokenProvider = RecordingTokenProvider()
            val transport = RecordingTransport()
            val repository = DeviceRuntimeRepository(
                tokenProvider = tokenProvider,
                wsClientFactory = { transport },
                dispatcher = Dispatchers.Unconfined
            )
            val states = CopyOnWriteArrayList<AqlWsConnectionState>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.connectionState.collect(states::add)
            }

            repository.connect(snapshot()).getOrThrow()
            transport.authenticate()
            val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
                repository.runtimeModules.security.unpair(DEVICE_UID)
            }
            val command = transport.commands.last { item ->
                item.module == AqlWsContract.MODULE_SECURITY &&
                    item.action == AqlWsContract.ACTION_SECURITY_UNPAIR
            }
            transport.emit(
                AqlWsEvent.Message(
                    DEVICE_UID,
                    AqlWsIncomingMessage.Response(
                        id = command.id,
                        type = AqlWsContract.TYPE_RESPONSE,
                        module = command.module,
                        action = command.action,
                        data = ownershipResetJson(),
                        ok = true,
                        statusCode = 200
                    )
                )
            )

            assertTrue(awaiting.await() is DeviceRuntimeCommandOutcome.Success)
            assertEquals(listOf(DEVICE_UID), tokenProvider.clearedDeviceUids)
            assertNull(repository.currentConnectionState(DEVICE_UID))
            assertTrue(transport.closed)
            assertTrue(
                states.any { state ->
                    state is AqlWsConnectionState.AuthRequired && state.deviceUid == DEVICE_UID
                }
            )
            collector.cancel()
            repository.close()
        }

    private fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID),
        endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.20", wsPort = 80)
    )

    private fun ownershipResetJson(): JSONObject = JSONObject()
        .put("operation", "unpair")
        .put("paired", false)
        .put("credentialReturned", false)
        .put("runtimeTransport", "websocket")
        .put("command", "security.unpair")
        .put(
            "message",
            "runtime credential cleared; encrypted BLE ownership is required again"
        )
        .put("status", unpairedStatusJson())

    private fun unpairedStatusJson(): JSONObject = JSONObject()
        .put("tokenGateEnabled", true)
        .put("dynamicPairingEnabled", true)
        .put("paired", false)
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

    private class RecordingTokenProvider : AqlWsTokenProvider {
        val clearedDeviceUids = CopyOnWriteArrayList<DeviceUid>()

        override suspend fun getToken(deviceUid: DeviceUid): String = "0".repeat(64)

        override suspend fun saveToken(deviceUid: DeviceUid, token: String) = Unit

        override suspend fun clearToken(deviceUid: DeviceUid) {
            clearedDeviceUids += deviceUid
        }
    }

    private class RecordingTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 32)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        val commands = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()
        var closed: Boolean = false
            private set

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            _connectionState.value = AqlWsConnectionState.Connected(
                deviceUid = deviceUid,
                url = "ws://${endpoint.ip}:${endpoint.wsPort}",
                connectedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            commands += message as AqlWsOutgoingMessage.Command
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            closed = true
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        fun authenticate() {
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = DEVICE_UID,
                authenticatedAtMillis = 2L
            )
            emit(AqlWsEvent.Authenticated(DEVICE_UID))
        }

        fun emit(event: AqlWsEvent) {
            _events.tryEmit(event)
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-SECURITY-LIFECYCLE")
    }
}

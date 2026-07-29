package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRuntimeMetadataReconnectBootstrapTest {

    @Test
    fun `authenticated reused socket starts one bootstrap for an untrusted snapshot`() {
        val transport = AuthenticatedWsTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        val snapshot = DeviceSnapshot(
            identity = DeviceIdentity(uid = DEVICE_UID),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.20", wsPort = 80)
        )

        repository.connect(snapshot).getOrThrow()
        repository.connect(snapshot).getOrThrow()
        assertEquals(EXPECTED_BOOTSTRAP_COMMAND_COUNT, transport.commands.size)

        repository.connect(snapshot).getOrThrow()
        assertEquals(EXPECTED_BOOTSTRAP_COMMAND_COUNT, transport.commands.size)
        repository.close()
    }

    private class AuthenticatedWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 1)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        val commands = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            check(endpoint.hasWebSocketEndpoint)
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = deviceUid,
                authenticatedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            val command = message as? AqlWsOutgoingMessage.Command ?: return false
            commands += command
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-RUNTIME-TRUST")
        const val EXPECTED_BOOTSTRAP_COMMAND_COUNT = 3
    }
}

package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.boundToGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeGenerationBoundSendTest {
    @Test
    fun `reused transport cannot send captured command on replacement socket`() = runBlocking {
        val transport = ReusedTransport()
        val repository = repository(transport)
        try {
            repository.connect(snapshot("192.168.1.42")).getOrThrow()
            transport.authenticate()
            val generation = checkNotNull(repository.currentConnectionGeneration(UID))
            val outcome = repository.executeCommand(UID, command {
                repository.connect(snapshot("192.168.1.43")).getOrThrow()
                transport.authenticate()
            }.boundToGeneration(generation))

            assertTrue(outcome is DeviceRuntimeCommandOutcome.SendFailed)
            assertEquals(generation, (outcome as DeviceRuntimeCommandOutcome.SendFailed).generation)
            assertEquals(2, transport.connections)
            assertTrue(transport.sent.none { it.module == AqlWsContract.MODULE_NETWORK })
            assertEquals(0, repository.pendingCommandCount())
        } finally {
            repository.close()
        }
    }

    @Test
    fun `retired session cannot send a command captured before retirement`() = runBlocking {
        val transport = ReusedTransport()
        val repository = repository(transport)
        try {
            repository.connect(snapshot("192.168.1.42")).getOrThrow()
            transport.authenticate()
            val outcome = repository.executeCommand(UID, command { repository.close(UID) })

            assertTrue(outcome is DeviceRuntimeCommandOutcome.SendFailed)
            assertTrue(transport.sent.none { it.module == AqlWsContract.MODULE_NETWORK })
            assertEquals(0, repository.pendingCommandCount())
        } finally {
            repository.close()
        }
    }

    private fun repository(transport: ReusedTransport) = DeviceRuntimeRepository(
        wsClientFactory = { transport }, dispatcher = Dispatchers.Unconfined
    )

    private fun snapshot(ip: String) = DeviceSnapshot(
        identity = DeviceIdentity(uid = UID, customName = "Bound send device"),
        product = DeviceProduct(),
        endpoint = DeviceRuntimeEndpoint(ip = ip, wsPort = 80)
    )

    private fun command(beforeEncode: () -> Unit) = object : DeviceRuntimeCommand<String> {
        override val module = AqlWsContract.MODULE_NETWORK
        override val action = AqlWsContract.ACTION_NETWORK_STATUS_GET
        override fun encodeData(): JSONObject {
            beforeEncode()
            return JSONObject()
        }
        override fun parseSuccess(response: AqlWsIncomingMessage.Response): String =
            response.data.getString("value")
    }

    private class ReusedTransport : AqlWsTransport {
        private val state = MutableStateFlow<AqlWsConnectionState>(AqlWsConnectionState.Disconnected)
        override val connectionState = state.asStateFlow()
        private val eventFlow = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 32)
        override val events = eventFlow.asSharedFlow()
        var connections = 0
        val sent = mutableListOf<AqlWsOutgoingMessage.Command>()

        override fun connect(deviceUid: DeviceUid, endpoint: DeviceRuntimeEndpoint): Result<Unit> {
            connections += 1
            state.value = AqlWsConnectionState.Connected(deviceUid, "ws://${endpoint.ip}:80", 1L)
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            sent += message as AqlWsOutgoingMessage.Command
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            state.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            state.value = AqlWsConnectionState.Disconnected
        }

        fun authenticate() {
            state.value = AqlWsConnectionState.Authenticated(UID, 2L)
            check(eventFlow.tryEmit(AqlWsEvent.Authenticated(UID)))
        }
    }

    private companion object {
        val UID = DeviceUid("generation-bound-send")
    }
}

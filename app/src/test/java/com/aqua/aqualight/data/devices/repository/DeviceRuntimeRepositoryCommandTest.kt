package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeRepositoryCommandTest {

    @Test
    fun `repository requires authentication then completes exact current-generation command`() =
        runBlocking {
            val transport = RecordingTransport()
            val repository = repository(transport)
            repository.connect(snapshot()).getOrThrow()

            assertTrue(
                repository.executeCommand(DEVICE_UID, NetworkEchoCommand()) is
                    DeviceRuntimeCommandOutcome.NotAuthenticated
            )

            transport.authenticate()
            val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
                repository.executeCommand(DEVICE_UID, NetworkEchoCommand())
            }
            val command = transport.commands.last { item ->
                item.module == AqlWsContract.MODULE_NETWORK
            }
            transport.emit(
                AqlWsEvent.Message(
                    DEVICE_UID,
                    AqlWsIncomingMessage.Response(
                        id = command.id,
                        type = AqlWsContract.TYPE_RESPONSE,
                        module = command.module,
                        action = command.action,
                        data = JSONObject().put("value", "online"),
                        ok = true,
                        statusCode = 200
                    )
                )
            )

            val success = awaiting.await() as DeviceRuntimeCommandOutcome.Success
            assertEquals("online", success.value)
            assertEquals(0, repository.pendingCommandCount())
            repository.close()
        }

    @Test
    fun `endpoint reconnect rotates generation and cancels old pending command`() = runBlocking {
        val transport = RecordingTransport()
        val repository = repository(transport)
        repository.connect(snapshot()).getOrThrow()
        transport.authenticate()
        val firstGeneration = requireNotNull(repository.currentConnectionGeneration(DEVICE_UID))

        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            repository.executeCommand(DEVICE_UID, NetworkEchoCommand())
        }
        assertEquals(1, repository.pendingCommandCount())

        repository.connect(snapshot(ip = "192.168.1.21")).getOrThrow()
        val secondGeneration = requireNotNull(repository.currentConnectionGeneration(DEVICE_UID))
        assertTrue(secondGeneration.value > firstGeneration.value)
        assertTrue(awaiting.await() is DeviceRuntimeCommandOutcome.Cancelled)
        assertEquals(0, repository.pendingCommandCount())
        repository.close()
    }

    @Test
    fun `product module command is rejected until authenticated metadata proves support`() =
        runBlocking {
            val transport = RecordingTransport()
            val repository = repository(transport)
            repository.connect(snapshot()).getOrThrow()
            transport.authenticate()

            val result = repository.executeCommand(DEVICE_UID, TimerStatusCommand())
            assertTrue(result is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
            assertTrue(
                transport.commands.none { command ->
                    command.module == AqlWsContract.MODULE_TIMER
                }
            )
            repository.close()
        }

    private fun repository(transport: RecordingTransport): DeviceRuntimeRepository =
        DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )

    private fun snapshot(ip: String = "192.168.1.20"): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID),
        endpoint = DeviceRuntimeEndpoint(ip = ip, wsPort = 80)
    )

    private class NetworkEchoCommand : DeviceRuntimeCommand<String> {
        override val module: String = AqlWsContract.MODULE_NETWORK
        override val action: String = AqlWsContract.ACTION_NETWORK_STATUS_GET
        override fun encodeData(): JSONObject = JSONObject()
        override fun parseSuccess(response: AqlWsIncomingMessage.Response): String {
            require(response.data.keys().asSequence().toSet() == setOf("value"))
            return response.data.getString("value")
        }
    }

    private class TimerStatusCommand : DeviceRuntimeCommand<Unit> {
        override val module: String = AqlWsContract.MODULE_TIMER
        override val action: String = AqlWsContract.ACTION_TIMER_STATUS_GET
        override fun encodeData(): JSONObject = JSONObject()
        override fun parseSuccess(response: AqlWsIncomingMessage.Response) = Unit
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
            disconnect(reason = "closed")
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
        val DEVICE_UID = DeviceUid("AQL-REPOSITORY-EXECUTOR")
    }
}

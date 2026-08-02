package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeRepositoryRequestBrokerTest {

    @Test
    fun `exact response completes typed request and is not leaked outside transport routing`() =
        runBlocking {
            val transport = RecordingTransport()
            val repository = repository(transport)
            repository.connect(snapshot()).getOrThrow()
            transport.authenticate()

            val externalEvent = async(start = CoroutineStart.UNDISPATCHED) {
                repository.events.first()
            }
            val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
                repository.executeCommand(DEVICE_UID, EchoCommand())
            }
            val command = transport.lastCommand(
                module = AqlWsContract.MODULE_NETWORK,
                action = AqlWsContract.ACTION_NETWORK_STATUS_GET
            )

            transport.emit(
                AqlWsEvent.Message(
                    DEVICE_UID,
                    response(command, value = "online")
                )
            )

            val success = awaiting.await() as DeviceRuntimeCommandOutcome.Success
            assertEquals("online", success.value)
            assertEquals(0, repository.pendingCommandCount())
            assertFalse(externalEvent.isCompleted)

            val unmatched = AqlWsEvent.Message(
                DEVICE_UID,
                AqlWsIncomingMessage.Event(
                    id = "evt-broker-regression",
                    type = AqlWsContract.TYPE_EVENT,
                    module = AqlWsContract.MODULE_NETWORK,
                    action = "state.changed",
                    data = JSONObject().put("connected", true)
                )
            )
            transport.emit(unmatched)
            assertEquals(unmatched, externalEvent.await())
            repository.close()
        }

    @Test
    fun `terminal transport state cancels pending request and clears registry`() = runBlocking {
        val transport = RecordingTransport()
        val repository = repository(transport)
        repository.connect(snapshot()).getOrThrow()
        transport.authenticate()

        val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
            repository.executeCommand(DEVICE_UID, EchoCommand())
        }
        assertEquals(1, repository.pendingCommandCount())

        transport.closeCurrent()

        val cancelled = awaiting.await() as DeviceRuntimeCommandOutcome.Cancelled
        assertEquals("runtime transport unavailable", cancelled.reason)
        assertEquals(0, repository.pendingCommandCount())
        repository.close()
    }

    @Test
    fun `metadata bootstrap responses remain unmatched and observable`() = runBlocking {
        val transport = RecordingTransport()
        val repository = repository(transport)
        repository.connect(snapshot()).getOrThrow()
        transport.authenticate()

        val identity = transport.lastCommand(
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_IDENTITY_GET
        )
        val externalEvent = async(start = CoroutineStart.UNDISPATCHED) {
            repository.events.first()
        }
        val responseEvent = AqlWsEvent.Message(
            DEVICE_UID,
            response(identity, JSONObject().put("productKey", "test"))
        )

        transport.emit(responseEvent)

        assertEquals(responseEvent, externalEvent.await())
        assertEquals(0, repository.pendingCommandCount())
        repository.close()
    }

    private fun repository(transport: RecordingTransport): DeviceRuntimeRepository =
        DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )

    private fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID, customName = "Broker Device"),
        product = DeviceProduct(),
        endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.42", wsPort = 80)
    )

    private fun response(
        command: AqlWsOutgoingMessage.Command,
        value: String
    ): AqlWsIncomingMessage.Response = response(
        command = command,
        data = JSONObject().put("value", value)
    )

    private fun response(
        command: AqlWsOutgoingMessage.Command,
        data: JSONObject
    ): AqlWsIncomingMessage.Response = AqlWsIncomingMessage.Response(
        id = command.id,
        type = AqlWsContract.TYPE_RESPONSE,
        module = command.module,
        action = command.action,
        data = data,
        ok = true,
        statusCode = 200
    )

    private class EchoCommand : DeviceRuntimeCommand<String> {
        override val module: String = AqlWsContract.MODULE_NETWORK
        override val action: String = AqlWsContract.ACTION_NETWORK_STATUS_GET

        override fun encodeData(): JSONObject = JSONObject()

        override fun parseSuccess(response: AqlWsIncomingMessage.Response): String {
            require(response.statusCode == 200)
            require(response.data.keys().asSequence().toSet() == setOf("value"))
            return response.data.getString("value").also { require(it.isNotBlank()) }
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

        private val sent = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()

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
            val command = message as? AqlWsOutgoingMessage.Command ?: return false
            sent += command
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        fun authenticate() {
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = DEVICE_UID,
                authenticatedAtMillis = 2L
            )
            assertTrue(_events.tryEmit(AqlWsEvent.Authenticated(DEVICE_UID)))
        }

        fun closeCurrent() {
            _connectionState.value = AqlWsConnectionState.Disconnected
            assertTrue(
                _events.tryEmit(
                    AqlWsEvent.Closed(
                        deviceUid = DEVICE_UID,
                        code = 1000,
                        reason = "test closed"
                    )
                )
            )
        }

        fun emit(event: AqlWsEvent) {
            assertTrue(_events.tryEmit(event))
        }

        fun lastCommand(module: String, action: String): AqlWsOutgoingMessage.Command =
            sent.last { command -> command.module == module && command.action == action }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-BROKER-DEVICE")
    }
}

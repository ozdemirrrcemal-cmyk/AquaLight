package com.aqua.aqualight.data.devices.runtime.events

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeEventPipelineTest {

    @Test
    fun `typed pipeline routes event while legacy raw event remains observable`() = runBlocking {
        val transport = RecordingTransport()
        val repository = repository(transport)
        val pipeline = DeviceRuntimeEventPipeline(repository, this)
        repository.connect(snapshot()).getOrThrow()
        transport.authenticate()

        val typed = async(start = CoroutineStart.UNDISPATCHED) { pipeline.events.first() }
        val routing = async(start = CoroutineStart.UNDISPATCHED) {
            pipeline.routingResults.first()
        }
        val raw = async(start = CoroutineStart.UNDISPATCHED) {
            repository.events.filterIsInstance<AqlWsEvent.Message>().first { event ->
                event.parsed is AqlWsIncomingMessage.Event
            }
        }
        val rawEvent = AqlWsEvent.Message(
            deviceUid = DEVICE_UID,
            parsed = AqlWsIncomingMessage.Event(
                id = "evt-network",
                type = AqlWsContract.TYPE_EVENT,
                module = DeviceRuntimeTypedEvent.Type.NETWORK_STATE_CHANGED.module,
                action = DeviceRuntimeTypedEvent.Type.NETWORK_STATE_CHANGED.action,
                data = JSONObject().put("connected", true)
            )
        )

        transport.emit(rawEvent)

        assertEquals(rawEvent, raw.await())
        assertEquals(
            DeviceRuntimeTypedEvent.Type.NETWORK_STATE_CHANGED,
            typed.await().type
        )
        assertTrue(routing.await() is DeviceRuntimeEventRoutingResult.Routed)
        assertEquals(
            true,
            (pipeline.states.value.getValue(DEVICE_UID)
                .getValue(DeviceRuntimeTypedEvent.Type.NETWORK_STATE_CHANGED)
                .payload as DeviceRuntimeEventPayload.Snapshot)
                .data.getBoolean("connected")
        )

        pipeline.shutdown()
        repository.close()
    }

    @Test
    fun `terminal repository event clears current generation state`() = runBlocking {
        val transport = RecordingTransport()
        val repository = repository(transport)
        val pipeline = DeviceRuntimeEventPipeline(repository, this)
        repository.connect(snapshot()).getOrThrow()
        transport.authenticate()

        val typed = async(start = CoroutineStart.UNDISPATCHED) { pipeline.events.first() }
        transport.emit(
            AqlWsEvent.Message(
                deviceUid = DEVICE_UID,
                parsed = AqlWsIncomingMessage.Event(
                    id = "evt-light",
                    type = AqlWsContract.TYPE_EVENT,
                    module = DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED.module,
                    action = DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED.action,
                    data = JSONObject().put("active", true)
                )
            )
        )
        typed.await()
        assertTrue(pipeline.states.value.containsKey(DEVICE_UID))

        transport.closeCurrent()

        withTimeout(EVENT_PROPAGATION_TIMEOUT_MILLIS) {
            pipeline.states.first { states -> states.isEmpty() }
        }
        pipeline.shutdown()
        repository.close()
    }

    private fun repository(transport: RecordingTransport): DeviceRuntimeRepository =
        DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )

    private fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID, customName = "Event Device"),
        product = DeviceProduct(),
        endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.42", wsPort = 80)
    )

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
    }

    private companion object {
        const val EVENT_PROPAGATION_TIMEOUT_MILLIS = 1_000L
        val DEVICE_UID = DeviceUid("AQL-EVENT-PIPELINE")
    }
}

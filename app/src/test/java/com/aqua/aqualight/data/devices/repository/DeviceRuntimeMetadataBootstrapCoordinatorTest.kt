package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeMetadataBootstrapCoordinatorTest {

    @Test
    fun `authenticated runtime sends only exact metadata bootstrap commands`() {
        val transport = RecordingWsTransport()
        val repository = repository(transport)
        val deviceUid = DeviceUid("bootstrap-device")
        repository.connect(snapshot(deviceUid)).getOrThrow()

        transport.emit(AqlWsEvent.Authenticated(deviceUid))

        assertEquals(EXPECTED_METADATA_COMMANDS, transport.commandKeys())
        assertFalse(transport.commandKeys().any { (module, _) -> module == AqlWsContract.MODULE_TIME })
        assertFalse(transport.commandKeys().any { (module, _) -> module == AqlWsContract.MODULE_LIGHT })
        assertFalse(transport.commandKeys().any { (module, _) -> module == AqlWsContract.MODULE_TIMER })
        assertTrue(
            repository.metadataBootstrapCoordinator.currentState(deviceUid) is
                DeviceRuntimeMetadataGenerationState.Collecting
        )
        repository.close()
    }

    @Test
    fun `reauthentication opens a fresh generation and retires old request ids`() {
        val transport = RecordingWsTransport()
        val repository = repository(transport)
        val deviceUid = DeviceUid("reauth-device")
        repository.connect(snapshot(deviceUid)).getOrThrow()

        transport.emit(AqlWsEvent.Authenticated(deviceUid))
        val firstCommands = transport.commands()
        val firstGeneration = requireNotNull(
            repository.metadataBootstrapCoordinator.currentState(deviceUid)
        ).generation

        transport.emit(AqlWsEvent.Authenticated(deviceUid))
        val secondCommands = transport.commands().drop(firstCommands.size)
        val secondGeneration = requireNotNull(
            repository.metadataBootstrapCoordinator.currentState(deviceUid)
        ).generation

        assertEquals(EXPECTED_METADATA_COMMANDS, secondCommands.map { it.module to it.action })
        assertEquals(firstGeneration.value + 1L, secondGeneration.value)
        assertNotEquals(firstCommands.map { it.id }, secondCommands.map { it.id })
        val staleClaim = repository.metadataBootstrapCoordinator.claim(
            deviceUid = deviceUid,
            response = responseFor(firstCommands.first())
        )
        assertTrue(staleClaim is DeviceRuntimeMetadataBootstrapClaim.Unmatched)
        repository.close()
    }

    @Test
    fun `transport failure rejects generation and stops remaining bootstrap commands`() {
        val transport = RecordingWsTransport(
            failingAction = AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET
        )
        val repository = repository(transport)
        val deviceUid = DeviceUid("dispatch-failure-device")
        repository.connect(snapshot(deviceUid)).getOrThrow()

        transport.emit(AqlWsEvent.Authenticated(deviceUid))

        assertEquals(
            EXPECTED_METADATA_COMMANDS.take(EXPECTED_COMMANDS_BEFORE_FAILURE),
            transport.commandKeys()
        )
        assertEquals(METADATA_BOOTSTRAP_FAILED_REASON, transport.lastDisconnectReason)
        val rejected = repository.metadataBootstrapCoordinator.currentState(deviceUid) as
            DeviceRuntimeMetadataGenerationState.Rejected
        assertEquals(DeviceRuntimeMetadataFailureCode.BOOTSTRAP_DISPATCH_FAILED, rejected.failure.code)
        repository.close()
    }

    @Test
    fun `response claim requires exact id module action and success status`() {
        val coordinator = DeviceRuntimeMetadataBootstrapCoordinator()
        val sent = mutableListOf<AqlWsOutgoingMessage.Command>()
        val deviceUid = DeviceUid("correlation-device")
        coordinator.beginAndDispatch(deviceUid) { command ->
            sent += command
            true
        }.getOrThrow()

        val accepted = coordinator.claim(
            deviceUid = deviceUid,
            response = responseFor(sent.first())
        )
        assertTrue(accepted is DeviceRuntimeMetadataBootstrapClaim.Accepted)

        val capabilities = sent[CAPABILITIES_COMMAND_INDEX]
        val rejected = coordinator.claim(
            deviceUid = deviceUid,
            response = responseFor(capabilities).copy(action = AqlWsContract.ACTION_DEVICE_STATUS_GET)
        )
        assertTrue(rejected is DeviceRuntimeMetadataBootstrapClaim.Rejected)
        val state = coordinator.currentState(deviceUid) as DeviceRuntimeMetadataGenerationState.Rejected
        assertEquals(DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH, state.failure.code)
    }

    private fun repository(transport: RecordingWsTransport): DeviceRuntimeRepository =
        DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )

    private fun snapshot(deviceUid: DeviceUid): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = deviceUid),
        product = DeviceProduct(),
        endpoint = DeviceRuntimeEndpoint(
            ip = "192.168.1.20",
            wsPort = WS_PORT
        )
    )

    private fun responseFor(
        command: AqlWsOutgoingMessage.Command
    ): AqlWsIncomingMessage.Response = AqlWsIncomingMessage.Response(
        id = command.id,
        type = AqlWsContract.TYPE_RESPONSE,
        module = command.module,
        action = command.action,
        data = JSONObject(),
        ok = true,
        statusCode = SUCCESS_STATUS
    )

    private class RecordingWsTransport(
        private val failingAction: String? = null
    ) : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        private val sentCommands = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()
        var lastDisconnectReason: String? = null
            private set

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            _connectionState.value = AqlWsConnectionState.Connected(
                deviceUid = deviceUid,
                url = "ws://${endpoint.ip}:${endpoint.wsPort}",
                connectedAtMillis = CONNECTED_AT_MILLIS
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            val command = message as? AqlWsOutgoingMessage.Command ?: return false
            sentCommands += command
            return command.action != failingAction
        }

        override fun disconnect(code: Int, reason: String) {
            lastDisconnectReason = reason
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            disconnect(reason = "closed")
        }

        fun emit(event: AqlWsEvent) {
            _events.tryEmit(event)
        }

        fun commands(): List<AqlWsOutgoingMessage.Command> = sentCommands.toList()

        fun commandKeys(): List<Pair<String, String>> =
            sentCommands.map { command -> command.module to command.action }
    }

    private companion object {
        const val WS_PORT = 80
        const val SUCCESS_STATUS = 200
        const val CONNECTED_AT_MILLIS = 1L
        const val EVENT_BUFFER_CAPACITY = 16
        const val CAPABILITIES_COMMAND_INDEX = 1
        const val EXPECTED_COMMANDS_BEFORE_FAILURE = 2
        const val METADATA_BOOTSTRAP_FAILED_REASON = "metadata bootstrap failed"

        val EXPECTED_METADATA_COMMANDS = listOf(
            AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_IDENTITY_GET,
            AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET,
            AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_STATUS_GET
        )
    }
}

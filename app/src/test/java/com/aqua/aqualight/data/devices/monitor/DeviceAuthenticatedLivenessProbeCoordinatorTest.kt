package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceIdentity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAuthenticatedLivenessProbeCoordinatorTest {

    @Test
    fun `one typed probe is in flight and successful completion preserves cadence`() = runBlocking {
        val fixture = fixture()
        val coordinator = DeviceAuthenticatedLivenessProbeCoordinator(fixture.repository)

        assertTrue(
            coordinator.request(
                scope = this,
                deviceUid = DEVICE_UID,
                nowMillis = 1_000L,
                force = false,
                minimumIntervalMillis = 8_000L,
                timeoutMillis = 3_000L
            )
        )
        assertTrue(coordinator.isInFlight(DEVICE_UID))
        assertTrue(
            coordinator.request(
                scope = this,
                deviceUid = DEVICE_UID,
                nowMillis = 1_001L,
                force = false,
                minimumIntervalMillis = 8_000L,
                timeoutMillis = 3_000L
            )
        )
        assertEquals(1, fixture.transport.networkCommands().size)

        fixture.transport.completeLatestNetworkSuccess(networkStatusJson())
        awaitProbeIdle(coordinator)

        assertEquals(1_000L, coordinator.lastProbeAt(DEVICE_UID))
        assertFalse(
            coordinator.request(
                scope = this,
                deviceUid = DEVICE_UID,
                nowMillis = 8_999L,
                force = false,
                minimumIntervalMillis = 8_000L,
                timeoutMillis = 3_000L
            )
        )
        fixture.repository.close()
    }

    @Test
    fun `firmware error reopens retry window and reset cancels pending probe`() = runBlocking {
        val fixture = fixture()
        val coordinator = DeviceAuthenticatedLivenessProbeCoordinator(fixture.repository)

        assertTrue(
            coordinator.request(
                scope = this,
                deviceUid = DEVICE_UID,
                nowMillis = 10_000L,
                force = true,
                minimumIntervalMillis = 8_000L,
                timeoutMillis = 3_000L
            )
        )
        fixture.transport.completeLatestNetworkError()
        awaitProbeIdle(coordinator)
        assertNull(coordinator.lastProbeAt(DEVICE_UID))

        assertTrue(
            coordinator.request(
                scope = this,
                deviceUid = DEVICE_UID,
                nowMillis = 10_001L,
                force = false,
                minimumIntervalMillis = 8_000L,
                timeoutMillis = 3_000L
            )
        )
        assertTrue(coordinator.isInFlight(DEVICE_UID))
        coordinator.reset()
        awaitProbeIdle(coordinator)
        assertNull(coordinator.lastProbeAt(DEVICE_UID))
        assertEquals(0, fixture.repository.pendingCommandCount())
        fixture.repository.close()
    }

    private fun fixture(): Fixture {
        val transport = RecordingTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        repository.connect(
            DeviceSnapshot(
                identity = DeviceIdentity(uid = DEVICE_UID),
                endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.42", wsPort = 80)
            )
        ).getOrThrow()
        transport.authenticate()
        return Fixture(repository, transport)
    }

    private suspend fun awaitProbeIdle(
        coordinator: DeviceAuthenticatedLivenessProbeCoordinator
    ) {
        withTimeout(1_000L) {
            while (coordinator.isInFlight(DEVICE_UID)) delay(1L)
        }
    }

    private data class Fixture(
        val repository: DeviceRuntimeRepository,
        val transport: RecordingTransport
    )

    private class RecordingTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 32)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()
        private val commands = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()

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
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        fun authenticate() {
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = DEVICE_UID,
                authenticatedAtMillis = 2L
            )
            _events.tryEmit(AqlWsEvent.Authenticated(DEVICE_UID))
        }

        fun networkCommands(): List<AqlWsOutgoingMessage.Command> = commands.filter { command ->
            command.module == AqlWsContract.MODULE_NETWORK &&
                command.action == AqlWsContract.ACTION_NETWORK_STATUS_GET
        }

        fun completeLatestNetworkSuccess(data: JSONObject) {
            val command = networkCommands().last()
            _events.tryEmit(
                AqlWsEvent.Message(
                    DEVICE_UID,
                    AqlWsIncomingMessage.Response(
                        id = command.id,
                        type = AqlWsContract.TYPE_RESPONSE,
                        module = command.module,
                        action = command.action,
                        data = data,
                        ok = true,
                        statusCode = 200
                    )
                )
            )
        }

        fun completeLatestNetworkError() {
            val command = networkCommands().last()
            _events.tryEmit(
                AqlWsEvent.Message(
                    DEVICE_UID,
                    AqlWsIncomingMessage.Error(
                        id = command.id,
                        type = AqlWsContract.TYPE_ERROR,
                        module = command.module,
                        action = command.action,
                        data = JSONObject(),
                        message = "Network status unavailable.",
                        statusCode = 503,
                        code = "unavailable",
                        field = ""
                    )
                )
            )
        }
    }

    private fun networkStatusJson(): JSONObject = JSONObject()
        .put("ip", "192.168.1.42")
        .put("macAddress", "AA:BB:CC:DD:EE:FF")
        .put("wifiModeCode", 1)
        .put("wifiMode", "client")
        .put("stationEnabled", true)
        .put("setupApEnabled", false)
        .put("clientConnected", true)
        .put("setupApActive", false)
        .put("uptimeMs", 123_456)
        .put(
            "client",
            JSONObject()
                .put("enabled", true)
                .put("configured", true)
                .put("ssid", "Aqua LAN")
                .put("bssidConfigured", false)
                .put("channel", 6)
                .put("connected", true)
                .put("state", "gotIp")
                .put("wifiStatus", 3)
                .put("ip", "192.168.1.42")
                .put("gateway", "192.168.1.1")
                .put("subnet", "255.255.255.0")
                .put("dns", "192.168.1.1")
                .put("rssi", -55)
                .put("lastWifiEvent", 7)
                .put("lastDisconnectReason", 0)
                .put("lastDisconnectReasonName", "none")
                .put("lastDisconnectAgeMs", 0)
                .put("lastGotIpAgeMs", 12_000)
                .put("nextRetryRemainingMs", 0)
                .put("connectionInProgress", false)
        )
        .put(
            "setupAp",
            JSONObject()
                .put("enabled", false)
                .put("active", false)
                .put("ssid", "AquaLight-Setup")
                .put("ip", "0.0.0.0")
                .put("stationCount", 0)
        )
        .put(
            "discovery",
            JSONObject()
                .put("ready", true)
                .put("port", 10_888)
                .put("broadcastIp", "192.168.1.255")
                .put("currentIp", "192.168.1.42")
                .put("payloadSize", 420)
                .put("lastRefreshMs", 120_000)
                .put("lastPacketRejectedMs", 0)
                .put("rejectedPacketCount", 0)
        )
        .put(
            "runtime",
            JSONObject()
                .put("transport", "websocket")
                .put("wsPort", 80)
                .put("wsPath", "/aql/v1/ws")
                .put("wsProtocol", "aql.ws.v1")
                .put("wsProtocolVersion", 1)
        )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIVENESS-000001")
    }
}

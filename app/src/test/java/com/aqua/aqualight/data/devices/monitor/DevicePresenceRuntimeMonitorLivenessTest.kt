package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryRefreshSender
import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoverySupervisor
import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryUdpScanner
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceDiscoveryRepository
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LargeClass", "LongMethod", "MagicNumber", "TooManyFunctions")
class DevicePresenceRuntimeMonitorLivenessTest {

    @Test
    fun `successful current generation probe commits canonical control proof`() = runBlocking {
        val harness = MonitorHarness()
        try {
            harness.start()
            val command = harness.transport.awaitNetworkStatusCommand()

            harness.transport.respond(command, networkStatusData())
            awaitCondition {
                harness.currentSnapshot().connectionState.lastControlProofElapsedMillis ==
                    TEST_ELAPSED_MILLIS
            }

            assertEquals(
                DeviceOnlineState.AUTHENTICATED,
                harness.currentSnapshot().connectionState.onlineState
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun `background transition cancels in flight monitor proof`() = runBlocking {
        val harness = MonitorHarness()
        try {
            harness.start()
            harness.transport.awaitNetworkStatusCommand()
            assertEquals(1, harness.runtime.pendingCommandCount())

            harness.monitor.setAppForeground(false)
            awaitCondition { harness.runtime.pendingCommandCount() == 0 }

            assertNull(harness.currentSnapshot().connectionState.lastControlProofElapsedMillis)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `local network loss cancels in flight monitor proof`() = runBlocking {
        val harness = MonitorHarness()
        try {
            harness.start()
            harness.transport.awaitNetworkStatusCommand()
            assertEquals(1, harness.runtime.pendingCommandCount())

            harness.monitor.reevaluateNow(localNetworkAvailable = false)
            awaitCondition { harness.runtime.pendingCommandCount() == 0 }

            val state = harness.currentSnapshot().connectionState
            assertNull(state.lastControlProofElapsedMillis)
            assertEquals(DeviceOnlineState.LOCAL_NETWORK_OFFLINE, state.onlineState)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `replaced generation cannot complete monitor proof`() = runBlocking {
        val harness = MonitorHarness()
        try {
            harness.start()
            val staleCommand = harness.transport.awaitNetworkStatusCommand()
            val firstGeneration = harness.runtime.currentConnectionGeneration(DEVICE_UID)

            harness.runtime.connect(
                harness.snapshot.copy(
                    endpoint = harness.snapshot.endpoint.copy(ip = "192.168.1.99")
                )
            ).getOrThrow()
            val secondGeneration = harness.runtime.currentConnectionGeneration(DEVICE_UID)
            assertNotEquals(firstGeneration, secondGeneration)
            harness.transport.authenticate(DEVICE_UID)

            harness.transport.respond(staleCommand, networkStatusData())
            awaitCondition { harness.runtime.pendingCommandCount() == 0 }
            delay(25L)

            assertNull(harness.currentSnapshot().connectionState.lastControlProofElapsedMillis)
        } finally {
            harness.close()
        }
    }

    private class MonitorHarness {
        val snapshot = DeviceSnapshot(
            identity = DeviceIdentity(uid = DEVICE_UID, customName = "Monitor Device"),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.42", wsPort = 80)
        )
        val registry = DeviceRegistryStore().also { it.upsert(snapshot) }
        val transport = RecordingTransport()
        val runtime = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        val monitor = DevicePresenceRuntimeMonitor(
            discoveryRepository = noNetworkDiscoveryRepository(),
            registryStore = registry,
            runtimeRepository = runtime,
            statusAggregator = DeviceStatusAggregator(),
            elapsedRealtimeMillis = { TEST_ELAPSED_MILLIS }
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private var monitorJob: Job? = null

        init {
            runtime.connect(snapshot).getOrThrow()
            transport.authenticate(DEVICE_UID)
        }

        fun start() {
            monitorJob = monitor.start(scope)
        }

        fun currentSnapshot(): DeviceSnapshot = checkNotNull(registry.currentDevice(DEVICE_UID))

        suspend fun close() {
            monitorJob?.cancelAndJoin()
            scope.cancel()
            runtime.shutdown()
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
        private val networkStatusCommand = CompletableDeferred<AqlWsOutgoingMessage.Command>()

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            _connectionState.value = AqlWsConnectionState.Connected(
                deviceUid = deviceUid,
                url = "ws://${endpoint.ip}:${endpoint.wsPort}${endpoint.wsPath}",
                connectedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            val command = message as? AqlWsOutgoingMessage.Command ?: return false
            sent += command
            if (
                command.module == AqlWsContract.MODULE_NETWORK &&
                command.action == AqlWsContract.ACTION_NETWORK_STATUS_GET
            ) {
                networkStatusCommand.complete(command)
            }
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            disconnect(code = 1000, reason = "test closed")
        }

        fun authenticate(deviceUid: DeviceUid) {
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = deviceUid,
                authenticatedAtMillis = 2L
            )
            assertTrue(_events.tryEmit(AqlWsEvent.Authenticated(deviceUid)))
        }

        suspend fun awaitNetworkStatusCommand(): AqlWsOutgoingMessage.Command =
            withTimeout(TEST_TIMEOUT_MILLIS) { networkStatusCommand.await() }

        fun respond(command: AqlWsOutgoingMessage.Command, data: JSONObject) {
            assertTrue(command in sent)
            assertTrue(
                _events.tryEmit(
                    AqlWsEvent.Message(
                        deviceUid = DEVICE_UID,
                        parsed = AqlWsIncomingMessage.Response(
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
            )
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-MONITOR-LIVENESS")
        const val TEST_ELAPSED_MILLIS = 10_000L
        const val TEST_TIMEOUT_MILLIS = 1_000L

        fun noNetworkDiscoveryRepository(): DeviceDiscoveryRepository =
            DeviceDiscoveryRepository(
                discoverySupervisor = AqlDiscoverySupervisor(
                    scanner = AqlDiscoveryUdpScanner(
                        networkProvider = { null },
                        requireLocalNetwork = true
                    ),
                    refreshSender = AqlDiscoveryRefreshSender(
                        addressResolver = { emptyList() },
                        networkProvider = { null },
                        requireLocalNetwork = true
                    )
                )
            )

        suspend fun awaitCondition(condition: () -> Boolean) {
            withTimeout(TEST_TIMEOUT_MILLIS) {
                while (!condition()) yield()
            }
        }

        fun networkStatusData(): JSONObject = JSONObject()
            .put("ip", "192.168.1.42")
            .put("macAddress", "AA:BB:CC:DD:EE:FF")
            .put("wifiModeCode", 1)
            .put("wifiMode", "client")
            .put("stationEnabled", true)
            .put("setupApEnabled", false)
            .put("clientConnected", true)
            .put("setupApActive", false)
            .put("uptimeMs", 10_000L)
            .put(
                "client",
                JSONObject()
                    .put("enabled", true)
                    .put("configured", true)
                    .put("ssid", "AquaLight")
                    .put("bssidConfigured", false)
                    .put("channel", 6)
                    .put("connected", true)
                    .put("state", "connected")
                    .put("wifiStatus", 3)
                    .put("ip", "192.168.1.42")
                    .put("gateway", "192.168.1.1")
                    .put("subnet", "255.255.255.0")
                    .put("dns", "192.168.1.1")
                    .put("rssi", -45)
                    .put("lastWifiEvent", 0)
                    .put("lastDisconnectReason", 0)
                    .put("lastDisconnectReasonName", "none")
                    .put("lastDisconnectAgeMs", 0L)
                    .put("lastGotIpAgeMs", 100L)
                    .put("nextRetryRemainingMs", 0L)
                    .put("connectionInProgress", false)
            )
            .put(
                "setupAp",
                JSONObject()
                    .put("enabled", false)
                    .put("active", false)
                    .put("ssid", "")
                    .put("ip", "192.168.4.1")
                    .put("stationCount", 0)
            )
            .put(
                "discovery",
                JSONObject()
                    .put("ready", true)
                    .put("port", 10_888)
                    .put("broadcastIp", "192.168.1.255")
                    .put("currentIp", "192.168.1.42")
                    .put("payloadSize", 512)
                    .put("lastRefreshMs", 100L)
                    .put("lastPacketRejectedMs", 0L)
                    .put("rejectedPacketCount", 0L)
            )
            .put(
                "runtime",
                JSONObject()
                    .put("transport", "websocket")
                    .put("wsPort", 80)
                    .put("wsPath", AqlWsContract.DEFAULT_PATH)
                    .put("wsProtocol", AqlWsContract.SCHEMA)
                    .put("wsProtocolVersion", AqlWsContract.PROTOCOL_VERSION)
            )
    }
}

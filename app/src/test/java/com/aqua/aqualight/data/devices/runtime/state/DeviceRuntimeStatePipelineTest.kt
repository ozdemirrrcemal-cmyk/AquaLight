package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkClientState
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkClientStatus
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkDiscoveryStatus
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkDisconnectReason
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkRuntimeTransport
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkSetupApStatus
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkStatus
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkWifiMode
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeStatePipelineTest {

    @Test
    fun `store isolates devices and preserves old values as stale across generations`() {
        val store = DeviceRuntimeStateStore()
        val firstGeneration = DeviceRuntimeConnectionGeneration(1L)
        val secondGeneration = DeviceRuntimeConnectionGeneration(2L)
        val otherGeneration = DeviceRuntimeConnectionGeneration(3L)

        store.beginGeneration(DEVICE_A, firstGeneration, authenticated = true)
        store.beginGeneration(DEVICE_B, otherGeneration, authenticated = true)
        store.reduce(DEVICE_A, firstGeneration) { state ->
            state.copy(
                network = DeviceRuntimeValue(
                    phase = DeviceRuntimeFreshness.READY,
                    value = networkStatus(),
                    sourceMessageId = "network-1"
                )
            )
        }

        store.beginGeneration(DEVICE_A, secondGeneration, authenticated = false)

        val deviceA = requireNotNull(store.current(DEVICE_A))
        val deviceB = requireNotNull(store.current(DEVICE_B))
        assertEquals(secondGeneration, deviceA.generation)
        assertEquals(DeviceRuntimeFreshness.STALE, deviceA.network.phase)
        assertNotNull(deviceA.network.value)
        assertEquals("network-1", deviceA.network.sourceMessageId)
        assertFalse(deviceA.authenticated)
        assertEquals(otherGeneration, deviceB.generation)
        assertEquals(DeviceRuntimeFreshness.UNAVAILABLE, deviceB.network.phase)
        assertTrue(deviceB.authenticated)

        assertFalse(
            store.reduce(DEVICE_A, firstGeneration) { state ->
                state.copy(authenticated = true)
            }
        )
        assertFalse(requireNotNull(store.current(DEVICE_A)).authenticated)
    }

    @Test
    fun `reducer records loading error and rejects old generation completion`() {
        val store = DeviceRuntimeStateStore()
        val reducer = DeviceRuntimeStateReducer(
            store = store,
            clockMillis = { 100L },
            elapsedRealtimeMillis = { 200L }
        )
        val firstGeneration = DeviceRuntimeConnectionGeneration(1L)
        val secondGeneration = DeviceRuntimeConnectionGeneration(2L)
        store.beginGeneration(DEVICE_A, firstGeneration, authenticated = true)

        reducer.commandStarted(
            deviceUid = DEVICE_A,
            generation = firstGeneration,
            module = AqlWsContract.MODULE_NETWORK,
            action = AqlWsContract.ACTION_NETWORK_STATUS_GET
        )
        assertEquals(
            DeviceRuntimeFreshness.LOADING,
            requireNotNull(store.current(DEVICE_A)).network.phase
        )

        reducer.commandCompleted(
            DeviceRuntimeCommandOutcome.Timeout(
                deviceUid = DEVICE_A,
                module = AqlWsContract.MODULE_NETWORK,
                action = AqlWsContract.ACTION_NETWORK_STATUS_GET,
                messageId = "network-timeout",
                generation = firstGeneration,
                timeoutMillis = 8_000L
            )
        )
        val failed = requireNotNull(store.current(DEVICE_A)).network
        assertEquals(DeviceRuntimeFreshness.ERROR, failed.phase)
        assertEquals("network-timeout", failed.fault?.messageId)

        store.beginGeneration(DEVICE_A, secondGeneration, authenticated = true)
        reducer.commandCompleted(
            DeviceRuntimeCommandOutcome.ProtocolError(
                deviceUid = DEVICE_A,
                module = AqlWsContract.MODULE_NETWORK,
                action = AqlWsContract.ACTION_NETWORK_STATUS_GET,
                messageId = "late-response",
                generation = firstGeneration,
                reason = "old generation"
            )
        )

        val current = requireNotNull(store.current(DEVICE_A))
        assertEquals(secondGeneration, current.generation)
        assertNull(current.protocolFault)
        assertEquals(DeviceRuntimeFreshness.STALE, current.network.phase)
    }

    @Test
    fun `message router accepts exact active wrapper and rejects unknown fields`() {
        val store = DeviceRuntimeStateStore()
        val router = DeviceRuntimeMessageRouter(DeviceRuntimeStateReducer(store))
        val valid = statusChangedEvent()

        val route = router.route(valid)
        assertTrue(route is DeviceRuntimeEventRoute.Refresh)
        route as DeviceRuntimeEventRoute.Refresh
        assertEquals(DeviceRuntimeStateTarget.LIGHT, route.target)
        assertEquals("command-1", route.sourceMessageId)

        val invalid = valid.copy(data = JSONObject(valid.data.toString()).put("legacy", true))
        assertTrue(router.route(invalid) is DeviceRuntimeEventRoute.ProtocolFault)

        val inactive = valid.copy(action = "temperature.changed")
        assertTrue(router.route(inactive) is DeviceRuntimeEventRoute.ProtocolFault)
    }

    @Test
    fun `refresh coordinator deduplicates targets isolates devices and cancels generations`() =
        runTest {
            val store = DeviceRuntimeStateStore()
            val generations = mutableMapOf<DeviceUid, DeviceRuntimeConnectionGeneration>()
            val executions = mutableListOf<
                Triple<DeviceUid, DeviceRuntimeConnectionGeneration, DeviceRuntimeStateTarget>
                >()
            val generationA = DeviceRuntimeConnectionGeneration(1L)
            val generationB = DeviceRuntimeConnectionGeneration(2L)

            generations[DEVICE_A] = generationA
            generations[DEVICE_B] = generationB
            store.beginGeneration(DEVICE_A, generationA, authenticated = true)
            store.beginGeneration(DEVICE_B, generationB, authenticated = true)
            listOf(DEVICE_A to generationA, DEVICE_B to generationB).forEach { (uid, generation) ->
                store.reduce(uid, generation) { state ->
                    state.copy(support = DeviceRuntimeSupport(network = true))
                }
            }

            val coordinator = DeviceRuntimeRefreshCoordinator(
                scopeProvider = { this },
                generationProvider = generations::get,
                stateProvider = store::current,
                refreshAction = { uid, generation, target ->
                    executions += Triple(uid, generation, target)
                },
                eventDebounceMillis = 10L
            )

            assertTrue(coordinator.schedule(DEVICE_A, generationA, DeviceRuntimeStateTarget.NETWORK))
            assertFalse(coordinator.schedule(DEVICE_A, generationA, DeviceRuntimeStateTarget.NETWORK))
            assertTrue(coordinator.schedule(DEVICE_B, generationB, DeviceRuntimeStateTarget.NETWORK))
            advanceUntilIdle()

            assertEquals(2, executions.size)
            assertEquals(1, executions.count { it.first == DEVICE_A })
            assertEquals(1, executions.count { it.first == DEVICE_B })
            assertEquals(0, coordinator.pendingCount())

            val cancelledExecutions = AtomicInteger(0)
            val cancellationCoordinator = DeviceRuntimeRefreshCoordinator(
                scopeProvider = { this },
                generationProvider = generations::get,
                stateProvider = store::current,
                refreshAction = { _, _, _ -> cancelledExecutions.incrementAndGet() },
                eventDebounceMillis = 1_000L
            )
            assertTrue(
                cancellationCoordinator.scheduleEventRefresh(
                    DEVICE_A,
                    generationA,
                    DeviceRuntimeStateTarget.NETWORK
                )
            )
            cancellationCoordinator.cancelGeneration(DEVICE_A, generationA)
            advanceUntilIdle()
            assertEquals(0, cancelledExecutions.get())
        }

    private fun statusChangedEvent(): AqlWsIncomingMessage.Event =
        AqlWsIncomingMessage.Event(
            id = "event-1",
            type = AqlWsContract.TYPE_EVENT,
            module = AqlWsContract.MODULE_LIGHT,
            action = AqlWsContract.Event.STATUS_CHANGED,
            data = JSONObject()
                .put("commandId", "command-1")
                .put("module", AqlWsContract.MODULE_LIGHT)
                .put("action", AqlWsContract.ACTION_LIGHT_MANUAL_SET)
                .put("sessionId", "session-1")
                .put("publishedAtMs", 10L)
                .put("result", JSONObject().put("operation", "manualSet"))
        )

    private fun networkStatus(): DeviceNetworkStatus = DeviceNetworkStatus(
        ip = "192.168.1.44",
        macAddress = "AA:BB:CC:DD:EE:FF",
        wifiModeCode = 1,
        wifiMode = DeviceNetworkWifiMode.CLIENT,
        stationEnabled = true,
        setupApEnabled = false,
        clientConnected = true,
        setupApActive = false,
        uptimeMs = 10L,
        client = DeviceNetworkClientStatus(
            enabled = true,
            configured = true,
            ssid = "AquaLight Test",
            bssidConfigured = false,
            channel = 1,
            connected = true,
            state = DeviceNetworkClientState.GOT_IP,
            wifiStatus = 3,
            ip = "192.168.1.44",
            gateway = "192.168.1.1",
            subnet = "255.255.255.0",
            dns = "192.168.1.1",
            rssi = -50,
            lastWifiEvent = 0,
            lastDisconnectReason = 0,
            lastDisconnectReasonName = DeviceNetworkDisconnectReason.NONE,
            lastDisconnectAgeMs = 0L,
            lastGotIpAgeMs = 0L,
            nextRetryRemainingMs = 0L,
            connectionInProgress = false
        ),
        setupAp = DeviceNetworkSetupApStatus(
            enabled = false,
            active = false,
            ssid = "",
            ip = "",
            stationCount = 0
        ),
        discovery = DeviceNetworkDiscoveryStatus(
            ready = true,
            port = 10_888,
            broadcastIp = "192.168.1.255",
            currentIp = "192.168.1.44",
            payloadSize = 300,
            lastRefreshMs = 0L,
            lastPacketRejectedMs = 0L,
            rejectedPacketCount = 0L
        ),
        runtime = DeviceNetworkRuntimeTransport(
            transport = "websocket",
            wsPort = 80,
            wsPath = "/aql/v1/ws",
            wsProtocol = "aql.ws.v1",
            wsProtocolVersion = 1
        )
    )

    private companion object {
        val DEVICE_A = DeviceUid("AQL-STATE-A")
        val DEVICE_B = DeviceUid("AQL-STATE-B")
    }
}

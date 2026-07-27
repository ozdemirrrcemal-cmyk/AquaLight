package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceMenuAccessOperationsTest {

    @Test
    fun `fresh LAN proof returns typed available result`() = runTest {
        val snapshot = snapshot(
            state = DeviceConnectionState(
                onlineState = DeviceOnlineState.ONLINE_LAN,
                lastUdpSeenElapsedMillis = 1_000L
            ),
            withRuntimeEndpoint = false
        )
        val port = FakeDeviceMenuRuntimePort(snapshot = snapshot)
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { 1_000L }
        )

        val result = operations.resolve(snapshot.deviceUid.value)

        val available = result as DeviceMenuAccessResult.Available
        assertEquals(snapshot.deviceUid.value, available.deviceUid)
        assertEquals("AquaLight One", available.title)
        assertEquals(OwnerDeviceFamily.LIGHT, available.family)
        assertEquals(1, port.refreshVisibleCalls)
        assertEquals(1, port.refreshNowCalls)
    }

    @Test
    fun `unavailable local network returns immediately without runtime work`() = runTest {
        val snapshot = snapshot()
        val port = FakeDeviceMenuRuntimePort(
            snapshot = snapshot,
            localNetworkAvailable = false
        )
        val operations = DefaultDeviceMenuAccessOperations(port)

        val result = operations.resolve(snapshot.deviceUid.value)

        val unavailable = result as DeviceMenuAccessResult.Unavailable
        assertEquals(DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE, unavailable.reason)
        assertEquals(0, port.connectCalls)
        assertEquals(0, port.refreshNowCalls)
        assertEquals(0, port.requestNetworkStatusCalls)
    }

    @Test
    fun `definitive offline snapshot does not wait for menu timeout`() = runTest {
        val snapshot = snapshot(
            state = DeviceConnectionState(onlineState = DeviceOnlineState.OFFLINE)
        )
        val port = FakeDeviceMenuRuntimePort(snapshot = snapshot)
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        val startedAt = testScheduler.currentTime
        val result = operations.resolve(snapshot.deviceUid.value)

        val unavailable = result as DeviceMenuAccessResult.Unavailable
        assertEquals(DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE, unavailable.reason)
        assertEquals(startedAt, testScheduler.currentTime)
        assertEquals(0, port.connectCalls)
        assertEquals(0, port.refreshNowCalls)
    }

    @Test
    fun `matching successful runtime response records canonical proof before opening`() = runTest {
        val snapshot = snapshot(
            state = DeviceConnectionState(onlineState = DeviceOnlineState.CONNECTING_WS)
        )
        val authenticated = AqlWsConnectionState.Authenticated(
            deviceUid = snapshot.deviceUid,
            authenticatedAtMillis = 100L
        )
        val port = FakeDeviceMenuRuntimePort(snapshot = snapshot).apply {
            currentRuntimeState = authenticated
            responseOnNetworkStatusRequest = successfulResponse(
                id = REQUEST_ID,
                module = "",
                action = ""
            )
        }
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        val result = operations.resolve(snapshot.deviceUid.value)

        assertTrue(result is DeviceMenuAccessResult.Available)
        assertEquals(1, port.requestNetworkStatusCalls)
        assertEquals(1, port.recordControlProofCalls)
        assertEquals(DeviceOnlineState.AUTHENTICATED, port.snapshot().connectionState.onlineState)
    }

    @Test
    fun `concurrent requests for one device share one runtime proof`() = runTest {
        val snapshot = snapshot(
            state = DeviceConnectionState(onlineState = DeviceOnlineState.CONNECTING_WS)
        )
        val requestGate = CompletableDeferred<Unit>()
        val port = FakeDeviceMenuRuntimePort(snapshot = snapshot).apply {
            currentRuntimeState = AqlWsConnectionState.Authenticated(
                deviceUid = snapshot.deviceUid,
                authenticatedAtMillis = 100L
            )
            responseOnNetworkStatusRequest = successfulResponse(
                id = REQUEST_ID,
                module = "",
                action = ""
            )
            networkStatusRequestGate = requestGate
        }
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        val first = async { operations.resolve(snapshot.deviceUid.value) }
        runCurrent()
        val second = async { operations.resolve(snapshot.deviceUid.value) }
        runCurrent()

        assertEquals(1, port.requestNetworkStatusCalls)
        requestGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(first.await() is DeviceMenuAccessResult.Available)
        assertTrue(second.await() is DeviceMenuAccessResult.Available)
        assertEquals(1, port.requestNetworkStatusCalls)
        assertEquals(1, port.recordControlProofCalls)
    }

    @Test
    fun `authentication required ends verification without proof request`() = runTest {
        val snapshot = snapshot(
            state = DeviceConnectionState(onlineState = DeviceOnlineState.CONNECTING_WS)
        )
        val port = FakeDeviceMenuRuntimePort(snapshot = snapshot).apply {
            currentRuntimeState = AqlWsConnectionState.AuthRequired(
                deviceUid = snapshot.deviceUid,
                message = "pairing required"
            )
        }
        val operations = DefaultDeviceMenuAccessOperations(port)

        val result = operations.resolve(snapshot.deviceUid.value)

        val unavailable = result as DeviceMenuAccessResult.Unavailable
        assertEquals(DeviceMenuUnavailableReason.AUTHENTICATION_REQUIRED, unavailable.reason)
        assertEquals(0, port.requestNetworkStatusCalls)
    }

    @Test
    fun `stalled authentication is bounded by commercial menu budget`() = runTest {
        val snapshot = snapshot(
            state = DeviceConnectionState(onlineState = DeviceOnlineState.CONNECTING_WS)
        )
        val port = FakeDeviceMenuRuntimePort(snapshot = snapshot).apply {
            currentRuntimeState = AqlWsConnectionState.Connecting(
                deviceUid = snapshot.deviceUid,
                url = "ws://device.test/ws"
            )
        }
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        val result = operations.resolve(snapshot.deviceUid.value)

        val unavailable = result as DeviceMenuAccessResult.Unavailable
        assertEquals(DeviceMenuUnavailableReason.VERIFICATION_TIMED_OUT, unavailable.reason)
        assertTrue(testScheduler.currentTime <= 2_500L)
        assertEquals(1, port.connectCalls)
    }

    @Test
    fun `blank uid is rejected before repository access`() = runTest {
        val port = FakeDeviceMenuRuntimePort(snapshot = null)
        val operations = DefaultDeviceMenuAccessOperations(port)

        val result = operations.resolve("   ")

        val unavailable = result as DeviceMenuAccessResult.Unavailable
        assertEquals(DeviceMenuUnavailableReason.INVALID_DEVICE_UID, unavailable.reason)
        assertEquals(0, port.currentDeviceCalls)
    }

    @Test
    fun `runtime proof accepts omitted command metadata but rejects contradictions`() {
        val deviceUid = DeviceUid("device-proof")
        val compatible = AqlWsEvent.Message(
            deviceUid = deviceUid,
            parsed = successfulResponse(
                id = REQUEST_ID,
                module = "",
                action = ""
            )
        )
        val contradictory = AqlWsEvent.Message(
            deviceUid = deviceUid,
            parsed = successfulResponse(
                id = REQUEST_ID,
                module = AqlWsContract.MODULE_NETWORK,
                action = "unexpected.action"
            )
        )

        assertTrue(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = compatible,
                requestedDeviceUid = deviceUid,
                expectedRequestId = REQUEST_ID
            )
        )
        assertFalse(
            DeviceMenuRuntimeProofPolicy.accepts(
                event = contradictory,
                requestedDeviceUid = deviceUid,
                expectedRequestId = REQUEST_ID
            )
        )
    }

    private fun snapshot(
        state: DeviceConnectionState = DeviceConnectionState(),
        withRuntimeEndpoint: Boolean = true
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid("AQL-WPE-336172"),
                customName = "AquaLight One"
            ),
            product = DeviceProduct(family = DeviceFamily.LIGHT),
            endpoint = if (withRuntimeEndpoint) {
                DeviceRuntimeEndpoint(
                    ip = "192.168.1.44",
                    wsPort = 80
                )
            } else {
                DeviceRuntimeEndpoint()
            },
            connectionState = state
        )
    }

    private class FakeDeviceMenuRuntimePort(
        snapshot: DeviceSnapshot?,
        private val localNetworkAvailable: Boolean = true
    ) : DeviceMenuRuntimePort {
        private val snapshotFlow = MutableStateFlow(snapshot)
        private val connectionStateFlow = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        private val eventFlow = MutableSharedFlow<AqlWsEvent>(
            replay = 1,
            extraBufferCapacity = 8
        )

        var currentRuntimeState: AqlWsConnectionState? = null
            set(value) {
                field = value
                if (value != null) connectionStateFlow.value = value
            }
        var connectSucceeds: Boolean = true
        var responseOnNetworkStatusRequest: AqlWsIncomingMessage.Response? = null
        var networkStatusRequestGate: CompletableDeferred<Unit>? = null

        var currentDeviceCalls = 0
        var refreshVisibleCalls = 0
        var refreshNowCalls = 0
        var connectCalls = 0
        var requestNetworkStatusCalls = 0
        var recordControlProofCalls = 0

        fun snapshot(): DeviceSnapshot = requireNotNull(snapshotFlow.value)

        override fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? {
            currentDeviceCalls += 1
            return snapshotFlow.value?.takeIf { it.deviceUid == deviceUid }
        }

        override fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> = snapshotFlow

        override fun isLocalNetworkAvailable(): Boolean = localNetworkAvailable

        override fun refreshVisibleDevices(localNetworkAvailable: Boolean) {
            refreshVisibleCalls += 1
        }

        override suspend fun refreshNow() {
            refreshNowCalls += 1
        }

        override fun runtimeConnectionStates(): Flow<AqlWsConnectionState> = connectionStateFlow

        override fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? {
            return currentRuntimeState
        }

        override fun connectRuntime(deviceUid: DeviceUid): Boolean {
            connectCalls += 1
            return connectSucceeds
        }

        override fun runtimeEvents(): Flow<AqlWsEvent> = eventFlow

        override suspend fun requestNetworkStatus(deviceUid: DeviceUid): String? {
            requestNetworkStatusCalls += 1
            networkStatusRequestGate?.await()
            val response = responseOnNetworkStatusRequest ?: return REQUEST_ID
            eventFlow.tryEmit(
                AqlWsEvent.Message(
                    deviceUid = deviceUid,
                    parsed = response
                )
            )
            return REQUEST_ID
        }

        override fun recordControlProof(deviceUid: DeviceUid): DeviceSnapshot? {
            recordControlProofCalls += 1
            val current = snapshotFlow.value ?: return null
            val updated = current.copy(
                connectionState = current.connectionState.copy(
                    onlineState = DeviceOnlineState.AUTHENTICATED,
                    lastControlProofElapsedMillis = 1L
                )
            )
            snapshotFlow.value = updated
            return updated
        }
    }

    private companion object {
        const val REQUEST_ID = "request-network-status"

        fun successfulResponse(
            id: String,
            module: String,
            action: String
        ): AqlWsIncomingMessage.Response {
            return AqlWsIncomingMessage.Response(
                id = id,
                type = "response",
                module = module,
                action = action,
                data = JSONObject(),
                ok = true,
                statusCode = 200
            )
        }
    }
}

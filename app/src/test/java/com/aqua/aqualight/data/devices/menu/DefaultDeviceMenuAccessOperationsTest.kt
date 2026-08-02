package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceMenuAccessOperationsTest {

    @Test
    fun `fresh UDP proof without authenticated runtime endpoint is rejected`() = runTest {
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

        val unavailable = result as DeviceMenuAccessResult.Unavailable
        assertEquals(
            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN,
            unavailable.reason
        )
        assertEquals(1, port.refreshVisibleCalls)
        assertEquals(1, port.refreshNowCalls)
        assertEquals(0, port.connectCalls)
        assertEquals(0, port.requestNetworkStatusCalls)
        assertEquals(0, port.recordControlProofCalls)
    }

    @Test
    fun `discovered endpoint still requires authenticated runtime proof`() = runTest {
        val udpSnapshot = snapshot(
            state = DeviceConnectionState(
                onlineState = DeviceOnlineState.ONLINE_LAN,
                lastUdpSeenElapsedMillis = 1_000L
            ),
            withRuntimeEndpoint = false
        )
        val runtimeSnapshot = snapshot(
            state = DeviceConnectionState(onlineState = DeviceOnlineState.CONNECTING_WS)
        )
        val port = FakeDeviceMenuRuntimePort(snapshot = udpSnapshot).apply {
            snapshotAfterRefresh = runtimeSnapshot
            currentRuntimeState = AqlWsConnectionState.Authenticated(
                deviceUid = runtimeSnapshot.deviceUid,
                authenticatedAtMillis = 100L
            )
            livenessProofSucceeds = true
        }
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        val result = operations.resolve(udpSnapshot.deviceUid.value)

        assertTrue(result is DeviceMenuAccessResult.Available)
        assertEquals(1, port.requestNetworkStatusCalls)
        assertEquals(1, port.recordControlProofCalls)
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
    fun `correlated successful runtime response records canonical proof before opening`() = runTest {
        val snapshot = snapshot(
            state = DeviceConnectionState(onlineState = DeviceOnlineState.CONNECTING_WS)
        )
        val authenticated = AqlWsConnectionState.Authenticated(
            deviceUid = snapshot.deviceUid,
            authenticatedAtMillis = 100L
        )
        val port = FakeDeviceMenuRuntimePort(snapshot = snapshot).apply {
            currentRuntimeState = authenticated
            livenessProofSucceeds = true
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
            livenessProofSucceeds = true
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
        var currentRuntimeState: AqlWsConnectionState? = null
            set(value) {
                field = value
                if (value != null) connectionStateFlow.value = value
            }
        var connectSucceeds: Boolean = true
        var snapshotAfterRefresh: DeviceSnapshot? = null
        var livenessProofSucceeds: Boolean = true
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
            snapshotAfterRefresh?.let { snapshotFlow.value = it }
        }

        override fun runtimeConnectionStates(): Flow<AqlWsConnectionState> = connectionStateFlow

        override fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? {
            return currentRuntimeState
        }

        override fun connectRuntime(deviceUid: DeviceUid): Boolean {
            connectCalls += 1
            return connectSucceeds
        }

        override suspend fun proveCurrentLiveness(deviceUid: DeviceUid): Boolean {
            requestNetworkStatusCalls += 1
            networkStatusRequestGate?.await()
            val current = snapshotFlow.value
            if (!livenessProofSucceeds || current == null) return false

            recordControlProofCalls += 1
            snapshotFlow.value = current.copy(
                connectionState = current.connectionState.copy(
                    onlineState = DeviceOnlineState.AUTHENTICATED,
                    lastControlProofElapsedMillis = 1L
                )
            )
            return true
        }
    }

}

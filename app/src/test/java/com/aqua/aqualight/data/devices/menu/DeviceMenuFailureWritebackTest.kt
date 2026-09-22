package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMenuFailureWritebackTest {

    @Test
    fun `failed control proof with canonical offline state returns offline reason`() = runTest {
        val snapshot = snapshot(
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED
            )
        )
        val port = NoResponsePort(snapshot, DeviceOnlineState.OFFLINE)
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        val result = operations.resolve(snapshot.deviceUid.value)

        assertTrue(result is DeviceMenuAccessResult.Unavailable)
        assertEquals(
            DeviceMenuUnavailableReason.DEVICE_OFFLINE,
            (result as DeviceMenuAccessResult.Unavailable).reason
        )
        assertEquals(1, port.controlFailureCalls)
        assertEquals(
            DeviceOnlineState.OFFLINE,
            port.currentDevice(snapshot.deviceUid)?.connectionState?.onlineState
        )
    }

    @Test
    fun `failed control proof with canonical LAN presence retains unresponsive reason`() = runTest {
        val snapshot = snapshot(
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED
            )
        )
        val port = NoResponsePort(snapshot, DeviceOnlineState.ONLINE_LAN)
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        val result = operations.resolve(snapshot.deviceUid.value)

        assertTrue(result is DeviceMenuAccessResult.Unavailable)
        assertEquals(
            DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE,
            (result as DeviceMenuAccessResult.Unavailable).reason
        )
        assertEquals(1, port.controlFailureCalls)
        assertEquals(
            DeviceOnlineState.ONLINE_LAN,
            port.currentDevice(snapshot.deviceUid)?.connectionState?.onlineState
        )
    }

    @Test
    fun `failed control proof with canonical stale presence returns unverified reason`() = runTest {
        val snapshot = snapshot(
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED
            )
        )
        val port = NoResponsePort(snapshot, DeviceOnlineState.STALE)
        val operations = DefaultDeviceMenuAccessOperations(
            runtimePort = port,
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        val result = operations.resolve(snapshot.deviceUid.value)

        assertTrue(result is DeviceMenuAccessResult.Unavailable)
        assertEquals(
            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN,
            (result as DeviceMenuAccessResult.Unavailable).reason
        )
        assertEquals(1, port.controlFailureCalls)
        assertEquals(
            DeviceOnlineState.STALE,
            port.currentDevice(snapshot.deviceUid)?.connectionState?.onlineState
        )
    }

    private fun snapshot(connectionState: DeviceConnectionState) = DeviceSnapshot(
        identity = DeviceIdentity(uid = DeviceUid("device-no-response")),
        product = DeviceProduct(),
        endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.90", wsPort = 80),
        connectionState = connectionState
    )

    private class NoResponsePort(
        snapshot: DeviceSnapshot,
        private val failureState: DeviceOnlineState
    ) : DeviceMenuRuntimePort {
        private val snapshotFlow = MutableStateFlow(snapshot)
        private val authenticatedState = AqlWsConnectionState.Authenticated(
            deviceUid = snapshot.deviceUid,
            authenticatedAtMillis = 100L
        )

        var controlFailureCalls = 0

        override fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? {
            return snapshotFlow.value.takeIf { current -> current.deviceUid == deviceUid }
        }

        override fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> = snapshotFlow

        override fun isLocalNetworkAvailable(): Boolean = true

        override fun refreshVisibleDevices(localNetworkAvailable: Boolean) = Unit

        override suspend fun refreshNow() = Unit

        override fun runtimeConnectionStates(): Flow<AqlWsConnectionState> {
            return MutableStateFlow(authenticatedState)
        }

        override fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState {
            return authenticatedState
        }

        override fun connectRuntime(deviceUid: DeviceUid): Boolean = true

        override suspend fun proveCurrentLiveness(deviceUid: DeviceUid): Boolean = false

        override fun recordControlFailure(deviceUid: DeviceUid): DeviceSnapshot {
            controlFailureCalls += 1
            val current = snapshotFlow.value
            val updated = current.copy(
                connectionState = current.connectionState.copy(
                    onlineState = failureState
                )
            )
            snapshotFlow.value = updated
            return updated
        }
    }
}

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
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMenuFailureWritebackTest {

    @Test
    fun `failed control proof publishes offline before unavailable result`() = runTest {
        val snapshot = DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid("device-no-response")),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.90", wsPort = 80),
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED
            )
        )
        val port = NoResponsePort(snapshot)
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
            DeviceOnlineState.OFFLINE,
            port.currentDevice(snapshot.deviceUid)?.connectionState?.onlineState
        )
    }

    private class NoResponsePort(snapshot: DeviceSnapshot) : DeviceMenuRuntimePort {
        private val snapshotFlow = MutableStateFlow(snapshot)
        private val eventFlow = MutableSharedFlow<AqlWsEvent>()
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

        override fun runtimeEvents(): Flow<AqlWsEvent> = eventFlow

        override suspend fun requestNetworkStatus(deviceUid: DeviceUid): String? = null

        override fun recordControlProof(deviceUid: DeviceUid): DeviceSnapshot? = null

        override fun recordControlFailure(deviceUid: DeviceUid): DeviceSnapshot {
            controlFailureCalls += 1
            val current = snapshotFlow.value
            val offline = current.copy(
                connectionState = current.connectionState.copy(
                    onlineState = DeviceOnlineState.OFFLINE
                )
            )
            snapshotFlow.value = offline
            return offline
        }
    }
}

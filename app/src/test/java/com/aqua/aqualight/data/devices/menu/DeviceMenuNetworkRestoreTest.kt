package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
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

class DeviceMenuNetworkRestoreTest {

    @Test
    fun `stale local network offline snapshot is verified when phone network has returned`() =
        runTest {
            val deviceUid = DeviceUid("device-network-restored")
            val snapshot = DeviceSnapshot(
                identity = DeviceIdentity(
                    uid = deviceUid,
                    customName = "AquaLight Dosing"
                ),
                product = DeviceProduct(family = DeviceFamily.DOSING),
                endpoint = DeviceRuntimeEndpoint(
                    ip = "192.168.1.80",
                    wsPort = 80
                ),
                connectionState = DeviceConnectionState(
                    onlineState = DeviceOnlineState.LOCAL_NETWORK_OFFLINE
                )
            )
            val port = RestoredNetworkPort(snapshot)
            val operations = DefaultDeviceMenuAccessOperations(
                runtimePort = port,
                elapsedRealtimeMillis = { testScheduler.currentTime }
            )

            val result = operations.resolve(deviceUid.value)

            assertTrue(result is DeviceMenuAccessResult.Available)
            assertEquals(1, port.refreshVisibleCalls)
            assertEquals(1, port.requestNetworkStatusCalls)
            assertEquals(1, port.recordControlProofCalls)
        }

    private class RestoredNetworkPort(
        snapshot: DeviceSnapshot
    ) : DeviceMenuRuntimePort {
        private val snapshotFlow = MutableStateFlow(snapshot)
        private val authenticatedState = AqlWsConnectionState.Authenticated(
            deviceUid = snapshot.deviceUid,
            authenticatedAtMillis = 100L
        )

        var refreshVisibleCalls = 0
        var requestNetworkStatusCalls = 0
        var recordControlProofCalls = 0

        override fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? {
            return snapshotFlow.value.takeIf { current -> current.deviceUid == deviceUid }
        }

        override fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> = snapshotFlow

        override fun isLocalNetworkAvailable(): Boolean = true

        override fun refreshVisibleDevices(localNetworkAvailable: Boolean) {
            refreshVisibleCalls += 1
        }

        override suspend fun refreshNow() = Unit

        override fun runtimeConnectionStates(): Flow<AqlWsConnectionState> {
            return MutableStateFlow(authenticatedState)
        }

        override fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState {
            return authenticatedState
        }

        override fun connectRuntime(deviceUid: DeviceUid): Boolean = true

        override suspend fun proveCurrentLiveness(deviceUid: DeviceUid): Boolean {
            requestNetworkStatusCalls += 1
            recordControlProofCalls += 1
            val current = snapshotFlow.value
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

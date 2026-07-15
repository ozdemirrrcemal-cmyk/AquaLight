package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceMenuAccessOperationsTest {

    @Test
    fun `fresh LAN proof returns typed available result`() = runTest {
        val snapshot = snapshot(lastUdpSeenAtMillis = Long.MAX_VALUE)
        val port = FakeDeviceMenuRuntimePort(snapshot = snapshot)
        val operations = DefaultDeviceMenuAccessOperations(port)

        val result = operations.resolve(snapshot.deviceUid.value)

        val available = result as DeviceMenuAccessResult.Available
        assertEquals(snapshot.deviceUid.value, available.deviceUid)
        assertEquals("AquaLight One", available.title)
        assertEquals(OwnerDeviceFamily.LIGHT, available.family)
        assertEquals(1, port.refreshVisibleCalls)
        assertEquals(1, port.refreshNowCalls)
    }

    @Test
    fun `unavailable local network returns typed reason without runtime connect`() = runTest {
        val snapshot = snapshot(lastUdpSeenAtMillis = null)
        val port = FakeDeviceMenuRuntimePort(
            snapshot = snapshot,
            localNetworkAvailable = false
        )
        val operations = DefaultDeviceMenuAccessOperations(port)

        val result = operations.resolve(snapshot.deviceUid.value)

        val unavailable = result as DeviceMenuAccessResult.Unavailable
        assertEquals("AquaLight One", unavailable.title)
        assertEquals(
            DeviceMenuUnavailableReason.LOCAL_NETWORK_UNAVAILABLE,
            unavailable.reason
        )
        assertEquals(0, port.connectCalls)
        assertEquals(0, port.refreshNowCalls)
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

    private fun snapshot(lastUdpSeenAtMillis: Long?): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid("AQL-WPE-336172"),
                customName = "AquaLight One"
            ),
            product = DeviceProduct(family = DeviceFamily.LIGHT),
            connectionState = DeviceConnectionState(
                lastUdpSeenAtMillis = lastUdpSeenAtMillis
            )
        )
    }

    private class FakeDeviceMenuRuntimePort(
        private val snapshot: DeviceSnapshot?,
        private val localNetworkAvailable: Boolean = true
    ) : DeviceMenuRuntimePort {
        private val snapshotFlow = MutableStateFlow(snapshot)

        var currentDeviceCalls = 0
        var refreshVisibleCalls = 0
        var refreshNowCalls = 0
        var connectCalls = 0

        override fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? {
            currentDeviceCalls += 1
            return snapshot?.takeIf { it.deviceUid == deviceUid }
        }

        override fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> = snapshotFlow

        override fun isLocalNetworkAvailable(): Boolean = localNetworkAvailable

        override fun refreshVisibleDevices(localNetworkAvailable: Boolean) {
            refreshVisibleCalls += 1
        }

        override suspend fun refreshNow() {
            refreshNowCalls += 1
        }

        override fun runtimeConnectionStates(): Flow<AqlWsConnectionState>? = null

        override fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? = null

        override fun connectRuntime(deviceUid: DeviceUid): Boolean {
            connectCalls += 1
            return false
        }

        override fun runtimeEvents(): Flow<AqlWsEvent>? = null

        override suspend fun requestNetworkStatus(deviceUid: DeviceUid): String? = null
    }
}

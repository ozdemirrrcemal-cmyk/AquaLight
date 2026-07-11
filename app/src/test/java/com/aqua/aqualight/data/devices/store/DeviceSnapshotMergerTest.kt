package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSnapshotMergerTest {

    @Test
    fun udpPresence_doesNotDowngradeAuthenticatedRuntime() {
        val previous = snapshot(
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED,
                lastWsConnectedAtMillis = 90_000L,
                lastAuthenticatedAtMillis = 91_000L,
                runtimeConnected = true,
                runtimeAuthenticated = true
            )
        )
        val incomingUdp = snapshot(
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.ONLINE_LAN,
                lastUdpSeenAtMillis = 100_000L
            )
        )

        val merged = DeviceSnapshotMerger.merge(previous, incomingUdp)

        assertEquals(
            DeviceOnlineState.AUTHENTICATED,
            merged.connectionState.onlineState
        )
        assertTrue(merged.connectionState.runtimeConnected)
        assertTrue(merged.connectionState.runtimeAuthenticated)
        assertEquals(
            100_000L,
            merged.connectionState.lastUdpSeenAtMillis
        )
    }

    @Test
    fun udpPresence_doesNotEraseAuthRequiredRuntimeState() {
        val previous = snapshot(
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTH_REQUIRED,
                runtimeConnected = true,
                runtimeAuthenticated = false,
                lastErrorMessage = "token rejected"
            )
        )
        val incomingUdp = snapshot(
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.ONLINE_LAN,
                lastUdpSeenAtMillis = 100_000L
            )
        )

        val merged = DeviceSnapshotMerger.merge(previous, incomingUdp)

        assertEquals(
            DeviceOnlineState.AUTH_REQUIRED,
            merged.connectionState.onlineState
        )
        assertTrue(merged.connectionState.runtimeConnected)
        assertEquals(
            "token rejected",
            merged.connectionState.lastErrorMessage
        )
    }

    private fun snapshot(
        connectionState: DeviceConnectionState
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid("device-1")),
            product = DeviceProduct(),
            connectionState = connectionState
        )
    }
}

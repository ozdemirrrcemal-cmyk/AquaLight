package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStatusAggregatorTest {

    private val policy = DeviceHeartbeatPolicy(
        udpFreshMillis = 20_000L,
        udpStaleMillis = 35_000L,
        wsFreshMillis = 20_000L,
        authFreshMillis = 60_000L
    )
    private val aggregator = DeviceStatusAggregator(policy)

    @Test
    fun authenticatedRuntime_isCanonicalOnlineState() {
        val state = DeviceConnectionState(
            onlineState = DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            runtimeConnected = true,
            runtimeAuthenticated = true
        )

        assertEquals(
            DeviceOnlineState.AUTHENTICATED,
            aggregator.resolve(state, nowMillis = 100_000L)
        )
    }

    @Test
    fun connectedRuntimeWithoutAuthentication_isNotUsableYet() {
        val state = DeviceConnectionState(
            runtimeConnected = true,
            runtimeAuthenticated = false
        )

        assertEquals(
            DeviceOnlineState.CONNECTING_WS,
            aggregator.resolve(state, nowMillis = 100_000L)
        )
    }

    @Test
    fun authRequired_isPreservedWhileSocketIsReachable() {
        val state = DeviceConnectionState(
            onlineState = DeviceOnlineState.AUTH_REQUIRED,
            runtimeConnected = true,
            runtimeAuthenticated = false
        )

        assertEquals(
            DeviceOnlineState.AUTH_REQUIRED,
            aggregator.resolve(state, nowMillis = 100_000L)
        )
    }

    @Test
    fun freshUdpWithoutRuntime_isLanEvidenceOnly() {
        val state = DeviceConnectionState(
            lastUdpSeenAtMillis = 95_000L
        )

        assertEquals(
            DeviceOnlineState.ONLINE_LAN,
            aggregator.resolve(state, nowMillis = 100_000L)
        )
    }

    @Test
    fun disconnectedRuntimeWithinGrace_isStaleNotOnline() {
        val state = DeviceConnectionState(
            onlineState = DeviceOnlineState.STALE,
            lastWsConnectedAtMillis = 95_000L,
            lastAuthenticatedAtMillis = 90_000L,
            runtimeConnected = false,
            runtimeAuthenticated = false
        )

        assertEquals(
            DeviceOnlineState.STALE,
            aggregator.resolve(state, nowMillis = 100_000L)
        )
    }

    @Test
    fun expiredPresenceEvidence_isOffline() {
        val state = DeviceConnectionState(
            lastUdpSeenAtMillis = 10_000L,
            lastWsConnectedAtMillis = 10_000L,
            lastAuthenticatedAtMillis = 10_000L
        )

        assertEquals(
            DeviceOnlineState.OFFLINE,
            aggregator.resolve(state, nowMillis = 100_000L)
        )
    }

    @Test
    fun localNetworkLoss_overridesLiveRuntime() {
        val state = DeviceConnectionState(
            runtimeConnected = true,
            runtimeAuthenticated = true
        )

        assertEquals(
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            aggregator.resolve(
                state = state,
                nowMillis = 100_000L,
                localNetworkAvailable = false
            )
        )
    }
}

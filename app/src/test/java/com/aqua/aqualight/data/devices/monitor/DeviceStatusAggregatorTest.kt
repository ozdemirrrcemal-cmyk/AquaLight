package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStatusAggregatorTest {

    private val aggregator = DeviceStatusAggregator(
        DeviceHeartbeatPolicy(
            udpFreshMillis = 1_000L,
            udpStaleMillis = 2_000L,
            wsFreshMillis = 1_000L,
            authenticationBootstrapFreshMillis = 500L,
            runtimeProofFreshMillis = 3_000L
        )
    )

    @Test
    fun `fresh decoded runtime proof remains authenticated`() {
        assertEquals(
            DeviceOnlineState.AUTHENTICATED,
            aggregator.resolve(
                state = DeviceConnectionState(
                    lastAuthenticatedElapsedMillis = 1_000L,
                    lastRuntimeMessageElapsedMillis = 1_500L
                ),
                nowElapsedMillis = 4_000L
            )
        )
    }

    @Test
    fun `fresh correlated control proof remains authenticated`() {
        assertEquals(
            DeviceOnlineState.AUTHENTICATED,
            aggregator.resolve(
                state = DeviceConnectionState(
                    lastControlProofElapsedMillis = 2_000L
                ),
                nowElapsedMillis = 4_500L
            )
        )
    }

    @Test
    fun `authentication alone is only a short bootstrap proof`() {
        assertEquals(
            DeviceOnlineState.ONLINE_LAN,
            aggregator.resolve(
                state = DeviceConnectionState(
                    lastAuthenticatedElapsedMillis = 1_000L,
                    lastUdpSeenElapsedMillis = 1_800L
                ),
                nowElapsedMillis = 2_000L
            )
        )
    }

    @Test
    fun `connected runtime without authentication remains connecting`() {
        assertEquals(
            DeviceOnlineState.CONNECTING_WS,
            aggregator.resolve(
                state = DeviceConnectionState(
                    lastWsConnectedElapsedMillis = 1_000L
                ),
                nowElapsedMillis = 1_500L
            )
        )
    }

    @Test
    fun `authentication required is never promoted by fresh UDP`() {
        assertEquals(
            DeviceOnlineState.AUTH_REQUIRED,
            aggregator.resolve(
                state = DeviceConnectionState(
                    onlineState = DeviceOnlineState.AUTH_REQUIRED,
                    lastUdpSeenElapsedMillis = 1_000L
                ),
                nowElapsedMillis = 1_100L
            )
        )
    }

    @Test
    fun `local network loss overrides fresh runtime proof`() {
        assertEquals(
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            aggregator.resolve(
                state = DeviceConnectionState(
                    onlineState = DeviceOnlineState.AUTHENTICATED,
                    lastRuntimeMessageElapsedMillis = 1_000L,
                    lastUdpSeenElapsedMillis = 1_000L
                ),
                nowElapsedMillis = 1_100L,
                localNetworkAvailable = false
            )
        )
    }

    @Test
    fun `wall clock jumps do not change freshness decisions`() {
        assertEquals(
            DeviceOnlineState.AUTHENTICATED,
            aggregator.resolve(
                state = DeviceConnectionState(
                    lastRuntimeMessageAtMillis = Long.MAX_VALUE,
                    lastRuntimeMessageElapsedMillis = 5_000L
                ),
                nowElapsedMillis = 6_000L
            )
        )
    }
}

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
            authFreshMillis = 3_000L
        )
    )

    @Test
    fun `fresh authenticated runtime remains authenticated`() {
        assertEquals(
            DeviceOnlineState.AUTHENTICATED,
            aggregator.resolve(
                state = DeviceConnectionState(lastAuthenticatedAtMillis = 1_000L),
                nowMillis = 3_500L
            )
        )
    }

    @Test
    fun `connected runtime without authentication remains connecting`() {
        assertEquals(
            DeviceOnlineState.CONNECTING_WS,
            aggregator.resolve(
                state = DeviceConnectionState(lastWsConnectedAtMillis = 1_000L),
                nowMillis = 1_500L
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
                    lastUdpSeenAtMillis = 1_000L
                ),
                nowMillis = 1_100L
            )
        )
    }
}

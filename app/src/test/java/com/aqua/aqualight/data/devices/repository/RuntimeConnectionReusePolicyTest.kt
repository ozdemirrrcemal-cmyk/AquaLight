package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConnectionReusePolicyTest {

    private val requestedUid = DeviceUid("AQL-WPE-336172")

    @Test
    fun `authenticated session for same endpoint is reused`() {
        assertFalse(
            RuntimeConnectionReusePolicy.shouldReconnect(
                state = AqlWsConnectionState.Authenticated(
                    deviceUid = requestedUid,
                    authenticatedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid,
                endpointMatches = true
            )
        )
    }

    @Test
    fun `connection in progress for same endpoint is reused`() {
        assertFalse(
            RuntimeConnectionReusePolicy.shouldReconnect(
                state = AqlWsConnectionState.Connecting(
                    deviceUid = requestedUid,
                    url = "ws://192.168.1.20:81/ws"
                ),
                requestedDeviceUid = requestedUid,
                endpointMatches = true
            )
        )
        assertFalse(
            RuntimeConnectionReusePolicy.shouldReconnect(
                state = AqlWsConnectionState.Connected(
                    deviceUid = requestedUid,
                    url = "ws://192.168.1.20:81/ws",
                    connectedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid,
                endpointMatches = true
            )
        )
    }

    @Test
    fun `failed disconnected or changed endpoint reconnects`() {
        assertTrue(
            RuntimeConnectionReusePolicy.shouldReconnect(
                state = AqlWsConnectionState.Disconnected,
                requestedDeviceUid = requestedUid,
                endpointMatches = true
            )
        )
        assertTrue(
            RuntimeConnectionReusePolicy.shouldReconnect(
                state = AqlWsConnectionState.Failed(
                    deviceUid = requestedUid,
                    message = "network failure"
                ),
                requestedDeviceUid = requestedUid,
                endpointMatches = true
            )
        )
        assertTrue(
            RuntimeConnectionReusePolicy.shouldReconnect(
                state = AqlWsConnectionState.Authenticated(
                    deviceUid = requestedUid,
                    authenticatedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid,
                endpointMatches = false
            )
        )
    }
}

package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMenuAuthenticationPolicyTest {

    private val requestedUid = DeviceUid("AQL-WPE-336172")

    @Test
    fun `socket connected is not sufficient to open menu`() {
        assertFalse(
            DeviceMenuAuthenticationPolicy.accepts(
                state = AqlWsConnectionState.Connected(
                    deviceUid = requestedUid,
                    url = "ws://192.168.1.20:81/ws",
                    connectedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid,
                gateStartedAtMillis = 900L
            )
        )
    }

    @Test
    fun `fresh authentication for requested device opens menu`() {
        assertTrue(
            DeviceMenuAuthenticationPolicy.accepts(
                state = AqlWsConnectionState.Authenticated(
                    deviceUid = requestedUid,
                    authenticatedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid,
                gateStartedAtMillis = 900L
            )
        )
    }

    @Test
    fun `currently authenticated requested-device session opens without reconnecting`() {
        assertTrue(
            DeviceMenuAuthenticationPolicy.isActiveAuthenticatedSession(
                state = AqlWsConnectionState.Authenticated(
                    deviceUid = requestedUid,
                    authenticatedAtMillis = 1L
                ),
                requestedDeviceUid = requestedUid
            )
        )
    }

    @Test
    fun `connected or different-device session is not an authenticated current session`() {
        assertFalse(
            DeviceMenuAuthenticationPolicy.isActiveAuthenticatedSession(
                state = AqlWsConnectionState.Connected(
                    deviceUid = requestedUid,
                    url = "ws://192.168.1.20:81/ws",
                    connectedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid
            )
        )
        assertFalse(
            DeviceMenuAuthenticationPolicy.isActiveAuthenticatedSession(
                state = AqlWsConnectionState.Authenticated(
                    deviceUid = DeviceUid("AQL-OTHER"),
                    authenticatedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid
            )
        )
    }

    @Test
    fun `stale or different-device authentication is rejected`() {
        assertFalse(
            DeviceMenuAuthenticationPolicy.accepts(
                state = AqlWsConnectionState.Authenticated(
                    deviceUid = requestedUid,
                    authenticatedAtMillis = 899L
                ),
                requestedDeviceUid = requestedUid,
                gateStartedAtMillis = 900L
            )
        )
        assertFalse(
            DeviceMenuAuthenticationPolicy.accepts(
                state = AqlWsConnectionState.Authenticated(
                    deviceUid = DeviceUid("AQL-OTHER"),
                    authenticatedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid,
                gateStartedAtMillis = 900L
            )
        )
    }
}

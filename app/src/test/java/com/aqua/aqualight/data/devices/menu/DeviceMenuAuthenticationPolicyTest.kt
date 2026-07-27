package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMenuAuthenticationPolicyTest {

    private val requestedUid = DeviceUid("AQL-WPE-336172")

    @Test
    fun `socket connected is not sufficient to open menu`() {
        assertNull(
            DeviceMenuAuthenticationPolicy.classify(
                state = AqlWsConnectionState.Connected(
                    deviceUid = requestedUid,
                    url = "ws://192.168.1.20:81/ws",
                    connectedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid
            )
        )
    }

    @Test
    fun `fresh authentication for requested device opens menu`() {
        assertEquals(
            AuthenticationOutcome.Authenticated,
            DeviceMenuAuthenticationPolicy.classify(
                state = AqlWsConnectionState.Authenticated(
                    deviceUid = requestedUid,
                    authenticatedAtMillis = 1_000L
                ),
                requestedDeviceUid = requestedUid
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
    fun `authentication required is a terminal typed result`() {
        assertEquals(
            AuthenticationOutcome.AuthRequired,
            DeviceMenuAuthenticationPolicy.classify(
                state = AqlWsConnectionState.AuthRequired(
                    deviceUid = requestedUid,
                    message = "pairing required"
                ),
                requestedDeviceUid = requestedUid
            )
        )
    }

    @Test
    fun `connection failure is terminal only for requested device`() {
        assertEquals(
            AuthenticationOutcome.Failed,
            DeviceMenuAuthenticationPolicy.classify(
                state = AqlWsConnectionState.Failed(
                    deviceUid = requestedUid,
                    message = "connection failed"
                ),
                requestedDeviceUid = requestedUid
            )
        )
        assertNull(
            DeviceMenuAuthenticationPolicy.classify(
                state = AqlWsConnectionState.Failed(
                    deviceUid = DeviceUid("AQL-OTHER"),
                    message = "connection failed"
                ),
                requestedDeviceUid = requestedUid
            )
        )
    }
}

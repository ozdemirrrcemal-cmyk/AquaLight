package com.aqua.aqualight.ui.tabs.devices.route

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

package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeClosedEventPolicyTest {

    private val deviceUid = DeviceUid("AQL-WPE-336172")

    @Test
    fun `current disconnected session clears stale runtime proof`() {
        assertTrue(
            RuntimeClosedEventPolicy.shouldClearRuntimeProof(
                AqlWsConnectionState.Disconnected
            )
        )
    }

    @Test
    fun `delayed close from old socket cannot clear reconnecting session`() {
        assertFalse(
            RuntimeClosedEventPolicy.shouldClearRuntimeProof(
                AqlWsConnectionState.Connecting(
                    deviceUid = deviceUid,
                    url = "ws://192.168.1.20:81/ws"
                )
            )
        )
        assertFalse(
            RuntimeClosedEventPolicy.shouldClearRuntimeProof(
                AqlWsConnectionState.Connected(
                    deviceUid = deviceUid,
                    url = "ws://192.168.1.20:81/ws",
                    connectedAtMillis = 1_000L
                )
            )
        )
        assertFalse(
            RuntimeClosedEventPolicy.shouldClearRuntimeProof(
                AqlWsConnectionState.Authenticated(
                    deviceUid = deviceUid,
                    authenticatedAtMillis = 2_000L
                )
            )
        )
    }

    @Test
    fun `missing or separately failed session is not changed by close event`() {
        assertFalse(RuntimeClosedEventPolicy.shouldClearRuntimeProof(null))
        assertFalse(
            RuntimeClosedEventPolicy.shouldClearRuntimeProof(
                AqlWsConnectionState.Failed(
                    deviceUid = deviceUid,
                    message = "network failure"
                )
            )
        )
    }
}

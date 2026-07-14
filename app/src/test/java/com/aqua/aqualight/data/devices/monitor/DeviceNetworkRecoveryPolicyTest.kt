package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNetworkRecoveryPolicyTest {

    @Test
    fun `network return retries every recoverable non authenticated state`() {
        listOf(
            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.STALE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.ERROR
        ).forEach { state ->
            assertTrue(state.name, DeviceNetworkRecoveryPolicy.shouldRetry(state))
        }
    }

    @Test
    fun `network return does not interrupt authenticated or protected operations`() {
        listOf(
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING
        ).forEach { state ->
            assertFalse(state.name, DeviceNetworkRecoveryPolicy.shouldRetry(state))
        }
    }
}

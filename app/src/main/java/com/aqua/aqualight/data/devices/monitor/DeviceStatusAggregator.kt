package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * Converts low-level connectivity timestamps into one product-level device presence state.
 *
 * Runtime socket reachability and authenticated reachability are deliberately distinct. A TCP/
 * WebSocket connection alone is not permission to expose a commercial control surface.
 */
class DeviceStatusAggregator(
    private val policy: DeviceHeartbeatPolicy = DeviceHeartbeatPolicy()
) {

    fun resolve(
        state: DeviceConnectionState,
        nowMillis: Long = System.currentTimeMillis(),
        localNetworkAvailable: Boolean = true
    ): DeviceOnlineState {
        if (!localNetworkAvailable) return DeviceOnlineState.LOCAL_NETWORK_OFFLINE

        if (state.onlineState == DeviceOnlineState.AUTH_REQUIRED) {
            return DeviceOnlineState.AUTH_REQUIRED
        }

        val lastAuthenticatedAt = state.lastAuthenticatedAtMillis
        if (
            lastAuthenticatedAt != null &&
            nowMillis - lastAuthenticatedAt <= policy.authFreshMillis
        ) {
            return DeviceOnlineState.AUTHENTICATED
        }

        val lastWsConnectedAt = state.lastWsConnectedAtMillis
        if (
            lastWsConnectedAt != null &&
            nowMillis - lastWsConnectedAt <= policy.wsFreshMillis
        ) {
            return DeviceOnlineState.CONNECTING_WS
        }

        val lastUdpSeenAt = state.lastUdpSeenAtMillis
            ?: return DeviceOnlineState.UNKNOWN

        val ageMillis = nowMillis - lastUdpSeenAt
        return when {
            ageMillis <= policy.udpFreshMillis -> DeviceOnlineState.ONLINE_LAN
            ageMillis <= policy.udpStaleMillis -> DeviceOnlineState.STALE
            else -> DeviceOnlineState.OFFLINE
        }
    }
}

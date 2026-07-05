package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * Converts low-level connectivity timestamps into one product-level device presence state.
 *
 * Runtime socket and token handshakes are implementation details. This resolver only answers
 * whether the device is currently reachable on the local network.
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

        val lastRuntimeSeenAt = listOfNotNull(
            state.lastAuthenticatedAtMillis,
            state.lastWsConnectedAtMillis
        ).maxOrNull()

        if (lastRuntimeSeenAt != null && nowMillis - lastRuntimeSeenAt <= policy.wsFreshMillis) {
            return DeviceOnlineState.ONLINE_LAN
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

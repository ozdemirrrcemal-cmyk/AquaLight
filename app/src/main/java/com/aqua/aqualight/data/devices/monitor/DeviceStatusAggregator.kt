package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * Converts low-level connectivity evidence into one product-level device presence state.
 *
 * Authentication starts a secure session. Sustained Online state is renewed by decoded runtime
 * traffic or a correlated successful command response, so an old authentication timestamp cannot
 * disagree with a still-responsive device or keep a dead socket Online indefinitely.
 */
class DeviceStatusAggregator(
    private val policy: DeviceHeartbeatPolicy = DeviceHeartbeatPolicy()
) {

    fun resolve(
        state: DeviceConnectionState,
        nowElapsedMillis: Long = DeviceElapsedRealtimeClock.nowMillis(),
        localNetworkAvailable: Boolean = true
    ): DeviceOnlineState {
        if (!localNetworkAvailable) return DeviceOnlineState.LOCAL_NETWORK_OFFLINE

        when (state.onlineState) {
            DeviceOnlineState.AUTH_REQUIRED -> return DeviceOnlineState.AUTH_REQUIRED
            DeviceOnlineState.PROVISIONING -> return DeviceOnlineState.PROVISIONING
            DeviceOnlineState.OTA_UPDATING -> return DeviceOnlineState.OTA_UPDATING
            else -> Unit
        }

        val runtimeProofAt = state.latestRuntimeProofElapsedMillis
        if (
            runtimeProofAt != null &&
            isFresh(nowElapsedMillis, runtimeProofAt, policy.runtimeProofFreshMillis)
        ) {
            return DeviceOnlineState.AUTHENTICATED
        }

        val authenticatedAt = state.lastAuthenticatedElapsedMillis
        if (
            authenticatedAt != null &&
            isFresh(
                nowElapsedMillis,
                authenticatedAt,
                policy.authenticationBootstrapFreshMillis
            )
        ) {
            return DeviceOnlineState.AUTHENTICATED
        }

        val wsConnectedAt = state.lastWsConnectedElapsedMillis
        if (
            wsConnectedAt != null &&
            isFresh(nowElapsedMillis, wsConnectedAt, policy.wsFreshMillis)
        ) {
            return DeviceOnlineState.CONNECTING_WS
        }

        val lastUdpSeenAt = state.lastUdpSeenElapsedMillis
            ?: return DeviceOnlineState.UNKNOWN
        val ageMillis = elapsedAge(nowElapsedMillis, lastUdpSeenAt)

        return when {
            ageMillis <= policy.udpFreshMillis -> DeviceOnlineState.ONLINE_LAN
            ageMillis <= policy.udpStaleMillis -> DeviceOnlineState.STALE
            else -> DeviceOnlineState.OFFLINE
        }
    }

    private fun isFresh(nowElapsedMillis: Long, proofAtMillis: Long, freshnessMillis: Long): Boolean {
        return elapsedAge(nowElapsedMillis, proofAtMillis) <= freshnessMillis
    }

    private fun elapsedAge(nowElapsedMillis: Long, proofAtMillis: Long): Long {
        return (nowElapsedMillis - proofAtMillis).coerceAtLeast(0L)
    }
}

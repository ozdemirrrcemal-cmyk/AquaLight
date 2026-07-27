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
        val protectedState = protectedState(state)
        val runtimeState = runtimeState(state, nowElapsedMillis)

        return when {
            !localNetworkAvailable -> DeviceOnlineState.LOCAL_NETWORK_OFFLINE
            protectedState != null -> protectedState
            runtimeState != null -> runtimeState
            else -> udpState(state, nowElapsedMillis)
        }
    }

    private fun protectedState(state: DeviceConnectionState): DeviceOnlineState? {
        return when (state.onlineState) {
            DeviceOnlineState.AUTH_REQUIRED -> DeviceOnlineState.AUTH_REQUIRED
            DeviceOnlineState.PROVISIONING -> DeviceOnlineState.PROVISIONING
            DeviceOnlineState.OTA_UPDATING -> DeviceOnlineState.OTA_UPDATING
            else -> null
        }
    }

    private fun runtimeState(
        state: DeviceConnectionState,
        nowElapsedMillis: Long
    ): DeviceOnlineState? {
        val runtimeProofIsFresh = state.latestRuntimeProofElapsedMillis
            ?.let { proofAt -> isFresh(nowElapsedMillis, proofAt, policy.runtimeProofFreshMillis) }
            ?: false
        val authenticationBootstrapIsFresh = state.lastAuthenticatedElapsedMillis
            ?.let { authenticatedAt ->
                isFresh(
                    nowElapsedMillis = nowElapsedMillis,
                    proofAtMillis = authenticatedAt,
                    freshnessMillis = policy.authenticationBootstrapFreshMillis
                )
            }
            ?: false
        val webSocketIsFresh = state.lastWsConnectedElapsedMillis
            ?.let { connectedAt -> isFresh(nowElapsedMillis, connectedAt, policy.wsFreshMillis) }
            ?: false

        return when {
            runtimeProofIsFresh || authenticationBootstrapIsFresh -> {
                DeviceOnlineState.AUTHENTICATED
            }
            webSocketIsFresh -> DeviceOnlineState.CONNECTING_WS
            else -> null
        }
    }

    private fun udpState(
        state: DeviceConnectionState,
        nowElapsedMillis: Long
    ): DeviceOnlineState {
        return state.lastUdpSeenElapsedMillis?.let { lastUdpSeenAt ->
            val ageMillis = elapsedAge(nowElapsedMillis, lastUdpSeenAt)
            when {
                ageMillis <= policy.udpFreshMillis -> DeviceOnlineState.ONLINE_LAN
                ageMillis <= policy.udpStaleMillis -> DeviceOnlineState.STALE
                else -> DeviceOnlineState.OFFLINE
            }
        } ?: DeviceOnlineState.UNKNOWN
    }

    private fun isFresh(
        nowElapsedMillis: Long,
        proofAtMillis: Long,
        freshnessMillis: Long
    ): Boolean {
        return elapsedAge(nowElapsedMillis, proofAtMillis) <= freshnessMillis
    }

    private fun elapsedAge(nowElapsedMillis: Long, proofAtMillis: Long): Long {
        return (nowElapsedMillis - proofAtMillis).coerceAtLeast(0L)
    }
}

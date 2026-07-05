package com.aqua.aqualight.data.devices.monitor

/**
 * Central timing policy for device presence decisions.
 *
 * Firmware announces periodically and Android actively refreshes while the app process is alive.
 * These values keep online/offline decisions live without allowing UI screens to invent their own
 * timing rules.
 */
data class DeviceHeartbeatPolicy(
    val udpFreshMillis: Long = 15_000L,
    val udpStaleMillis: Long = 30_000L,
    val wsFreshMillis: Long = 10_000L,
    val authFreshMillis: Long = 20_000L
) {
    init {
        require(udpFreshMillis > 0L)
        require(udpStaleMillis >= udpFreshMillis)
        require(wsFreshMillis > 0L)
        require(authFreshMillis > 0L)
    }
}

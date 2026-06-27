package com.aqua.aqualight.data.devices.monitor

/**
 * Central timing policy for device presence decisions.
 *
 * Firmware announces periodically, but Android should actively refresh when foregrounded. Keeping
 * these values in one place prevents random timeout constants from spreading across UI code.
 */
data class DeviceHeartbeatPolicy(
    val udpFreshMillis: Long = 60_000L,
    val udpStaleMillis: Long = 120_000L,
    val wsFreshMillis: Long = 45_000L,
    val authFreshMillis: Long = 45_000L
) {
    init {
        require(udpFreshMillis > 0L)
        require(udpStaleMillis >= udpFreshMillis)
        require(wsFreshMillis > 0L)
        require(authFreshMillis > 0L)
    }
}

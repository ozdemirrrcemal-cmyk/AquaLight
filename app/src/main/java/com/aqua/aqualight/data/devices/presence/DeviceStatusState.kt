package com.aqua.aqualight.data.devices.presence

data class DeviceStatusState(
    val deviceId: Long,
    val ip: String,
    val status: DeviceConnectionStatus,
    val isOnline: Boolean,
    val lastSeenMillis: Long,
    val lastCheckedMillis: Long,
    val missedChecks: Int
)
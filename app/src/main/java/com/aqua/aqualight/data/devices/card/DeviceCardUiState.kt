package com.aqua.aqualight.data.devices.card

import com.aqua.aqualight.data.devices.catalog.AquaDeviceType

/**
 * Single source for the common card fields shared by Devices, Tank devices and
 * other device-list surfaces.
 *
 * Device-specific values stay in their own domain layer. This model only owns
 * the common identity/presence/card metadata so every screen renders online,
 * offline, title, IP and type with the same rules.
 */
data class DeviceCardUiState(
    val deviceId: Long,
    val title: String,
    val familyName: String,
    val tankName: String,
    val ip: String,
    val serial: String,
    val firmwareBuild: String,
    val deviceType: AquaDeviceType,
    val isOnline: Boolean,
    val lastSeenMillis: Long,
    val lastCheckedMillis: Long?,
    val missedChecks: Int,
    val statusText: String,
    val lastSeenText: String = ""
)

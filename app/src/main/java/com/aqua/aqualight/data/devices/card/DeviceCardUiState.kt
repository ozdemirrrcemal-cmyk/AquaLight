package com.aqua.aqualight.data.devices.card

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.catalog.AquaProductKey

/**
 * Single source for the common card fields shared by Devices, Tank devices and
 * other device-list surfaces.
 */
data class DeviceCardUiState(
    val deviceId: Long,
    val title: String,
    val familyName: String,
    val tankName: String,
    val ip: String,
    val serial: String,
    val firmwareBuild: String,
    val productId: String,
    val productKey: AquaProductKey,
    val category: AquaDeviceCategory,
    /** Compatibility until old UI mappers are migrated to category. */
    val deviceType: AquaDeviceType,
    val isOnline: Boolean,
    val lastSeenMillis: Long,
    val lastCheckedMillis: Long?,
    val missedChecks: Int,
    val statusText: String,
    val lastSeenText: String = ""
)

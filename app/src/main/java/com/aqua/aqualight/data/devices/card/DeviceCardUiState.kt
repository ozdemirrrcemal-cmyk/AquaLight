package com.aqua.aqualight.data.devices.card

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
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
    val productLine: String,
    val productModel: String,
    val skuCode: String,
    val setupCode: String,
    val deviceUid: String,
    val macAddress: String,
    val serialNumber: String,
    val shortId: String,
    val hardwareRevision: String,
    val firmwareVersion: String,
    val protocolVersion: Int?,
    val productMetaText: String,
    val identityText: String,
    val networkText: String,
    val statusText: String,
    val isOnline: Boolean,
    val lastSeenMillis: Long,
    val lastCheckedMillis: Long?,
    val missedChecks: Int,
    val lastSeenText: String = ""
)

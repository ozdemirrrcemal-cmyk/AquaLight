package com.aqua.aqualight.ui.tabs.devices.model

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey

data class DeviceCardUi(
    val id: Long,
    val displayName: String,
    val familyName: String,
    val tankName: String = "",
    val ip: String,
    val serial: String,
    val firmwareBuild: String,
    val isOnline: Boolean,
    val lastSeenText: String = "",
    val productId: String = "",
    val productKey: AquaProductKey = AquaProductKey.UNKNOWN,
    val category: AquaDeviceCategory = AquaDeviceCategory.UNKNOWN,
    val productLine: String = "",
    val productModel: String = "",
    val skuCode: String = "",
    val setupCode: String = "",
    val deviceUid: String = "",
    val macAddress: String = "",
    val serialNumber: String = "",
    val shortId: String = "",
    val hardwareRevision: String = "",
    val firmwareVersion: String = "",
    val protocolVersion: Int? = null,
    val productMetaText: String = "",
    val identityText: String = "",
    val networkText: String = "",
    val statusText: String = ""
)

package com.aqua.aqualight.ui.tabs.devices.model

import com.aqua.aqualight.data.devices.catalog.AquaDeviceType

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
    val deviceType: AquaDeviceType = AquaDeviceType.UNKNOWN
)
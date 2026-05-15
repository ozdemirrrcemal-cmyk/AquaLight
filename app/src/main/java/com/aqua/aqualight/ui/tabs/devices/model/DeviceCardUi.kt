package com.aqua.aqualight.ui.tabs.devices.model

data class DeviceCardUi(
    val id: Long,
    val name: String,
    val aquaName: String,
    val tankName: String = "",          // <-- Yeni alan eklendi
    val ip: String,
    val serial: String,
    val firmwareBuild: String,
    val isOnline: Boolean,
    val lastSeenText: String = "",
    val type: DeviceType = DeviceType.fromName(aquaName)
)
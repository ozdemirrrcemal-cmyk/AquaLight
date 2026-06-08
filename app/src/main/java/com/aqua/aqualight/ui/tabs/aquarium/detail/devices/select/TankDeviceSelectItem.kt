package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

data class TankDeviceSelectItem(
    val deviceId: Long,
    val title: String,
    val subtitle: String,
    val statusText: String
) {

    val initial: String
        get() = title
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "D"
}
package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import androidx.annotation.DrawableRes

data class TankDeviceSelectItem(
    val deviceId: Long,
    val title: String,
    val serialNumber: String,
    @DrawableRes val iconRes: Int,
    val isOnline: Boolean
)
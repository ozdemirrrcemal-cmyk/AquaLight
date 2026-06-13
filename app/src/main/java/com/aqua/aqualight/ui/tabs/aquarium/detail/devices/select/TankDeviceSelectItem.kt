package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import androidx.annotation.DrawableRes

data class TankDeviceSelectItem(
    val deviceId: Long,
    val displayName: String,
    val productMetaText: String,
    val identityText: String,
    @DrawableRes val iconRes: Int,
    val isOnline: Boolean
)
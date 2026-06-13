package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes

data class DeviceCompactCardUi(
    val deviceId: Long,
    val displayName: String,
    val serialText: String,
    val tankText: String = "",
    val showTankText: Boolean = false,
    @DrawableRes val iconRes: Int,
    val isOnline: Boolean
)

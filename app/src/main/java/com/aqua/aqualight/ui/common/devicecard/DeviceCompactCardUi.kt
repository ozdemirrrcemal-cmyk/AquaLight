package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes

data class DeviceCompactCardUi(
    val deviceId: Long,
    val displayName: String,
    val serialText: String,
    val tankText: String = "",
    val showTankText: Boolean = false,
    val supportingText: String = "",
    val showSupportingText: Boolean = false,
    @DrawableRes val iconRes: Int,
    val isOnline: Boolean = false,
    val showConnectionStatus: Boolean = true,
    val actionText: String = "",
    val showAction: Boolean = false
)

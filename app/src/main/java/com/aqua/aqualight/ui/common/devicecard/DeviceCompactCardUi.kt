package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes

data class DeviceCompactCardUi(
    val deviceUid: String,
    val displayName: String,
    val serialText: String,
    val supportingText: String = "",
    @DrawableRes val iconRes: Int,
    val statusStyle: DeviceCompactStatusStyle = DeviceCompactStatusStyle.OFFLINE,
    val actionText: String = "",
    val showAction: Boolean = false,
    val isBusy: Boolean = false
)

enum class DeviceCompactStatusStyle {
    ONLINE,
    CONNECTING,
    WARNING,
    OFFLINE
}

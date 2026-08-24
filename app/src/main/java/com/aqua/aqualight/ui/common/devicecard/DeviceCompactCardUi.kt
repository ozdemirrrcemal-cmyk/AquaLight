package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes

data class DeviceCompactCardUi(
    val deviceUid: String,
    val displayName: String,
    val serialText: String,
    val supportingText: String = "",
    @DrawableRes val iconRes: Int,
    val isDosingProduct: Boolean = false,
    /** Exact catalog-owned Dose Pro physical channel count when available. */
    val dosingChannelCount: Int? = null,
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

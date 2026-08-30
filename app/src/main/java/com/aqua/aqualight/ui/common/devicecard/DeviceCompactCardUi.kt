package com.aqua.aqualight.ui.common.devicecard

import androidx.annotation.DrawableRes
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

data class DeviceCompactCardUi(
    val deviceUid: String,
    val displayName: String,
    val serialText: String,
    val supportingText: String = "",
    @DrawableRes val iconRes: Int,
    val statusStyle: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val actionText: String = "",
    val showAction: Boolean = false,
    val isBusy: Boolean = false
)

package com.aqua.aqualight.ui.tabs.devices.detail.chrome

import androidx.annotation.DrawableRes

data class DeviceHeaderAction(
    @DrawableRes val iconRes: Int,
    val contentDescription: String,
    val onClick: () -> Unit
)
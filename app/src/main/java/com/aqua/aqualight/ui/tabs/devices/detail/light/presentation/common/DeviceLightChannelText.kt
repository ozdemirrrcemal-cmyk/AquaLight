package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R

@StringRes
internal fun deviceLightChannelNameResource(wireKey: String): Int = when (wireKey) {
    "red" -> R.string.device_light_channel_red
    "green" -> R.string.device_light_channel_green
    "blue" -> R.string.device_light_channel_blue
    "white" -> R.string.device_light_channel_white
    else -> R.string.device_light_unknown_value
}

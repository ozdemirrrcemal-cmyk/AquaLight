package com.aqua.aqualight.ui.tabs.devices.detail.light.core.presets.catalog

import com.aqua.aqualight.data.devices.light.model.LightRgbwChannels

data class LightBuiltInPreset(
    val id: String,
    val title: String,
    val manualTitle: String,
    val subtitle: String,
    val channels: LightRgbwChannels
) {

    val red: Int
        get() = channels.safeRed

    val green: Int
        get() = channels.safeGreen

    val blue: Int
        get() = channels.safeBlue

    val white: Int
        get() = channels.safeWhite
}

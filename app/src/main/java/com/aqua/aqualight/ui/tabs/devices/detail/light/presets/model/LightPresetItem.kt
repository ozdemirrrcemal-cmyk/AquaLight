package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.color.LightRgbwChannels
import com.aqua.aqualight.data.devices.light.math.LightRgbwPreviewColorMath
import com.aqua.aqualight.data.devices.light.math.LightOutputMath

data class LightPresetItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: LightPresetCategory,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {

    val channels: LightRgbwChannels
        get() = LightRgbwChannels(
            red = red,
            green = green,
            blue = blue,
            white = white
        )

    val isCustom: Boolean
        get() = category == LightPresetCategory.CUSTOM

    val channelLabel: String
        get() = channels.compactLabel

    val previewColor: Int
        get() = LightRgbwPreviewColorMath.previewColor(
            red = channels.safeRed,
            green = channels.safeGreen,
            blue = channels.safeBlue,
            white = channels.safeWhite
        )

    val outputPercent: Int
        get() =
            LightOutputMath.outputPercent(
                red = channels.safeRed,
                green = channels.safeGreen,
                blue = channels.safeBlue,
                white = channels.safeWhite
            )
}

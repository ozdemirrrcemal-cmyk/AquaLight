package com.aqua.aqualight.ui.tabs.devices.detail.light.core.color

import com.aqua.aqualight.data.devices.light.math.LightRgbwPreviewColorMath

/**
 * UI compatibility wrapper around the central RGBW preview renderer.
 *
 * Keep this object so existing Manual/Preset screens do not need to know where
 * the device/domain math lives. The actual color calculation is single-source
 * in data.devices.light.math.LightRgbwPreviewColorMath.
 */
object LightRgbwColorMath {

    fun previewColor(
        channels: LightRgbwChannels
    ): Int {
        return previewColor(
            red = channels.safeRed,
            green = channels.safeGreen,
            blue = channels.safeBlue,
            white = channels.safeWhite
        )
    }

    fun previewColor(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): Int {
        return LightRgbwPreviewColorMath.previewColor(
            red = red,
            green = green,
            blue = blue,
            white = white
        )
    }
}

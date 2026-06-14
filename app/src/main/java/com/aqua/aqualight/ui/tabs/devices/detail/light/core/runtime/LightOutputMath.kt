package com.aqua.aqualight.ui.tabs.devices.detail.light.core.runtime

/**
 * UI compatibility wrapper around the central light output math.
 */
object LightOutputMath {

    fun outputPercent(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): Int {
        return com.aqua.aqualight.data.devices.light.math.LightOutputMath.outputPercent(
            red = red,
            green = green,
            blue = blue,
            white = white
        )
    }
}

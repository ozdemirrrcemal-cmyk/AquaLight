package com.aqua.aqualight.ui.tabs.devices.detail.light.core.runtime

object LightOutputMath {

    fun outputPercent(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): Int {
        return maxOf(
            red,
            green,
            blue,
            white
        ).coerceIn(0, 100)
    }
}

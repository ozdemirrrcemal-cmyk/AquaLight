package com.aqua.aqualight.data.devices.light.runtime

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
        ).coerceIn(
            0,
            100
        )
    }
}
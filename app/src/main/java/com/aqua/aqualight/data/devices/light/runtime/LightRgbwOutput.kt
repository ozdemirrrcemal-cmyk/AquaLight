package com.aqua.aqualight.data.devices.light.runtime

data class LightRgbwOutput(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {

    val maxOutputPercent: Int
        get() =
            LightOutputMath.outputPercent(
                red = red,
                green = green,
                blue = blue,
                white = white
            )

    val isPowerOn: Boolean
        get() = maxOutputPercent > 0

    fun normalized(): LightRgbwOutput {
        return copy(
            red = red.coerceIn(
                0,
                100
            ),
            green = green.coerceIn(
                0,
                100
            ),
            blue = blue.coerceIn(
                0,
                100
            ),
            white = white.coerceIn(
                0,
                100
            )
        )
    }
}
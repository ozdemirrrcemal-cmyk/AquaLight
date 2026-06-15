package com.aqua.aqualight.data.devices.light.curve.model

data class LightCurveChannelValues(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {
    fun normalized(): LightCurveChannelValues {
        return copy(
            red = red.coerceIn(0, 100),
            green = green.coerceIn(0, 100),
            blue = blue.coerceIn(0, 100),
            white = white.coerceIn(0, 100)
        )
    }
}
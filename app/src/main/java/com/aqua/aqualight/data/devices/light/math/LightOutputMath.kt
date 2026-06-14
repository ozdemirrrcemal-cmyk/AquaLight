package com.aqua.aqualight.data.devices.light.math

/**
 * Central light-output intensity math.
 *
 * Output percent is the visual/runtime channel intensity, not the electrical
 * watt load. A single RGBW channel at 100% means the light output is 100%, even
 * if the current watt draw is much lower than full fixture power.
 */
object LightOutputMath {

    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 100

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
            MIN_PERCENT,
            MAX_PERCENT
        )
    }
}

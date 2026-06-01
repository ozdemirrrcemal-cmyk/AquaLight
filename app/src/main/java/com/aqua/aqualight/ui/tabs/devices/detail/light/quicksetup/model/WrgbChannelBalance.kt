package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

import java.io.Serializable
import kotlin.math.roundToInt

data class WrgbChannelBalance(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) : Serializable {

    fun scaledBy(
        masterPercent: Int
    ): WrgbChannelOutput {
        val safeMaster =
            masterPercent.coerceIn(
                MIN_PERCENT,
                MAX_PERCENT
            )

        return WrgbChannelOutput(
            red = scaleChannel(red, safeMaster),
            green = scaleChannel(green, safeMaster),
            blue = scaleChannel(blue, safeMaster),
            white = scaleChannel(white, safeMaster)
        )
    }

    private fun scaleChannel(
        channelPercent: Int,
        masterPercent: Int
    ): Int {
        return ((channelPercent.coerceIn(MIN_PERCENT, MAX_PERCENT) *
            masterPercent.coerceIn(MIN_PERCENT, MAX_PERCENT)) / 100f)
            .roundToInt()
            .coerceIn(
                MIN_PERCENT,
                MAX_PERCENT
            )
    }

    private companion object {
        private const val MIN_PERCENT = 0
        private const val MAX_PERCENT = 100
    }
}

data class WrgbChannelOutput(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) : Serializable
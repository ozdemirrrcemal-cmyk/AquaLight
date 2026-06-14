package com.aqua.aqualight.data.devices.light.math

import android.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Central RGBW preview renderer used by all Light screens and tank cards.
 *
 * This is UI preview math, not physical spectral simulation. It mixes channel
 * contribution in linear light and converts back to sRGB so manual, presets,
 * dashboard and tank cards render the same RGBW combination consistently.
 */
object LightRgbwPreviewColorMath {

    private const val OFF_PREVIEW_RED = 10
    private const val OFF_PREVIEW_GREEN = 16
    private const val OFF_PREVIEW_BLUE = 24

    private val redEmitter = LinearRgb(
        red = 1.00,
        green = 0.025,
        blue = 0.005
    )

    private val greenEmitter = LinearRgb(
        red = 0.060,
        green = 1.00,
        blue = 0.180
    )

    private val blueEmitter = LinearRgb(
        red = 0.030,
        green = 0.090,
        blue = 1.00
    )

    private val whiteEmitter = LinearRgb(
        red = 0.850,
        green = 0.920,
        blue = 1.00
    )

    fun previewColor(
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): Int {
        val redLevel = percentToLevel(red)
        val greenLevel = percentToLevel(green)
        val blueLevel = percentToLevel(blue)
        val whiteLevel = percentToLevel(white)

        val strongestInput = maxOf(
            redLevel,
            greenLevel,
            blueLevel,
            whiteLevel
        )

        if (strongestInput <= 0.0) {
            return Color.rgb(
                OFF_PREVIEW_RED,
                OFF_PREVIEW_GREEN,
                OFF_PREVIEW_BLUE
            )
        }

        val mixed =
            redEmitter * redLevel +
                greenEmitter * greenLevel +
                blueEmitter * blueLevel +
                whiteEmitter * whiteLevel

        val peak = maxOf(
            mixed.red,
            mixed.green,
            mixed.blue
        )

        if (peak <= 0.0) {
            return Color.rgb(
                OFF_PREVIEW_RED,
                OFF_PREVIEW_GREEN,
                OFF_PREVIEW_BLUE
            )
        }

        val previewBrightness = 0.46 + (0.54 * strongestInput)
        val displayLinear = LinearRgb(
            red = (mixed.red / peak) * previewBrightness,
            green = (mixed.green / peak) * previewBrightness,
            blue = (mixed.blue / peak) * previewBrightness
        )

        return Color.rgb(
            linearToSrgb(displayLinear.red),
            linearToSrgb(displayLinear.green),
            linearToSrgb(displayLinear.blue)
        )
    }

    private fun percentToLevel(
        value: Int
    ): Double {
        return value.coerceIn(
            LightOutputMath.MIN_PERCENT,
            LightOutputMath.MAX_PERCENT
        ) / 100.0
    }

    private fun linearToSrgb(
        value: Double
    ): Int {
        val safeValue = value.coerceIn(0.0, 1.0)
        val srgb = if (safeValue <= 0.0031308) {
            safeValue * 12.92
        } else {
            1.055 * safeValue.pow(1.0 / 2.4) - 0.055
        }

        return (srgb * 255.0)
            .roundToInt()
            .coerceIn(0, 255)
    }

    private data class LinearRgb(
        val red: Double,
        val green: Double,
        val blue: Double
    ) {

        operator fun plus(
            other: LinearRgb
        ): LinearRgb {
            return LinearRgb(
                red = red + other.red,
                green = green + other.green,
                blue = blue + other.blue
            )
        }

        operator fun times(
            multiplier: Double
        ): LinearRgb {
            return LinearRgb(
                red = red * multiplier,
                green = green * multiplier,
                blue = blue * multiplier
            )
        }
    }
}

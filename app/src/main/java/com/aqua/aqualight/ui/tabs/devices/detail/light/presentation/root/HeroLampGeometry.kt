package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Normalized masks registered to the unchanged device_light_hero_card artwork. */
internal class HeroLampGeometry(size: Size) {
    val bounds = Rect(
        left = size.width * LAMP_LEFT,
        top = size.height * LAMP_TOP,
        right = size.width * LAMP_RIGHT,
        bottom = size.height * LAMP_BOTTOM
    )
    val verticalFeather = Brush.verticalGradient(
        0f to Color.Transparent,
        VERTICAL_FEATHER_START to Color.Black,
        VERTICAL_FEATHER_END to Color.Black,
        1f to Color.Transparent,
        startY = bounds.top,
        endY = bounds.bottom
    )
    val horizontalFeather = Brush.horizontalGradient(
        0f to Color.Transparent,
        EDGE_FEATHER to Color.Black,
        (1f - EDGE_FEATHER) to Color.Black,
        1f to Color.Transparent,
        startX = bounds.left,
        endX = bounds.right
    )

    fun outputBrush(channels: HeroLightChannels): Brush {
        val red = lampEmissionAlpha(channels.red)
        val green = lampEmissionAlpha(channels.green)
        val blue = lampEmissionAlpha(channels.blue)
        val white = lampEmissionAlpha(channels.white)
        return Brush.horizontalGradient(
            0f to red,
            RED_CENTER to red,
            GREEN_CENTER to green,
            BLUE_CENTER to blue,
            WHITE_CENTER to white,
            1f to white,
            startX = bounds.left,
            endX = bounds.right
        )
    }
}

private fun lampEmissionAlpha(level: Float): Color =
    Color.Black.copy(alpha = heroDisplayResponse(level))

private const val LAMP_LEFT = 0.442f
private const val LAMP_RIGHT = 0.856f
private const val LAMP_TOP = 0.245f
private const val LAMP_BOTTOM = 0.475f
private const val VERTICAL_FEATHER_START = 0.19f
private const val VERTICAL_FEATHER_END = 0.72f
private const val EDGE_FEATHER = 0.035f
private const val RED_CENTER = 0.186f
private const val GREEN_CENTER = 0.386f
private const val BLUE_CENTER = 0.599f
private const val WHITE_CENTER = 0.821f

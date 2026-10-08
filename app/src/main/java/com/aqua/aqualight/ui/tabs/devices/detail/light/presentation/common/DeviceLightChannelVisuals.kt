package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

import androidx.compose.ui.graphics.Color

/**
 * Canonical WRGB presentation colors for every Light channel control.
 *
 * Values intentionally match the firmware V1 channel descriptor contract used by the
 * Manual screen. Manual, Custom, and Automatic must resolve slider colors through this
 * function instead of supplying screen-specific palette colors.
 */
internal fun deviceLightChannelColor(wireKey: String): Color = when (wireKey) {
    "red" -> Color(CHANNEL_RED_ARGB)
    "green" -> Color(CHANNEL_GREEN_ARGB)
    "blue" -> Color(CHANNEL_BLUE_ARGB)
    "white" -> Color(CHANNEL_WHITE_ARGB)
    else -> Color(CHANNEL_WHITE_ARGB)
}

private const val CHANNEL_RED_ARGB = 0xFFFF0000
private const val CHANNEL_GREEN_ARGB = 0xFF00FF00
private const val CHANNEL_BLUE_ARGB = 0xFF0000FF
private const val CHANNEL_WHITE_ARGB = 0xFFFFFFFF

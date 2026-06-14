package com.aqua.aqualight.ui.tabs.devices.detail.light.core.color

/**
 * Normalized WRGB channel values used by every light screen.
 *
 * Device values are percentage based. The raw constructor values are kept for
 * compatibility with persisted data, while safe values guarantee all UI math is
 * clamped consistently to the supported 0..100 range.
 */
data class LightRgbwChannels(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {

    val safeRed: Int
        get() = red.coerceIn(MIN_PERCENT, MAX_PERCENT)

    val safeGreen: Int
        get() = green.coerceIn(MIN_PERCENT, MAX_PERCENT)

    val safeBlue: Int
        get() = blue.coerceIn(MIN_PERCENT, MAX_PERCENT)

    val safeWhite: Int
        get() = white.coerceIn(MIN_PERCENT, MAX_PERCENT)

    val compactLabel: String
        get() = "R$safeRed · G$safeGreen · B$safeBlue · W$safeWhite"

    companion object {
        const val MIN_PERCENT = 0
        const val MAX_PERCENT = 100
    }
}

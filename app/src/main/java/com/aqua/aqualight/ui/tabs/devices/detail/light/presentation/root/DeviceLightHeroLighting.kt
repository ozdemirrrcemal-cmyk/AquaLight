package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import kotlin.math.pow

/** Linear effective channel levels, never requested levels or a locally evaluated schedule. */
internal data class HeroLightChannels(
    val red: Float = 0f,
    val green: Float = 0f,
    val blue: Float = 0f,
    val white: Float = 0f
)

internal data class HeroLightProfile(
    val red: Boolean,
    val green: Boolean,
    val blue: Boolean,
    val white: Boolean
)

internal data class HeroLightFrame(
    val channels: HeroLightChannels,
    val profile: HeroLightProfile
)

internal data class HeroSceneExposure(
    val red: Float,
    val green: Float,
    val blue: Float,
    val ambient: Float
)

/** Keys are the Light V1 wire identities, not list positions or translated display names. */
internal fun resolveHeroLightFrame(
    effectivePercents: Map<String, Int>,
    outputActive: Boolean?
): HeroLightFrame {
    val profile = HeroLightProfile(
        red = effectivePercents.containsKey("red"),
        green = effectivePercents.containsKey("green"),
        blue = effectivePercents.containsKey("blue"),
        white = effectivePercents.containsKey("white")
    )
    val channels = if (outputActive == true) {
        HeroLightChannels(
            red = effectivePercents.channelLevel("red"),
            green = effectivePercents.channelLevel("green"),
            blue = effectivePercents.channelLevel("blue"),
            white = effectivePercents.channelLevel("white")
        )
    } else {
        // A cold/unknown frame must not flash the fully illuminated source artwork.
        HeroLightChannels()
    }
    return HeroLightFrame(channels, profile)
}

/**
 * Mix energy before applying the display response. White contributes to all three components.
 * Normalize against installed channels, including channels currently at zero, so a sunrise
 * cannot normalize itself to full brightness. RGB products do not get a fictional white LED.
 * This is an artwork exposure model, not a calibrated lux, PAR, or spectral simulation.
 */
internal fun HeroLightChannels.sceneExposure(profile: HeroLightProfile): HeroSceneExposure {
    val redGain = heroDisplayResponse(mixWithWhite(red, profile.red, white, profile.white))
    val greenGain = heroDisplayResponse(mixWithWhite(green, profile.green, white, profile.white))
    val blueGain = heroDisplayResponse(mixWithWhite(blue, profile.blue, white, profile.white))
    return HeroSceneExposure(
        red = redGain,
        green = greenGain,
        blue = blueGain,
        ambient = (1f - maxOf(redGain, greenGain, blueGain)) * DARK_AMBIENT_GAIN
    )
}

/** sRGB-shaped response keeps low-output ramps visible without thresholds or overshoot. */
internal fun heroDisplayResponse(energy: Float): Float {
    val level = if (energy.isFinite()) energy.coerceIn(0f, 1f) else 0f
    return when {
        level == 1f -> 1f // Exact identity at full output, including floating-point rounding.
        level <= SRGB_LINEAR_BREAK -> level * SRGB_LINEAR_SCALE
        else -> SRGB_SCALE * level.pow(1f / SRGB_GAMMA) - SRGB_OFFSET
    }
}

private fun Map<String, Int>.channelLevel(key: String): Float =
    (get(key) ?: 0).coerceIn(0, CHANNEL_PERCENT_MAX) / CHANNEL_PERCENT_MAX.toFloat()

private fun mixWithWhite(
    color: Float,
    colorInstalled: Boolean,
    white: Float,
    whiteInstalled: Boolean
): Float {
    val installed = (if (colorInstalled) 1 else 0) + (if (whiteInstalled) 1 else 0)
    if (installed == 0) return 0f
    val colorEnergy = if (colorInstalled) color else 0f
    val whiteEnergy = if (whiteInstalled) white else 0f
    return (colorEnergy + whiteEnergy) / installed
}

private const val CHANNEL_PERCENT_MAX = 100
private const val DARK_AMBIENT_GAIN = 0.035f
private const val SRGB_LINEAR_BREAK = 0.0031308f
private const val SRGB_LINEAR_SCALE = 12.92f
private const val SRGB_SCALE = 1.055f
private const val SRGB_GAMMA = 2.4f
private const val SRGB_OFFSET = 0.055f

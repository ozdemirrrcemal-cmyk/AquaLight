package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import kotlin.math.pow

/** Linear, effective channel levels, not requested settings or a locally replayed schedule. */
internal data class DeviceLightHeroIllumination(
    val red: Float,
    val green: Float,
    val blue: Float,
    val white: Float
) {
    val isFull: Boolean
        get() = red == 1f && green == 1f && blue == 1f && white == 1f

    val isUniform: Boolean
        get() = red == green && green == blue && blue == white

    fun level(channel: DeviceLightHeroEmitterChannel): Float = when (channel) {
        DeviceLightHeroEmitterChannel.RED -> red
        DeviceLightHeroEmitterChannel.GREEN -> green
        DeviceLightHeroEmitterChannel.BLUE -> blue
        DeviceLightHeroEmitterChannel.WHITE -> white
    }

    /** White illuminates all three components; RGB preserves the reported spectral balance. */
    fun sceneGains() = DeviceLightHeroSceneGains(
        red = heroDisplayGain((red + white) * 0.5f),
        green = heroDisplayGain((green + white) * 0.5f),
        blue = heroDisplayGain((blue + white) * 0.5f)
    )

    companion object {
        val dark = DeviceLightHeroIllumination(0f, 0f, 0f, 0f)
        val full = DeviceLightHeroIllumination(1f, 1f, 1f, 1f)
    }
}

internal data class DeviceLightHeroSceneGains(val red: Float, val green: Float, val blue: Float)

internal enum class DeviceLightHeroEmitterChannel(val wireKey: String) {
    RED("red"),
    GREEN("green"),
    BLUE("blue"),
    WHITE("white")
}

/** Missing/ambiguous telemetry never lights an emitter; explicit device-off has precedence. */
internal fun DeviceLightHeroSnapshot.toHeroIllumination(
    channels: List<DeviceLightChannelOutputSnapshot>
): DeviceLightHeroIllumination = if (outputActive == true) {
    DeviceLightHeroIllumination(
        red = channels.effectiveLevel(DeviceLightHeroEmitterChannel.RED),
        green = channels.effectiveLevel(DeviceLightHeroEmitterChannel.GREEN),
        blue = channels.effectiveLevel(DeviceLightHeroEmitterChannel.BLUE),
        white = channels.effectiveLevel(DeviceLightHeroEmitterChannel.WHITE)
    )
} else {
    DeviceLightHeroIllumination.dark
}

private fun List<DeviceLightChannelOutputSnapshot>.effectiveLevel(
    channel: DeviceLightHeroEmitterChannel
): Float = singleOrNull { it.key == channel.wireKey }
    ?.effectivePercent
    ?.coerceIn(0, 100)
    ?.div(100f)
    ?: 0f

/**
 * Perceptual exposure for an already tone-mapped illustration, not a PAR/CCT calibration.
 * The sRGB-shaped response avoids a harsh black-to-on step at low effective PWM percentages.
 * A small neutral floor leaves the tank silhouette visible without luminous white LED spots.
 */
internal fun heroDisplayGain(level: Float): Float {
    val linear = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
    val encoded = when {
        linear == 1f -> 1f
        linear <= 0.0031308f -> 12.92f * linear
        else -> 1.055f * linear.pow(1f / 2.4f) - 0.055f
    }
    return HERO_DARK_GAIN + (1f - HERO_DARK_GAIN) * encoded
}

internal const val HERO_DARK_GAIN = 0.04f

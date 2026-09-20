package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot

/**
 * Pure presentation projection for the hero renderer.
 *
 * The renderer never owns Light state and never performs firmware I/O. It only projects the
 * effective channel percentages from the already-authoritative Light control snapshot.
 */
internal data class DeviceLightAquariumLighting(
    val red: Float,
    val green: Float,
    val blue: Float,
    val white: Float,
    val intensity: Float
) {
    companion object {
        val Off = DeviceLightAquariumLighting(
            red = 0f,
            green = 0f,
            blue = 0f,
            white = 0f,
            intensity = 0f
        )
    }
}

internal fun resolveDeviceLightAquariumLighting(
    channels: List<DeviceLightChannelOutputSnapshot>,
    outputActive: Boolean?
): DeviceLightAquariumLighting {
    if (outputActive == false) return DeviceLightAquariumLighting.Off

    val red = channels.effectiveFraction("red")
    val green = channels.effectiveFraction("green")
    val blue = channels.effectiveFraction("blue")
    val white = channels.effectiveFraction("white")

    // Fixture-calibrated visual energy model. White contributes most of the scene luminance while
    // RGB still has enough weight to make low-level sunrise/sunset ramps visible on a phone panel.
    val additiveEnergy =
        (white * WHITE_ENERGY_WEIGHT) +
            (red * RED_ENERGY_WEIGHT) +
            (green * GREEN_ENERGY_WEIGHT) +
            (blue * BLUE_ENERGY_WEIGHT)
    val peakEnergy = maxOf(red, green, blue, white) * PEAK_ENERGY_WEIGHT
    val intensity = (additiveEnergy + peakEnergy).coerceIn(0f, 1f)

    return DeviceLightAquariumLighting(
        red = red,
        green = green,
        blue = blue,
        white = white,
        intensity = intensity
    )
}

private fun List<DeviceLightChannelOutputSnapshot>.effectiveFraction(key: String): Float {
    val percent = firstOrNull { channel -> channel.key == key }
        ?.effectivePercent
        ?.coerceIn(MIN_PERCENT, MAX_PERCENT)
        ?: MIN_PERCENT
    return percent.toFloat() / MAX_PERCENT.toFloat()
}

private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
private const val WHITE_ENERGY_WEIGHT = 0.58f
private const val RED_ENERGY_WEIGHT = 0.14f
private const val GREEN_ENERGY_WEIGHT = 0.16f
private const val BLUE_ENERGY_WEIGHT = 0.12f
private const val PEAK_ENERGY_WEIGHT = 0.25f

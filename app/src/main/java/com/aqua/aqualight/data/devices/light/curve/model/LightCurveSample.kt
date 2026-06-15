package com.aqua.aqualight.data.devices.light.curve.model

/**
 * Pure light-curve sample used by data/domain calculations and UI graph rendering.
 *
 * This model intentionally avoids Android graphics classes so the interpolation
 * logic remains testable outside the Android view layer.
 */
data class LightCurveSample(
    val minute: Float,
    val percent: Float
)

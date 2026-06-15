package com.aqua.aqualight.data.devices.light.programs.compiler

/**
 * Device-safe point used by the schedule compiler.
 *
 * x = minute of day, y = channel percent. Keeping the x/y aliases makes the
 * graph and legacy timeline code simple while avoiding Android graphics types
 * in the domain layer.
 */
data class LightCurveSample(
    val x: Int,
    val y: Int
) {
    val minuteOfDay: Int
        get() = x

    val percent: Int
        get() = y
}

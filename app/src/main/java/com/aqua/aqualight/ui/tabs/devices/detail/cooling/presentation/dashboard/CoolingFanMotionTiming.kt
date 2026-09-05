package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import kotlin.math.roundToInt

/**
 * Converts the applied firmware PWM output into the visual rotor period.
 *
 * Angular velocity is proportional to the applied output: at full output one revolution takes
 * [FULL_OUTPUT_FAN_MOTION_MILLIS], while halving the output doubles the period.
 */
internal fun fanMotionDurationMillis(outputIntensity: Float): Int {
    val normalizedOutput = outputIntensity.coerceIn(
        MINIMUM_ACTIVE_FAN_INTENSITY,
        MAXIMUM_OUTPUT_INTENSITY
    )
    return (FULL_OUTPUT_FAN_MOTION_MILLIS / normalizedOutput).roundToInt()
}

private const val FULL_OUTPUT_FAN_MOTION_MILLIS = 620
private const val MINIMUM_ACTIVE_FAN_INTENSITY = 0.01f
private const val MAXIMUM_OUTPUT_INTENSITY = 1f

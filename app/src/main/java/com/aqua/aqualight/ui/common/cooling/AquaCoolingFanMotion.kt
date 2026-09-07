package com.aqua.aqualight.ui.common.cooling

import kotlin.math.roundToInt

/**
 * Converts a normalized fan output into the shared visual motion timing.
 *
 * Angular velocity is proportional to output. At full output the rotor completes one revolution
 * in [FULL_OUTPUT_FAN_MOTION_MILLIS]; zero output always resolves to zero angular velocity.
 */
internal fun fanMotionDurationMillis(outputIntensity: Float): Int {
    val normalizedOutput = outputIntensity.coerceIn(
        MINIMUM_ACTIVE_FAN_INTENSITY,
        MAXIMUM_OUTPUT_INTENSITY
    )
    return (FULL_OUTPUT_FAN_MOTION_MILLIS / normalizedOutput).roundToInt()
}

internal fun fanMotionDegreesPerSecond(outputIntensity: Float): Float =
    outputIntensity.coerceIn(NO_OUTPUT_INTENSITY, MAXIMUM_OUTPUT_INTENSITY) *
        FULL_OUTPUT_FAN_DEGREES_PER_SECOND

private const val FULL_OUTPUT_FAN_MOTION_MILLIS = 620f
private const val MILLISECONDS_PER_SECOND = 1_000f
private const val FULL_ROTATION_DEGREES = 360f
private const val FULL_OUTPUT_FAN_DEGREES_PER_SECOND =
    FULL_ROTATION_DEGREES * MILLISECONDS_PER_SECOND / FULL_OUTPUT_FAN_MOTION_MILLIS
private const val MINIMUM_ACTIVE_FAN_INTENSITY = 0.01f
private const val NO_OUTPUT_INTENSITY = 0f
private const val MAXIMUM_OUTPUT_INTENSITY = 1f

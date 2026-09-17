package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

internal fun automaticCycleMoonTime(startTimeMs: Long?, endTimeMs: Long?): Long {
    if (startTimeMs == null || endTimeMs == null || startTimeMs == endTimeMs) {
        return DEFAULT_MOON_TIME_MS
    }
    val nightDuration = MILLIS_PER_DAY - occupiedAutomaticCycleDuration(startTimeMs, endTimeMs)
    return (endTimeMs + nightDuration / HALF_LONG_DIVISOR) % MILLIS_PER_DAY
}

internal fun occupiedAutomaticCycleDuration(startTimeMs: Long, endTimeMs: Long): Long =
    if (endTimeMs > startTimeMs) endTimeMs - startTimeMs
    else MILLIS_PER_DAY - startTimeMs + endTimeMs

internal fun automaticCycleSignedDeltaDegrees(previous: Double, current: Double): Double {
    val wrapped = (current - previous + ONE_AND_HALF_TURNS_DEGREES) % FULL_CIRCLE_DEGREES
    return wrapped - HALF_TURN_DEGREES
}

internal fun advanceAutomaticCycleDragTime(
    currentTimeMs: Double,
    deltaDegrees: Double,
    sensitivity: Double
): Double {
    val deltaTimeMs = deltaDegrees / FULL_CIRCLE_DEGREES * MILLIS_PER_DAY * sensitivity
    val advanced = (currentTimeMs + deltaTimeMs) % MILLIS_PER_DAY
    return (advanced + MILLIS_PER_DAY) % MILLIS_PER_DAY
}

internal fun automaticCycleTimeFromClockDegrees(degrees: Double, stepMs: Long): Long {
    val normalizedDegrees = (degrees % FULL_CIRCLE_DEGREES + FULL_CIRCLE_DEGREES) %
        FULL_CIRCLE_DEGREES
    val rawTime = normalizedDegrees / FULL_CIRCLE_DEGREES * MILLIS_PER_DAY
    return snapAutomaticCycleTime(rawTime.toLong(), stepMs)
}

internal fun snapAutomaticCycleTime(timeMs: Long, stepMs: Long): Long {
    require(stepMs > 0L)
    val snapped = ((timeMs + stepMs / HALF_LONG_DIVISOR) / stepMs) * stepMs
    return snapped % MILLIS_PER_DAY
}

internal fun nearestAutomaticCycleField(
    touchedTimeMs: Long,
    startTimeMs: Long?,
    endTimeMs: Long?
): DeviceLightAutomaticTimeField = when {
    startTimeMs == null -> DeviceLightAutomaticTimeField.START
    endTimeMs == null -> DeviceLightAutomaticTimeField.END
    circularAutomaticCycleDistance(touchedTimeMs, startTimeMs) <=
        circularAutomaticCycleDistance(touchedTimeMs, endTimeMs) ->
        DeviceLightAutomaticTimeField.START
    else -> DeviceLightAutomaticTimeField.END
}

private fun circularAutomaticCycleDistance(first: Long, second: Long): Long {
    val direct = kotlin.math.abs(first - second)
    return minOf(direct, MILLIS_PER_DAY - direct)
}

internal fun Long.automaticCycleDegrees(): Float =
    toFloat() / MILLIS_PER_DAY * DeviceLightAutomaticDialSpec.fullCircleDegrees

internal fun Offset.automaticCycleRadialOffset(angle: Double, radius: Float): Offset =
    this + Offset(
        x = (cos(angle) * radius).toFloat(),
        y = (sin(angle) * radius).toFloat()
    )

private const val HALF_LONG_DIVISOR = 2L
private const val HALF_TURN_DEGREES = 180.0
private const val FULL_CIRCLE_DEGREES = 360.0
private const val ONE_AND_HALF_TURNS_DEGREES = 540.0
private const val DEFAULT_MOON_HOUR = 1L
private const val DEFAULT_MOON_MINUTE = 30L
private const val MINUTES_PER_HOUR = 60L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val DEFAULT_MOON_TIME_MS =
    (DEFAULT_MOON_HOUR * MINUTES_PER_HOUR + DEFAULT_MOON_MINUTE) * MILLIS_PER_MINUTE

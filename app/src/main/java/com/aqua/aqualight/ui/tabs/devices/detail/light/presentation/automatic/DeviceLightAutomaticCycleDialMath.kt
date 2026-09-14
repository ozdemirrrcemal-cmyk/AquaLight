package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

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

internal fun Long.automaticCycleDegrees(): Float =
    toFloat() / MILLIS_PER_DAY * DeviceLightAutomaticDialSpec.fullCircleDegrees

internal fun Offset.automaticCycleRadialOffset(angle: Double, radius: Float): Offset =
    this + Offset(
        x = (cos(angle) * radius).toFloat(),
        y = (sin(angle) * radius).toFloat()
    )

private const val HALF_LONG_DIVISOR = 2L
private const val DEFAULT_MOON_HOUR = 1L
private const val DEFAULT_MOON_MINUTE = 30L
private const val MINUTES_PER_HOUR = 60L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val DEFAULT_MOON_TIME_MS =
    (DEFAULT_MOON_HOUR * MINUTES_PER_HOUR + DEFAULT_MOON_MINUTE) * MILLIS_PER_MINUTE

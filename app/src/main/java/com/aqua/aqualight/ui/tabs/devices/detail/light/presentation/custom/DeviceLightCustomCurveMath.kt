package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class DeviceLightCustomChartSample(
    val timeMs: Long,
    val channels: Map<DeviceLightCustomChannelId, Int>
)

internal fun List<DeviceLightCustomPointUiState>.interpolatedChannelsAt(
    timeMs: Long,
    fallbackChannels: List<DeviceLightCustomChannelId>
): Map<DeviceLightCustomChannelId, Int> {
    if (isEmpty()) return fallbackChannels.associateWith { MIN_LIGHT_CHANNEL_PERCENT }
    if (size == 1) return first().channels

    val normalizedTime = timeMs.mod(MILLIS_PER_DAY)
    singleOrNull { point -> point.timeMs == normalizedTime }?.let { return it.channels }

    val rightIndex = indexOfFirst { point -> point.timeMs > normalizedTime }
    val left = if (rightIndex > 0) get(rightIndex - 1) else last()
    val right = if (rightIndex >= 0) get(rightIndex) else first()
    val leftTime = if (rightIndex == 0) left.timeMs - MILLIS_PER_DAY else left.timeMs
    val rightTime = if (rightIndex < 0) right.timeMs + MILLIS_PER_DAY else right.timeMs
    val fraction = (normalizedTime - leftTime).toFloat() / (rightTime - leftTime).toFloat()
    val channels = (left.channels.keys + right.channels.keys + fallbackChannels).distinct()
    return channels.associateWith { channel ->
        val start = left.channels[channel] ?: MIN_LIGHT_CHANNEL_PERCENT
        val end = right.channels[channel] ?: MIN_LIGHT_CHANNEL_PERCENT
        (start + (end - start) * fraction).roundToInt()
            .coerceIn(MIN_LIGHT_CHANNEL_PERCENT, MAX_LIGHT_CHANNEL_PERCENT)
    }
}

internal fun List<DeviceLightCustomPointUiState>.chartSamples(
    channels: List<DeviceLightCustomChannelId>,
    windowStartMs: Long,
    windowEndMs: Long
): List<DeviceLightCustomChartSample> {
    if (isEmpty()) return emptyList()
    val boundarySamples = listOf(windowStartMs, windowEndMs).map { timeMs ->
        DeviceLightCustomChartSample(
            timeMs = timeMs,
            channels = interpolatedChannelsAt(timeMs, channels)
        )
    }
    val visiblePoints = filter { point -> point.timeMs in windowStartMs..windowEndMs }
        .map { point -> DeviceLightCustomChartSample(point.timeMs, point.channels) }
    return (boundarySamples + visiblePoints).distinctBy { sample -> sample.timeMs }
        .sortedBy { sample -> sample.timeMs }
}

internal fun nearestVisiblePointTime(
    points: List<DeviceLightCustomPointUiState>,
    tapX: Float,
    chartWidth: Float,
    windowStartMs: Long,
    windowEndMs: Long,
    tolerancePx: Float
): Long? {
    if (chartWidth <= 0f || windowEndMs <= windowStartMs) return null
    return points.asSequence()
        .filter { point -> point.timeMs in windowStartMs..windowEndMs }
        .map { point ->
            point.timeMs to abs(
                tapX - chartX(
                    timeMs = point.timeMs,
                    width = chartWidth,
                    windowStartMs = windowStartMs,
                    windowEndMs = windowEndMs
                )
            )
        }
        .filter { (_, distance) -> distance <= tolerancePx }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

internal fun chartX(
    timeMs: Long,
    width: Float,
    windowStartMs: Long,
    windowEndMs: Long
): Float {
    val duration = (windowEndMs - windowStartMs).coerceAtLeast(1L)
    return width * (timeMs - windowStartMs) / duration.toFloat()
}

internal fun playheadTimeForX(x: Float, chartWidth: Float): Long {
    if (chartWidth <= 0f) return 0L
    val fraction = (x / chartWidth).coerceIn(0f, 1f)
    return (fraction * (MILLIS_PER_DAY - MILLIS_PER_MINUTE)).roundToLong().alignedTime()
}

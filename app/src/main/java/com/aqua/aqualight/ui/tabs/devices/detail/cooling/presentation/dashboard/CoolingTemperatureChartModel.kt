package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingTemperatureTimelinePresentation
import kotlin.math.abs

internal const val TEMPERATURE_CHART_WINDOW_MILLIS = 24L * 60L * 60L * 1_000L
private const val MAXIMUM_CONNECTED_GAP_MILLIS = 65L * 60L * 1_000L
private const val SAME_POSITION_EPSILON = 0.0002f

internal enum class TemperatureChartSource {
    ARCHIVE,
    LIVE
}

internal data class TemperatureChartValue(
    val xFraction: Float,
    val temperatureC: Float,
    val source: TemperatureChartSource
)

internal data class CoolingTemperatureChartData(
    val archivedPoints: List<DeviceCoolingTemperatureHistoryPoint>,
    val historyGeneratedAtEpochMillis: Long?,
    val liveTimeline: CoolingTemperatureTimelinePresentation
)

internal fun temperatureChartValues(
    archivedPoints: List<DeviceCoolingTemperatureHistoryPoint>,
    historyGeneratedAtEpochMillis: Long?,
    liveTimeline: CoolingTemperatureTimelinePresentation,
    animatedLiveHeadTemperatureC: Float? = null
): List<TemperatureChartValue> {
    val windowEndEpochMillis = temperatureChartWindowEnd(
        historyGeneratedAtEpochMillis = historyGeneratedAtEpochMillis,
        liveTimeline = liveTimeline
    )
    val archiveValues = archivedTemperatureChartValues(
        points = archivedPoints,
        windowEndEpochMillis = windowEndEpochMillis
    )
    val liveValues = liveTemperatureChartValues(
        liveTimeline = liveTimeline,
        animatedLiveHeadTemperatureC = animatedLiveHeadTemperatureC
    )

    return coalesceArchiveAndLivePositions(
        (archiveValues + liveValues).sortedBy(TemperatureChartValue::xFraction)
    )
}

private fun temperatureChartWindowEnd(
    historyGeneratedAtEpochMillis: Long?,
    liveTimeline: CoolingTemperatureTimelinePresentation
): Long? {
    val generatedAt = historyGeneratedAtEpochMillis ?: return null
    val anchorEpoch = liveTimeline.historyAnchorEpochMillis
    val anchorUptime = liveTimeline.historyAnchorEvaluatedAtUptimeMillis
    val currentUptime = liveTimeline.currentLivePoint?.evaluatedAtUptimeMillis
    return if (anchorEpoch != null && anchorUptime != null && currentUptime != null) {
        anchorEpoch + (currentUptime - anchorUptime)
    } else {
        generatedAt
    }
}

private fun archivedTemperatureChartValues(
    points: List<DeviceCoolingTemperatureHistoryPoint>,
    windowEndEpochMillis: Long?
): List<TemperatureChartValue> {
    val windowEnd = windowEndEpochMillis ?: return emptyList()
    val windowStart = windowEnd - TEMPERATURE_CHART_WINDOW_MILLIS
    return points.mapNotNull { point ->
        if (
            point.sampledAtEpochMillis !in windowStart..windowEnd ||
            !point.temperatureC.isFinite()
        ) {
            null
        } else {
            TemperatureChartValue(
                xFraction = (
                    (point.sampledAtEpochMillis - windowStart).toDouble() /
                        TEMPERATURE_CHART_WINDOW_MILLIS.toDouble()
                    ).toFloat().coerceIn(0f, 1f),
                temperatureC = point.temperatureC.toFloat(),
                source = TemperatureChartSource.ARCHIVE
            )
        }
    }
}

private fun liveTemperatureChartValues(
    liveTimeline: CoolingTemperatureTimelinePresentation,
    animatedLiveHeadTemperatureC: Float?
): List<TemperatureChartValue> {
    val head = liveTimeline.currentLivePoint ?: return emptyList()
    val uniqueLive = (liveTimeline.committedLivePoints + head)
        .distinctBy { point ->
            point.inputSampleSequence to point.sampledAtUptimeMillis
        }
        .sortedWith(
            compareBy(
                { point -> point.sampledAtUptimeMillis },
                { point -> point.inputSampleSequence }
            )
        )
    return uniqueLive.mapNotNull { point ->
        val ageMillis = head.sampledAtUptimeMillis - point.sampledAtUptimeMillis
        if (
            ageMillis !in 0L..TEMPERATURE_CHART_WINDOW_MILLIS ||
            !point.temperatureC.isFinite()
        ) {
            null
        } else {
            TemperatureChartValue(
                xFraction = (
                    1.0 - ageMillis.toDouble() /
                        TEMPERATURE_CHART_WINDOW_MILLIS.toDouble()
                    ).toFloat().coerceIn(0f, 1f),
                temperatureC = if (
                    point.inputSampleSequence == head.inputSampleSequence &&
                    point.sampledAtUptimeMillis == head.sampledAtUptimeMillis
                ) {
                    animatedLiveHeadTemperatureC ?: point.temperatureC.toFloat()
                } else {
                    point.temperatureC.toFloat()
                },
                source = TemperatureChartSource.LIVE
            )
        }
    }
}

internal fun temperatureChartSegments(
    values: List<TemperatureChartValue>
): List<List<TemperatureChartValue>> {
    if (values.isEmpty()) return emptyList()
    val maximumGapFraction =
        MAXIMUM_CONNECTED_GAP_MILLIS.toFloat() / TEMPERATURE_CHART_WINDOW_MILLIS.toFloat()
    val segments = mutableListOf<MutableList<TemperatureChartValue>>()
    values.forEach { value ->
        val current = segments.lastOrNull()
        if (
            current == null ||
            value.xFraction - current.last().xFraction > maximumGapFraction
        ) {
            segments += mutableListOf(value)
        } else {
            current += value
        }
    }
    return segments
}

private fun coalesceArchiveAndLivePositions(
    values: List<TemperatureChartValue>
): List<TemperatureChartValue> {
    val merged = mutableListOf<TemperatureChartValue>()
    values.forEach { value ->
        val sameTime = merged.lastOrNull()?.takeIf { previous ->
            previous.source != value.source &&
                abs(previous.xFraction - value.xFraction) <= SAME_POSITION_EPSILON
        }
        if (sameTime != null) {
            if (value.source == TemperatureChartSource.LIVE) {
                merged[merged.lastIndex] = value
            }
        } else {
            merged += value
        }
    }
    return merged
}

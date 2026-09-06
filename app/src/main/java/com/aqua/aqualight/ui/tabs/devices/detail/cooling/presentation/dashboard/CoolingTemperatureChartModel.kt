package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingLiveTemperaturePointPresentation
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

internal fun temperatureChartValues(
    archivedPoints: List<DeviceCoolingTemperatureHistoryPoint>,
    historyGeneratedAtEpochMillis: Long?,
    liveTimeline: CoolingTemperatureTimelinePresentation,
    animatedLiveHeadTemperatureC: Float? = null
): List<TemperatureChartValue> {
    val currentLive = liveTimeline.currentLivePoint
    val windowEndEpochMillis = historyGeneratedAtEpochMillis?.let { generatedAt ->
        val anchorEpoch = liveTimeline.historyAnchorEpochMillis
        val anchorUptime = liveTimeline.historyAnchorEvaluatedAtUptimeMillis
        val currentUptime = currentLive?.evaluatedAtUptimeMillis
        if (anchorEpoch != null && anchorUptime != null && currentUptime != null) {
            anchorEpoch + (currentUptime - anchorUptime)
        } else {
            generatedAt
        }
    }

    val archiveValues = if (windowEndEpochMillis == null) {
        emptyList()
    } else {
        val windowStartEpochMillis = windowEndEpochMillis - TEMPERATURE_CHART_WINDOW_MILLIS
        archivedPoints.mapNotNull { point ->
            if (
                point.sampledAtEpochMillis !in
                windowStartEpochMillis..windowEndEpochMillis ||
                !point.temperatureC.isFinite()
            ) {
                null
            } else {
                TemperatureChartValue(
                    xFraction = (
                        (point.sampledAtEpochMillis - windowStartEpochMillis).toDouble() /
                            TEMPERATURE_CHART_WINDOW_MILLIS.toDouble()
                        ).toFloat().coerceIn(0f, 1f),
                    temperatureC = point.temperatureC.toFloat(),
                    source = TemperatureChartSource.ARCHIVE
                )
            }
        }
    }

    val liveValues = currentLive?.let { head ->
        val uniqueLive = buildList {
            addAll(liveTimeline.committedLivePoints)
            if (
                lastOrNull()?.inputSampleSequence != head.inputSampleSequence ||
                lastOrNull()?.sampledAtUptimeMillis != head.sampledAtUptimeMillis
            ) {
                add(head)
            }
        }
        uniqueLive.mapNotNull { point ->
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
    }.orEmpty()

    return coalesceArchiveAndLivePositions(
        (archiveValues + liveValues).sortedBy(TemperatureChartValue::xFraction)
    )
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
        val previous = = merged.lastOrNull()?.takeIf { previous ->
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

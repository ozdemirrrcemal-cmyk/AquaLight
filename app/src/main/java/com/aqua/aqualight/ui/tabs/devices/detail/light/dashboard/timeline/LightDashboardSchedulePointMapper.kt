package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.timeline

import com.aqua.aqualight.data.devices.api.light.LightScheduleChannelState
import com.aqua.aqualight.data.devices.api.light.LightSchedulePoint
import kotlin.math.roundToInt

/**
 * Builds dashboard graph data from the controller schedule exactly at LP point
 * boundaries. The dashboard must not infer Linear/Smooth/Natural modes from a
 * local draft; the device already stores concrete LP points and those points are
 * the source of truth for the chart.
 */
object LightDashboardSchedulePointMapper {

    fun mainProgramSegments(
        scheduleChannels: List<LightScheduleChannelState>
    ): List<LightDashboardTimelineSegment> {
        val channelPointLists = scheduleChannels
            .mapNotNull { channel ->
                channel.points
                    .toNormalizedRuntimePoints()
                    .takeIf { points -> points.isNotEmpty() }
            }

        if (channelPointLists.isEmpty()) {
            return emptyList()
        }

        val aggregatePoints = buildAggregateRuntimePoints(channelPointLists)
            .ifEmpty {
                return emptyList()
            }

        if (aggregatePoints.size == 1) {
            val point = aggregatePoints.first()
            val constantPoints = listOf(
                LightDashboardTimelinePoint(
                    minute = 0,
                    percent = point.percent
                ),
                LightDashboardTimelinePoint(
                    minute = MINUTES_PER_DAY,
                    percent = point.percent
                )
            )

            return listOf(
                LightDashboardTimelineSegment(
                    id = MAIN_SEGMENT_ID,
                    name = MAIN_SEGMENT_NAME,
                    startMinute = 0,
                    peakStartMinute = 0,
                    peakEndMinute = MINUTES_PER_DAY,
                    endMinute = MINUTES_PER_DAY,
                    outputPercent = point.percent,
                    runtimePoints = constantPoints
                )
            )
        }

        val maxOutput = aggregatePoints.maxOf { point -> point.percent }
        val peakMinutes = aggregatePoints
            .filter { point -> point.percent == maxOutput }
            .map { point -> point.minute }
        val startMinute = aggregatePoints.first().minute
        val endMinute = aggregatePoints.last().minute

        if (endMinute <= startMinute) {
            return emptyList()
        }

        return listOf(
            LightDashboardTimelineSegment(
                id = MAIN_SEGMENT_ID,
                name = MAIN_SEGMENT_NAME,
                startMinute = startMinute,
                peakStartMinute = peakMinutes.firstOrNull() ?: startMinute,
                peakEndMinute = peakMinutes.lastOrNull() ?: startMinute,
                endMinute = endMinute,
                outputPercent = maxOutput,
                runtimePoints = aggregatePoints
            )
        )
    }

    private fun buildAggregateRuntimePoints(
        channelPointLists: List<List<LightDashboardTimelinePoint>>
    ): List<LightDashboardTimelinePoint> {
        val sampleMinutes = channelPointLists
            .flatMap { points -> points.map { point -> point.minute } }
            .distinct()
            .sorted()

        return sampleMinutes.map { minute ->
            LightDashboardTimelinePoint(
                minute = minute,
                percent = channelPointLists.maxOf { points ->
                    points.evaluateAtMinute(minute)
                }
            )
        }.distinctBy { point -> point.minute }
    }

    private fun List<LightSchedulePoint>.toNormalizedRuntimePoints(): List<LightDashboardTimelinePoint> {
        return map { point ->
            LightDashboardTimelinePoint(
                minute = point.minuteOfDay.coerceIn(0, MINUTES_PER_DAY),
                percent = point.percent.coerceIn(0, 100)
            )
        }
            .groupBy { point -> point.minute }
            .map { (minute, pointsAtMinute) ->
                LightDashboardTimelinePoint(
                    minute = minute,
                    percent = pointsAtMinute.maxOf { point -> point.percent }
                )
            }
            .sortedBy { point -> point.minute }
    }

    private fun List<LightDashboardTimelinePoint>.evaluateAtMinute(
        minute: Int
    ): Int {
        if (isEmpty()) {
            return 0
        }

        if (size == 1) {
            return first().percent
        }

        val safeMinute = minute.coerceIn(0, MINUTES_PER_DAY)
        val firstPoint = first()
        val lastPoint = last()

        if (safeMinute == firstPoint.minute) {
            return firstPoint.percent
        }

        if (safeMinute == lastPoint.minute) {
            return lastPoint.percent
        }

        val segment = when {
            safeMinute < firstPoint.minute -> {
                RuntimeInterpolationSegment(
                    startMinute = lastPoint.minute - MINUTES_PER_DAY,
                    startPercent = lastPoint.percent,
                    endMinute = firstPoint.minute,
                    endPercent = firstPoint.percent,
                    sampleMinute = safeMinute
                )
            }

            safeMinute > lastPoint.minute -> {
                RuntimeInterpolationSegment(
                    startMinute = lastPoint.minute,
                    startPercent = lastPoint.percent,
                    endMinute = firstPoint.minute + MINUTES_PER_DAY,
                    endPercent = firstPoint.percent,
                    sampleMinute = safeMinute
                )
            }

            else -> {
                val endIndex = indexOfFirst { point -> point.minute >= safeMinute }
                    .takeIf { index -> index > 0 }
                    ?: return firstPoint.percent
                val startPoint = this[endIndex - 1]
                val endPoint = this[endIndex]

                RuntimeInterpolationSegment(
                    startMinute = startPoint.minute,
                    startPercent = startPoint.percent,
                    endMinute = endPoint.minute,
                    endPercent = endPoint.percent,
                    sampleMinute = safeMinute
                )
            }
        }

        return segment.interpolatePercent()
    }

    private data class RuntimeInterpolationSegment(
        val startMinute: Int,
        val startPercent: Int,
        val endMinute: Int,
        val endPercent: Int,
        val sampleMinute: Int
    ) {
        fun interpolatePercent(): Int {
            val duration = endMinute - startMinute
            if (duration <= 0) {
                return endPercent.coerceIn(0, 100)
            }

            val ratio = (sampleMinute - startMinute).toDouble() / duration.toDouble()
            val value = startPercent + (endPercent - startPercent) * ratio
            return value.roundToInt().coerceIn(0, 100)
        }
    }

    private const val MAIN_SEGMENT_ID = "device-main-schedule"
    private const val MAIN_SEGMENT_NAME = "Auto"
    private const val MINUTES_PER_DAY = 24 * 60
}

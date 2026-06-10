package com.aqua.aqualight.data.devices.light.programs.timeline

import com.aqua.aqualight.data.devices.light.curve.interpolator.LightCurveInterpolator
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import kotlin.math.roundToInt

object LightProgramTimelineEvaluator {

    fun outputAtMinute(
        timeline: LightProgramTimeline,
        minute: Int
    ): LightCurveChannelValues {
        val phase = timeline.phaseAt(minute)
            ?: return LightCurveChannelValues(
                red = 0,
                green = 0,
                blue = 0,
                white = 0
            )

        val resolvedMinute = resolvedMinuteForPhase(
            minute = minute,
            phase = phase
        )

        return when (phase.type) {
            LightProgramPhaseType.MAIN_CURVE -> {
                mainCurveOutputAtMinute(
                    phase = phase,
                    minute = resolvedMinute
                )
            }

            LightProgramPhaseType.MOONLIGHT -> {
                phase.channelValues
            }

            LightProgramPhaseType.CLOUD_OVERLAY -> {
                phase.channelValues
            }
        }
    }

    private fun mainCurveOutputAtMinute(
        phase: LightProgramTimelinePhase,
        minute: Int
    ): LightCurveChannelValues {
        return LightCurveChannelValues(
            red = calculateChannelValue(
                phase = phase,
                minute = minute,
                peakPercent = phase.channelValues.red
            ),
            green = calculateChannelValue(
                phase = phase,
                minute = minute,
                peakPercent = phase.channelValues.green
            ),
            blue = calculateChannelValue(
                phase = phase,
                minute = minute,
                peakPercent = phase.channelValues.blue
            ),
            white = calculateChannelValue(
                phase = phase,
                minute = minute,
                peakPercent = phase.channelValues.white
            )
        )
    }

    private fun calculateChannelValue(
        phase: LightProgramTimelinePhase,
        minute: Int,
        peakPercent: Int
    ): Int {
        val safePeak = peakPercent.coerceIn(0, 100)

        if (safePeak <= 0) {
            return 0
        }

        val peakStart = phase.peakStartMinute ?: return 0
        val peakEnd = phase.peakEndMinute ?: return 0

        val points = LightCurveInterpolator.buildCurvePoints(
            startMinute = phase.startMinute,
            peakStartMinute = peakStart,
            peakEndMinute = peakEnd,
            endMinute = phase.endMinute,
            peakPercent = safePeak,
            transitionMode = phase.transitionMode
        ).sortedBy { point ->
            point.x
        }

        if (points.isEmpty()) {
            return 0
        }

        val currentMinute = minute.toDouble()

        val previous = points.lastOrNull { point ->
            point.x.toDouble() <= currentMinute
        }

        val next = points.firstOrNull { point ->
            point.x.toDouble() >= currentMinute
        }

        val value = when {
            previous == null -> {
                points.first().y.toDouble()
            }

            next == null -> {
                points.last().y.toDouble()
            }

            previous.x == next.x -> {
                previous.y.toDouble()
            }

            else -> {
                val previousX = previous.x.toDouble()
                val nextX = next.x.toDouble()
                val previousY = previous.y.toDouble()
                val nextY = next.y.toDouble()

                val progress =
                    (currentMinute - previousX) / (nextX - previousX)

                previousY + ((nextY - previousY) * progress)
            }
        }

        return value
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun resolvedMinuteForPhase(
        minute: Int,
        phase: LightProgramTimelinePhase
    ): Int {
        return when {
            minute >= phase.startMinute -> {
                minute
            }

            phase.endMinute > LightProgramTimelinePhase.MINUTES_PER_DAY -> {
                minute + LightProgramTimelinePhase.MINUTES_PER_DAY
            }

            else -> {
                minute
            }
        }
    }
}
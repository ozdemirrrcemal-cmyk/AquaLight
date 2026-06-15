package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.programs.model.LightCurveTransitionMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

object LightCurveInterpolator {

    const val MINUTES_PER_DAY = 24 * 60
    const val DEFAULT_SAMPLES_PER_RAMP = 24

    fun buildCurvePoints(
        startMinute: Int,
        peakStartMinute: Int,
        peakEndMinute: Int,
        endMinute: Int,
        peakPercent: Int,
        transitionMode: LightCurveTransitionMode,
        samplesPerRamp: Int = DEFAULT_SAMPLES_PER_RAMP
    ): List<LightCurveSample> {
        val safePeak = peakPercent.coerceIn(0, 100)

        val start = startMinute.coerceIn(0, MINUTES_PER_DAY)
        val peakStart = peakStartMinute.coerceIn(start, MINUTES_PER_DAY)
        val peakEnd = peakEndMinute.coerceIn(peakStart, MINUTES_PER_DAY)
        val end = endMinute.coerceIn(peakEnd, MINUTES_PER_DAY)

        val points = mutableListOf<LightCurveSample>()

        points.add(LightCurveSample(0, 0))
        points.add(LightCurveSample(start, 0))

        points.addAll(
            buildRamp(
                fromMinute = start,
                toMinute = peakStart,
                fromPercent = 0,
                toPercent = safePeak,
                transitionMode = transitionMode,
                samples = samplesPerRamp
            )
        )

        points.add(LightCurveSample(peakEnd, safePeak))

        points.addAll(
            buildRamp(
                fromMinute = peakEnd,
                toMinute = end,
                fromPercent = safePeak,
                toPercent = 0,
                transitionMode = transitionMode,
                samples = samplesPerRamp
            )
        )

        points.add(LightCurveSample(MINUTES_PER_DAY, 0))

        return points
            .groupBy { sample -> sample.x }
            .map { (_, sameMinute) ->
                sameMinute.maxBy { sample -> sample.y }
            }
            .sortedBy { sample -> sample.x }
    }

    private fun buildRamp(
        fromMinute: Int,
        toMinute: Int,
        fromPercent: Int,
        toPercent: Int,
        transitionMode: LightCurveTransitionMode,
        samples: Int
    ): List<LightCurveSample> {
        if (toMinute <= fromMinute) {
            return listOf(
                LightCurveSample(
                    x = toMinute,
                    y = toPercent.coerceIn(0, 100)
                )
            )
        }

        val safeSamples = samples.coerceAtLeast(2)

        return (1..safeSamples).map { index ->
            val t = index / safeSamples.toFloat()
            val eased = ease(t, transitionMode)

            val minute = fromMinute + (toMinute - fromMinute) * t
            val percent = fromPercent + (toPercent - fromPercent) * eased

            LightCurveSample(
                x = minute.roundToInt().coerceIn(0, MINUTES_PER_DAY),
                y = percent.roundToInt().coerceIn(0, 100)
            )
        }
    }

    private fun ease(
        t: Float,
        mode: LightCurveTransitionMode
    ): Float {
        val clamped = t.coerceIn(0f, 1f)

        return when (mode) {
            LightCurveTransitionMode.LINEAR -> clamped

            LightCurveTransitionMode.SMOOTH -> {
                clamped * clamped * (3f - 2f * clamped)
            }

            LightCurveTransitionMode.NATURAL -> {
                val cosineEase = ((1f - cos(clamped * PI)) / 2f).toFloat()
                cosineEase.pow(1.15f)
            }
        }
    }
}

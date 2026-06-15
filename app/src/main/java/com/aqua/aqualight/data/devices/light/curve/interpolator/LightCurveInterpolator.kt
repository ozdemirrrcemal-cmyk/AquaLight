package com.aqua.aqualight.data.devices.light.curve.interpolator

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveSample
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import kotlin.math.cos
import kotlin.math.pow

object LightCurveInterpolator {

    fun buildCurvePoints(
        startMinute: Int,
        peakStartMinute: Int,
        peakEndMinute: Int,
        endMinute: Int,
        peakPercent: Int,
        transitionMode: LightCurveTransitionMode,
        samplesPerRamp: Int = 24
    ): List<LightCurveSample> {
        val safePeak = peakPercent.coerceIn(0, 100)

        val start = startMinute.coerceIn(0, MINUTES_PER_DAY)
        val peakStart = peakStartMinute.coerceIn(start, MINUTES_PER_DAY)
        val peakEnd = peakEndMinute.coerceIn(peakStart, MINUTES_PER_DAY)
        val end = endMinute.coerceIn(peakEnd, MINUTES_PER_DAY)

        val points = mutableListOf<LightCurveSample>()

        points.add(LightCurveSample(minute = 0f, percent = 0f))
        points.add(LightCurveSample(minute = start.toFloat(), percent = 0f))

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

        points.add(
            LightCurveSample(
                minute = peakEnd.toFloat(),
                percent = safePeak.toFloat()
            )
        )

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

        points.add(
            LightCurveSample(
                minute = MINUTES_PER_DAY.toFloat(),
                percent = 0f
            )
        )

        return points
            .distinctBy { sample -> "${sample.minute}:${sample.percent}" }
            .sortedBy { sample -> sample.minute }
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
                    minute = toMinute.toFloat(),
                    percent = toPercent.toFloat()
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
                minute = minute,
                percent = percent
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
                // Smoothstep: softer start and softer end.
                clamped * clamped * (3f - 2f * clamped)
            }

            LightCurveTransitionMode.NATURAL -> {
                // More sunrise-like curve: very soft at the start, then catches up naturally.
                val cosineEase = ((1f - cos(clamped * Math.PI)) / 2f).toFloat()
                cosineEase.pow(1.15f)
            }
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

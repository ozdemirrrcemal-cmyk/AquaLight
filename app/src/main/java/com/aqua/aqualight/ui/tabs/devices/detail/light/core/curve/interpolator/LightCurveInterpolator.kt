package com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.interpolator

import android.graphics.PointF
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveTransitionMode
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
    ): List<PointF> {
        val safePeak = peakPercent.coerceIn(0, 100)

        val start = startMinute.coerceIn(0, 1440)
        val peakStart = peakStartMinute.coerceIn(start, 1440)
        val peakEnd = peakEndMinute.coerceIn(peakStart, 1440)
        val end = endMinute.coerceIn(peakEnd, 1440)

        val points = mutableListOf<PointF>()

        points.add(PointF(0f, 0f))
        points.add(PointF(start.toFloat(), 0f))

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

        points.add(PointF(peakEnd.toFloat(), safePeak.toFloat()))

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

        points.add(PointF(1440f, 0f))

        return points
            .distinctBy { "${it.x}:${it.y}" }
            .sortedBy { it.x }
    }

    private fun buildRamp(
        fromMinute: Int,
        toMinute: Int,
        fromPercent: Int,
        toPercent: Int,
        transitionMode: LightCurveTransitionMode,
        samples: Int
    ): List<PointF> {
        if (toMinute <= fromMinute) {
            return listOf(PointF(toMinute.toFloat(), toPercent.toFloat()))
        }

        val safeSamples = samples.coerceAtLeast(2)

        return (1..safeSamples).map { index ->
            val t = index / safeSamples.toFloat()
            val eased = ease(t, transitionMode)

            val minute = fromMinute + (toMinute - fromMinute) * t
            val percent = fromPercent + (toPercent - fromPercent) * eased

            PointF(minute, percent)
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
}
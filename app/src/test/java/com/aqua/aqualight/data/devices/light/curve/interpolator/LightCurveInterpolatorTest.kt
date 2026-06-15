package com.aqua.aqualight.data.devices.light.curve.interpolator

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightCurveInterpolatorTest {

    @Test
    fun buildCurvePoints_returnsAndroidFreeDomainSamplesInMinuteOrder() {
        val points = LightCurveInterpolator.buildCurvePoints(
            startMinute = 7 * 60,
            peakStartMinute = 9 * 60,
            peakEndMinute = 17 * 60,
            endMinute = 20 * 60,
            peakPercent = 80,
            transitionMode = LightCurveTransitionMode.LINEAR
        )

        assertEquals(0f, points.first().minute, 0.001f)
        assertEquals(0f, points.first().percent, 0.001f)
        assertEquals(1440f, points.last().minute, 0.001f)
        assertEquals(0f, points.last().percent, 0.001f)
        assertTrue(points.zipWithNext().all { pair -> pair.first.minute <= pair.second.minute })
    }

    @Test
    fun buildCurvePoints_clampsPeakPercentToSafeRange() {
        val points = LightCurveInterpolator.buildCurvePoints(
            startMinute = 60,
            peakStartMinute = 120,
            peakEndMinute = 180,
            endMinute = 240,
            peakPercent = 180,
            transitionMode = LightCurveTransitionMode.SMOOTH
        )

        assertTrue(points.all { point -> point.percent in 0f..100f })
    }
}

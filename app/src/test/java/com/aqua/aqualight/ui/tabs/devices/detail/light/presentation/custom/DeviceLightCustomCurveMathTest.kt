package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceLightCustomCurveMathTest {

    @Test
    fun `interpolation wraps smoothly across midnight`() {
        val points = listOf(
            point(hour = 4, percent = 20),
            point(hour = 20, percent = 60)
        )

        val midnight = points.interpolatedChannelsAt(0L, listOf(CHANNEL))

        assertEquals(40, midnight[CHANNEL])
    }

    @Test
    fun `chart hit testing selects only a nearby existing point`() {
        val points = listOf(point(hour = 6, percent = 10), point(hour = 18, percent = 70))

        assertEquals(
            6 * MILLIS_PER_HOUR,
            nearestVisiblePointTime(
                points = points,
                target = DeviceLightCustomHitTarget(
                    tapX = 27f,
                    chartWidth = 100f,
                    tolerancePx = 5f
                ),
                window = DeviceLightCustomChartWindow(0L, MILLIS_PER_DAY)
            )
        )
        assertNull(
            nearestVisiblePointTime(
                points = points,
                target = DeviceLightCustomHitTarget(
                    tapX = 50f,
                    chartWidth = 100f,
                    tolerancePx = 5f
                ),
                window = DeviceLightCustomChartWindow(0L, MILLIS_PER_DAY)
            )
        )
    }

    @Test
    fun `playhead maps and clamps the full day to minute precision`() {
        assertEquals(0L, playheadTimeForX(-10f, 100f))
        assertEquals(12 * MILLIS_PER_HOUR, playheadTimeForX(50f, 100f))
        assertEquals(MILLIS_PER_DAY - MILLIS_PER_MINUTE, playheadTimeForX(110f, 100f))
    }

    private fun point(hour: Int, percent: Int) = DeviceLightCustomPointUiState(
        timeMs = hour * MILLIS_PER_HOUR,
        channels = mapOf(CHANNEL to percent)
    )

    private companion object {
        val CHANNEL = DeviceLightCustomChannelId.BLUE
    }
}

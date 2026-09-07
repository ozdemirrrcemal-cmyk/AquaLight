package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.history

import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryChartSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingTemperatureHistoryChartModelTest {

    @Test
    fun timeAxisTicksRunFromNewestToOldest() {
        val ticks = historyTimeTickEpochMillis(OLDEST_TIME, NEWEST_TIME)

        assertEquals(AquaCoolingHistoryChartSpec.timeAxisLabelCount, ticks.size)
        assertEquals(NEWEST_TIME, ticks.first())
        assertEquals(OLDEST_TIME, ticks.last())
        assertTrue(ticks.zipWithNext().all { (left, right) -> left > right })
    }

    @Test
    fun seriesPlacesNewestSampleAtTheLeftEdge() {
        val timeSpan = (NEWEST_TIME - OLDEST_TIME).toDouble()

        assertEquals(
            RIGHT_EDGE_FRACTION,
            historyHorizontalFraction(OLDEST_TIME, OLDEST_TIME, timeSpan),
            NO_DELTA
        )
        assertEquals(
            CENTER_FRACTION,
            historyHorizontalFraction(MIDDLE_TIME, OLDEST_TIME, timeSpan),
            NO_DELTA
        )
        assertEquals(
            LEFT_EDGE_FRACTION,
            historyHorizontalFraction(NEWEST_TIME, OLDEST_TIME, timeSpan),
            NO_DELTA
        )
    }

    @Test
    fun defaultScaleIncludesEveryIntermediateDegree() {
        val scale = historyTemperatureScale(listOf(AQUARIUM_TEMPERATURE_C))
        val expected = (DEFAULT_MAXIMUM_C downTo DEFAULT_MINIMUM_C).map(Int::toFloat)

        assertEquals(expected, scale.axisValues())
    }

    private companion object {
        const val OLDEST_TIME = 100L
        const val MIDDLE_TIME = 300L
        const val NEWEST_TIME = 500L
        const val DEFAULT_MINIMUM_C = 21
        const val DEFAULT_MAXIMUM_C = 30
        const val AQUARIUM_TEMPERATURE_C = 25f
        const val LEFT_EDGE_FRACTION = 0f
        const val CENTER_FRACTION = 0.5f
        const val RIGHT_EDGE_FRACTION = 1f
        const val NO_DELTA = 0f
    }
}

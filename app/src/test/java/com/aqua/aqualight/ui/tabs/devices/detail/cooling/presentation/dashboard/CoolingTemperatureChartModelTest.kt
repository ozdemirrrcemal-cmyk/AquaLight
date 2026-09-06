package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingLiveTemperaturePointPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingTemperatureTimelinePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingTemperatureChartModelTest {

    @Test
    fun archiveUsesRealRollingTwentyFourHourPositions() {
        val values = temperatureChartValues(
            archivedPoints = listOf(
                historyPoint(NOW - TEMPERATURE_CHART_WINDOW_MILLIS, 24.0),
                historyPoint(NOW - 12L * 60L * 60L * 1_000L, 25.0),
                historyPoint(NOW, 26.0)
            ),
            historyGeneratedAtEpochMillis = NOW,
            liveTimeline = CoolingTemperatureTimelinePresentation()
        )

        assertEquals(0f, values[0].xFraction, 0.0001f)
        assertEquals(0.5f, values[1].xFraction, 0.0001f)
        assertEquals(1f, values[2].xFraction, 0.0001f)
    }

    @Test
    fun firstLiveSampleIsDrawnAtNowWithoutHistory() {
        val point = livePoint(sequence = 1L, sampledAt = 10_000L)
        val values = temperatureChartValues(
            archivedPoints = emptyList(),
            historyGeneratedAtEpochMillis = null,
            liveTimeline = CoolingTemperatureTimelinePresentation(
                committedLivePoints = listOf(point),
                currentLivePoint = point
            )
        )

        assertEquals(1, values.size)
        assertEquals(1f, values.single().xFraction, 0f)
        assertEquals(TemperatureChartSource.LIVE, values.single().source)
    }

    @Test
    fun liveSeriesIsAlwaysOrderedOldestToNewestWithHeadAtNow() {
        val oldest = livePoint(sequence = 1L, sampledAt = 1_000L, temperatureC = 24.5)
        val middle = livePoint(sequence = 2L, sampledAt = 301_000L, temperatureC = 25.0)
        val head = livePoint(sequence = 3L, sampledAt = 601_000L, temperatureC = 25.5)
        val values = temperatureChartValues(
            archivedPoints = emptyList(),
            historyGeneratedAtEpochMillis = null,
            liveTimeline = CoolingTemperatureTimelinePresentation(
                committedLivePoints = listOf(middle, oldest),
                currentLivePoint = head
            )
        )

        assertEquals(listOf(24.5f, 25.0f, 25.5f), values.map { it.temperatureC })
        assertTrue(values.zipWithNext().all { (left, right) -> left.xFraction <= right.xFraction })
        assertEquals(1f, values.last().xFraction, 0f)
        assertEquals(TemperatureChartSource.LIVE, values.last().source)
    }

    @Test
    fun longMissingIntervalBreaksFalseConnectingLine() {
        val values = listOf(
            TemperatureChartValue(0f, 24f, TemperatureChartSource.ARCHIVE),
            TemperatureChartValue(0.25f, 25f, TemperatureChartSource.ARCHIVE),
            TemperatureChartValue(1f, 26f, TemperatureChartSource.LIVE)
        )

        val segments = temperatureChartSegments(values)

        assertEquals(3, segments.size)
        assertTrue(segments.all { segment -> segment.size == 1 })
    }

    private fun historyPoint(epochMillis: Long, temperatureC: Double) =
        DeviceCoolingTemperatureHistoryPoint(
            sampledAtEpochMillis = epochMillis,
            temperatureC = temperatureC
        )

    private fun livePoint(
        sequence: Long,
        sampledAt: Long,
        temperatureC: Double = 25.0
    ) =
        CoolingLiveTemperaturePointPresentation(
            inputSampleSequence = sequence,
            sampledAtUptimeMillis = sampledAt,
            evaluatedAtUptimeMillis = sampledAt + 100L,
            temperatureC = temperatureC
        )

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}

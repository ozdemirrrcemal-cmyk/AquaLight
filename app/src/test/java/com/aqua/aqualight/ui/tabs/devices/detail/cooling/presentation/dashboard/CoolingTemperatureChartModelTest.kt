package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingLiveTemperaturePointPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingTemperatureTimelinePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingTemperatureChartModelTest {

    @Test
    fun archiveUsesConventionalPastToPresentRollingPositions() {
        val values = temperatureChartValues(
            archivedPoints = listOf(
                historyPoint(NOW - TEMPERATURE_CHART_WINDOW_MILLIS, 24.0),
                historyPoint(NOW - 12L * 60L * 60L * 1_000L, 25.0),
                historyPoint(NOW, 26.0)
            ),
            historyGeneratedAtEpochMillis = NOW,
            liveTimeline = CoolingTemperatureTimelinePresentation()
        )

        assertEquals(listOf(24f, 25f, 26f), values.map { it.temperatureC })
        assertEquals(0f, values[0].xFraction, 0.0001f)
        assertEquals(0.5f, values[1].xFraction, 0.0001f)
        assertEquals(1f, values[2].xFraction, 0.0001f)
    }

    @Test
    fun firstLiveSampleIsDrawnAtNowOnRightEdgeWithoutHistory() {
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

    @Test
    fun compactChartDropsTinyDetachedHistoryFragmentButKeepsCurrentSeries() {
        val values = listOf(
            TemperatureChartValue(0.35f, 25.4f, TemperatureChartSource.ARCHIVE),
            TemperatureChartValue(0.36f, 25.6f, TemperatureChartSource.ARCHIVE),
            TemperatureChartValue(0.55f, 25.8f, TemperatureChartSource.ARCHIVE),
            TemperatureChartValue(0.57f, 25.7f, TemperatureChartSource.ARCHIVE),
            TemperatureChartValue(0.59f, 25.9f, TemperatureChartSource.ARCHIVE),
            TemperatureChartValue(1f, 25.1f, TemperatureChartSource.LIVE)
        )

        val segments = renderableTemperatureChartSegments(values)

        assertEquals(2, segments.size)
        assertEquals(listOf(0.55f, 0.57f, 0.59f), segments.first().map { it.xFraction })
        assertEquals(1f, segments.last().single().xFraction, 0f)
    }

    @Test
    fun defaultTemperatureScaleRemainsAquariumFocused() {
        val scale = temperatureChartScale(listOf(24.8f, 25.9f, 27.1f))

        assertEquals(21f, scale.minimumC, 0f)
        assertEquals(30f, scale.maximumC, 0f)
        assertEquals(listOf(30f, 27f, 24f, 21f), scale.axisValues())
    }

    @Test
    fun highTemperatureExpandsScaleToQuantizedHeadroom() {
        val scale = temperatureChartScale(listOf(25.0f, 40.0f))

        assertEquals(21f, scale.minimumC, 0f)
        assertEquals(42f, scale.maximumC, 0f)
        assertEquals(listOf(42f, 35f, 28f, 21f), scale.axisValues())
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

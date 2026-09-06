package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingWaterTemperatureSample
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingTemperatureTimelinePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingTemperatureTimelineStateTest {

    @Test
    fun firstValidFirmwareSampleIsAvailableImmediately() {
        val update = CoolingTemperatureTimelinePresentation().accept(sample(sequence = 1L))

        assertFalse(update.sourceReset)
        assertEquals(1, update.state.committedLivePoints.size)
        assertEquals(25.0, update.state.currentLivePoint?.temperatureC ?: 0.0, 0.0)
    }

    @Test
    fun sameTemperatureWithNewSequenceAdvancesHeadWithoutDensePoint() {
        val first = CoolingTemperatureTimelinePresentation().accept(
            sample(sequence = 1L, sampledAt = 1_000L)
        ).state
        val second = first.accept(
            sample(sequence = 2L, sampledAt = 4_000L)
        ).state

        assertEquals(1, second.committedLivePoints.size)
        assertEquals(2L, second.currentLivePoint?.inputSampleSequence)
    }

    @Test
    fun fiveMinuteBoundaryCommitsCleanLivePoint() {
        val first = CoolingTemperatureTimelinePresentation().accept(
            sample(sequence = 1L, sampledAt = 1_000L)
        ).state
        val next = first.accept(
            sample(sequence = 2L, sampledAt = 301_000L, temperatureC = 25.5)
        ).state

        assertEquals(2, next.committedLivePoints.size)
        assertEquals(25.5, next.committedLivePoints.last().temperatureC, 0.0)
    }

    @Test
    fun repeatedSampleIdentityIsIgnored() {
        val first = CoolingTemperatureTimelinePresentation().accept(sample(sequence = 7L)).state
        val repeated = first.accept(sample(sequence = 7L))

        assertFalse(repeated.sourceReset)
        assertEquals(first, repeated.state)
    }

    @Test
    fun clockGenerationChangeStartsNewLiveSegment() {
        val first = CoolingTemperatureTimelinePresentation().accept(
            sample(sequence = 1L, timeGeneration = 3L)
        ).state.withHistoryAnchor(1_000_000L)
        val reset = first.accept(
            sample(
                sequence = 2L,
                sampledAt = 4_000L,
                timeGeneration = 4L
            )
        )

        assertTrue(reset.sourceReset)
        assertEquals(1, reset.state.committedLivePoints.size)
        assertEquals(null, reset.state.historyAnchorEpochMillis)
    }

    private fun sample(
        sequence: Long,
        sampledAt: Long = 1_000L,
        timeGeneration: Long = 1L,
        temperatureC: Double = 25.0
    ) = DeviceCoolingWaterTemperatureSample(
        inputSampleSequence = sequence,
        sampledAtUptimeMillis = sampledAt,
        evaluatedAtUptimeMillis = sampledAt + 100L,
        timeGeneration = timeGeneration,
        temperatureC = temperatureC
    )
}

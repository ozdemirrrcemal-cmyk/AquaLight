package com.aqua.aqualight.application.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObservationWaterEvidencePolicyTest {
    private val window = ObservationWaterWindow(100L, "test-window-v1")

    @Test
    fun boundaryIsInclusiveAndAnOlderOrFutureSampleIsNotEligible() {
        assertEquals(ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION, resolve(900).relation)
        assertEquals(ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION, resolve(1000).relation)
        assertEquals(ObservationWaterRelation.OUTSIDE_WINDOW, resolve(899).relation)
        assertEquals(ObservationWaterRelation.OUTSIDE_WINDOW, resolve(1001).relation)
        assertEquals(-1L, resolve(1001).ageAtObservationMillis)
    }

    @Test
    fun wrongTankCannotBecomeLinkedEvidenceAndMissingIsExplicit() {
        val sample = WaterAnalysisSnapshot(1, 3, 950, null, null, emptyList(), 1000)
        assertThrows(IllegalArgumentException::class.java) {
            ObservationWaterEvidencePolicy.resolve(2, 1000, sample, window)
        }
        assertEquals(ObservationWaterRelation.MISSING,
            ObservationWaterEvidencePolicy.resolve(2, 1000, null, window).relation)
    }

    private fun resolve(measuredAt: Long) = ObservationWaterEvidencePolicy.resolve(2, 1000,
        WaterAnalysisSnapshot(1, 2, measuredAt, null, null, emptyList(), 1000), window)
}

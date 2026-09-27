package com.aqua.aqualight.application.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.algae.AlgaeAssessmentEngine
import com.aqua.aqualight.application.aquarium.health.plant.PlantHealthAssessmentEngine
import com.aqua.aqualight.application.aquarium.health.livestock.LivestockHealthAssessmentEngine
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.context.HealthLivestockContext
import com.aqua.aqualight.application.aquarium.health.context.HealthMaterialContext
import com.aqua.aqualight.application.aquarium.health.context.HealthPlantContext
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthObservationEnginesTest {
    private val context = AquariumHealthContext(HealthContextCapture(1000, "test-context-v1"),
        HealthTankFacts(2, "Freshwater Fish", "Freshwater", null, null),
        HealthCatalogRevisions("plants-v1", "animals-v1"),
        listOf(HealthPlantContext(3, "same-catalog", "plant", HealthEntityResolution.PARTIAL, null)),
        listOf(HealthLivestockContext(4, "custom", "animal", 2, HealthEntityResolution.CUSTOM_UNVERIFIED, null, null)),
        listOf(HealthMaterialContext(1, "light-product", "lighting")))
    private val water = ObservationWaterEvidence(null, null, ObservationWaterRelation.MISSING, "test-window-v1", 100)

    @Test
    fun installedLightDoesNotSupplyMeasuredOperatingEvidence() {
        val observation = HealthObservation.Algae(setOf(AlgaeLocation.PLANT), setOf(AlgaeAppearance.THREADS),
            AlgaeExtent.LOCAL, ObservationOperatingEvidence(null, "", ReportedCo2Pattern.UNKNOWN, ""))
        val result = AlgaeAssessmentEngine.assess(observation, context, water)
        assertTrue(result.gaps.containsAll(setOf(ObservationGap.LIGHT_SCHEDULE_MISSING,
            ObservationGap.LIGHT_MEASUREMENT_MISSING, ObservationGap.CO2_PATTERN_UNKNOWN,
            ObservationGap.DOSING_HISTORY_MISSING)))
        assertEquals(result, AlgaeAssessmentEngine.assess(observation, context, water))
        assertTrue(result.waterFindings.isEmpty())
    }

    @Test
    fun plantAlgaeIsAnObservationAndRoutesToTheTankEngine() {
        val result = PlantHealthAssessmentEngine.assess(
            HealthObservation.Plant(3, setOf(PlantFinding.ALGAE_PRESENT)), context, water)
        assertTrue(ObservationAction.OPEN_ALGAE_CONTROL in result.actions)
        assertTrue(ObservationGap.PARTIAL_CARE_PROFILE in result.gaps)
        assertEquals(ObservationAssessmentState.REVIEW_NEEDED, result.state)
    }

    @Test
    fun removedLocalPlantIsNotAdoptedByAnotherRecordWithTheSameCatalog() {
        val result = PlantHealthAssessmentEngine.assess(
            HealthObservation.Plant(99, setOf(PlantFinding.LEAF_DAMAGE)), context, water)
        assertTrue(ObservationGap.SUBJECT_REMOVED in result.gaps)
        assertEquals(ObservationAssessmentState.INSUFFICIENT_DATA, result.state)
    }

    @Test
    fun affectedQuantityCannotExceedTheRegisteredGroup() {
        assertThrows(IllegalArgumentException::class.java) {
            LivestockHealthAssessmentEngine.assess(HealthObservation.Livestock(4, 3,
                setOf(LivestockFinding.BEHAVIOR_CHANGE)), context, water)
        }
        val result = LivestockHealthAssessmentEngine.assess(HealthObservation.Livestock(4, null,
            setOf(LivestockFinding.UNKNOWN)), context, water)
        assertTrue(ObservationGap.SUBJECT_UNVERIFIED in result.gaps)
        assertTrue(ObservationGap.AFFECTED_QUANTITY_UNKNOWN in result.gaps)
        assertEquals(ObservationAssessmentState.INSUFFICIENT_DATA, result.state)
    }
}

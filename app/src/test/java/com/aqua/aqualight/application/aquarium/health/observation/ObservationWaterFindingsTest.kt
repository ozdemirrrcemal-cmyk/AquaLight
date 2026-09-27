package com.aqua.aqualight.application.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentCoverage
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntity
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessment
import com.aqua.aqualight.application.aquarium.health.water.WaterRuleFinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationWaterFindingsTest {
    private val context = AquariumHealthContext(HealthContextCapture(1000, "context-v1"),
        HealthTankFacts(2, "Freshwater Fish", "Freshwater", null, null), HealthCatalogRevisions(null, null),
        emptyList(), emptyList(), emptyList())
    private val plant = finding(WaterAssessmentEntityKind.PLANT, 3)
    private val otherPlant = finding(WaterAssessmentEntityKind.PLANT, 4)
    private val animal = finding(WaterAssessmentEntityKind.LIVESTOCK, 3)
    private val assessment = WaterQualityAssessment("test", "test-rules", context.revision, null,
        WaterAssessmentCoverage.NONE, WaterAssessmentCoverage.NONE,
        listOf(plant, otherPlant, animal), emptyList(), emptyList())
    private val sample = WaterAnalysisSnapshot(7, 2, 900, null, null, emptyList(), 950, assessment)
    private val evidence = ObservationWaterEvidence(sample, 100,
        ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION, "test-window", 100)

    @Test
    fun findingsAreReusedOnlyForTheExactEntityKindAndLocalIdentity() {
        assertEquals(listOf(plant), observationWaterFindings(evidence, context, WaterAssessmentEntityKind.PLANT, 3))
        assertEquals(listOf(animal),
            observationWaterFindings(evidence, context, WaterAssessmentEntityKind.LIVESTOCK, 3))
    }

    @Test
    fun staleAndDifferentContextFindingsRemainHistoricalWithoutCurrentAdvice() {
        val stale = evidence.copy(relation = ObservationWaterRelation.OUTSIDE_WINDOW)
        assertTrue(observationWaterFindings(stale, context, WaterAssessmentEntityKind.PLANT, 3).isEmpty())
        val changed = evidence.copy(analysis = sample.copy(assessment = assessment.copy(contextRevision = "older")))
        assertTrue(observationWaterFindings(changed, context, WaterAssessmentEntityKind.PLANT, 3).isEmpty())
        assertTrue(ObservationGap.WATER_CONTEXT_DIFFERS in observationWaterGaps(changed, context))
        assertEquals(listOf(plant, otherPlant, animal), changed.analysis?.assessment?.findings)
    }

    private fun finding(kind: WaterAssessmentEntityKind, id: Long) = WaterRuleFinding(
        WaterAssessmentEntity(kind, id, "shared-catalog"), AquariumWaterParameter.PH,
        "$kind-$id", "test-rule", "catalog-v1", null, null, null, null, null)
}

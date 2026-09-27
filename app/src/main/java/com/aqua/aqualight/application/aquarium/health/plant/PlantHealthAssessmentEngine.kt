package com.aqua.aqualight.application.aquarium.health.plant

import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPolicy
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterEvidence
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessment
import com.aqua.aqualight.application.aquarium.health.observation.observationWaterGaps
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import com.aqua.aqualight.application.aquarium.health.observation.ObservationGap
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAction
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessmentState
import com.aqua.aqualight.application.aquarium.health.observation.observationWaterFindings
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import java.util.Collections

/** Catalog care and water findings remain evidence; symptoms never become a disease diagnosis. */
object PlantHealthAssessmentEngine {
    const val REVISION = "plant-observation-v1"

    fun assess(input: HealthObservation.Plant, context: AquariumHealthContext,
        water: ObservationWaterEvidence): ObservationAssessment {
        val observation = HealthObservationPolicy.freeze(input) as HealthObservation.Plant
        require(water.analysis == null || water.analysis.tankId == context.tankId)
        val subject = context.plants.singleOrNull { it.plantId == observation.plantId }
        val gaps = observationWaterGaps(water, context).toMutableSet()
        when {
            subject == null -> gaps += ObservationGap.SUBJECT_REMOVED
            subject.resolution == HealthEntityResolution.PARTIAL -> gaps += ObservationGap.PARTIAL_CARE_PROFILE
            subject.resolution != HealthEntityResolution.RESOLVED || subject.care == null ->
                gaps += ObservationGap.SUBJECT_UNVERIFIED
        }
        if (PlantFinding.UNKNOWN in observation.findings) gaps += ObservationGap.UNCERTAIN_OBSERVATION
        val actions = mutableSetOf(ObservationAction.RECORD_FOLLOW_UP, ObservationAction.REVIEW_WATER_ANALYSIS)
        if (PlantFinding.ALGAE_PRESENT in observation.findings) actions += ObservationAction.OPEN_ALGAE_CONTROL
        if (subject?.resolution != HealthEntityResolution.RESOLVED) actions += ObservationAction.VERIFY_SUBJECT_PROFILE
        val findings = observationWaterFindings(water, context, WaterAssessmentEntityKind.PLANT,
            observation.plantId)
        val state = if (subject == null || PlantFinding.UNKNOWN in observation.findings)
            ObservationAssessmentState.INSUFFICIENT_DATA else ObservationAssessmentState.REVIEW_NEEDED
        return ObservationAssessment(REVISION, state, Collections.unmodifiableSet(gaps),
            Collections.unmodifiableSet(actions), Collections.unmodifiableList(findings))
    }
}

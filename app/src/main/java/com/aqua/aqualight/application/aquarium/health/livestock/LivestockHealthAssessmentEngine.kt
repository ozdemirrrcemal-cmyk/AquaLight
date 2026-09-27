package com.aqua.aqualight.application.aquarium.health.livestock

import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPolicy
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterEvidence
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessment
import com.aqua.aqualight.application.aquarium.health.observation.observationWaterGaps
import com.aqua.aqualight.application.aquarium.health.observation.LivestockFinding
import com.aqua.aqualight.application.aquarium.health.observation.ObservationGap
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAction
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessmentState
import com.aqua.aqualight.application.aquarium.health.observation.observationWaterFindings
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import java.util.Collections

/** Reuses recorded water compatibility; there is no second range comparator or veterinary diagnosis. */
object LivestockHealthAssessmentEngine {
    const val REVISION = "livestock-observation-v1"

    fun assess(input: HealthObservation.Livestock, context: AquariumHealthContext,
        water: ObservationWaterEvidence): ObservationAssessment {
        val observation = HealthObservationPolicy.freeze(input) as HealthObservation.Livestock
        require(water.analysis == null || water.analysis.tankId == context.tankId)
        val subject = context.livestock.singleOrNull { it.livestockId == observation.livestockId }
        require(subject == null || observation.affectedQuantity == null ||
            observation.affectedQuantity <= subject.quantity)
        val gaps = observationWaterGaps(water, context).toMutableSet()
        if (subject == null) gaps += ObservationGap.SUBJECT_REMOVED
        else if (subject.resolution != HealthEntityResolution.RESOLVED) gaps += ObservationGap.SUBJECT_UNVERIFIED
        if (observation.affectedQuantity == null) gaps += ObservationGap.AFFECTED_QUANTITY_UNKNOWN
        if (LivestockFinding.UNKNOWN in observation.findings) gaps += ObservationGap.UNCERTAIN_OBSERVATION
        val actions = mutableSetOf(ObservationAction.RECORD_FOLLOW_UP, ObservationAction.REVIEW_WATER_ANALYSIS,
            ObservationAction.REVIEW_LIVESTOCK_CONDITION)
        if (subject?.resolution != HealthEntityResolution.RESOLVED) actions += ObservationAction.VERIFY_SUBJECT_PROFILE
        val findings = observationWaterFindings(water, context, WaterAssessmentEntityKind.LIVESTOCK,
            observation.livestockId)
        val state = if (subject == null || LivestockFinding.UNKNOWN in observation.findings)
            ObservationAssessmentState.INSUFFICIENT_DATA else ObservationAssessmentState.REVIEW_NEEDED
        return ObservationAssessment(REVISION, state, Collections.unmodifiableSet(gaps),
            Collections.unmodifiableSet(actions), Collections.unmodifiableList(findings))
    }
}

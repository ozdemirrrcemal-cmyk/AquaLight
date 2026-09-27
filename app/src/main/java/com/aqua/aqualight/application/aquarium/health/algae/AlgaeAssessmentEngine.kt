package com.aqua.aqualight.application.aquarium.health.algae

import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPolicy
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterEvidence
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessment
import com.aqua.aqualight.application.aquarium.health.observation.observationWaterGaps
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeLocation
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeAppearance
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeExtent
import com.aqua.aqualight.application.aquarium.health.observation.ObservationGap
import com.aqua.aqualight.application.aquarium.health.observation.ReportedCo2Pattern
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAction
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessmentState
import java.util.Collections

/** Assesses documented observations and missing evidence, not algae species or treatment doses. */
object AlgaeAssessmentEngine {
    const val REVISION = "algae-observation-v1"

    fun assess(input: HealthObservation.Algae, context: AquariumHealthContext,
        water: ObservationWaterEvidence): ObservationAssessment {
        val observation = HealthObservationPolicy.freeze(input) as HealthObservation.Algae
        require(water.analysis == null || water.analysis.tankId == context.tankId)
        val gaps = observationWaterGaps(water, context).toMutableSet()
        if (AlgaeLocation.UNKNOWN in observation.locations || AlgaeAppearance.UNKNOWN in observation.appearances ||
            observation.extent == AlgaeExtent.UNKNOWN) gaps += ObservationGap.UNCERTAIN_OBSERVATION
        val operating = observation.operatingEvidence
        if (operating.photoperiodMinutes == null) gaps += ObservationGap.LIGHT_SCHEDULE_MISSING
        if (operating.lightMeasurement.isBlank()) gaps += ObservationGap.LIGHT_MEASUREMENT_MISSING
        if (operating.co2Pattern == ReportedCo2Pattern.UNKNOWN) gaps += ObservationGap.CO2_PATTERN_UNKNOWN
        if (operating.dosingHistory.isBlank()) gaps += ObservationGap.DOSING_HISTORY_MISSING
        val actions = setOf(ObservationAction.RECORD_FOLLOW_UP, ObservationAction.REVIEW_WATER_ANALYSIS,
            ObservationAction.RECORD_OPERATING_OBSERVATIONS)
        val state = if (ObservationGap.UNCERTAIN_OBSERVATION in gaps) ObservationAssessmentState.INSUFFICIENT_DATA
            else ObservationAssessmentState.REVIEW_NEEDED
        return ObservationAssessment(REVISION, state, Collections.unmodifiableSet(gaps),
            Collections.unmodifiableSet(actions), emptyList())
    }
}

package com.aqua.aqualight.application.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.water.WaterRuleFinding
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind

/** These states describe observation coverage; none declares an animal/plant healthy or diagnoses disease. */
enum class ObservationAssessmentState { RECORDED, REVIEW_NEEDED, INSUFFICIENT_DATA }
enum class ObservationGap {
    UNCERTAIN_OBSERVATION, WATER_MISSING, WATER_OUTSIDE_WINDOW, WATER_CONTEXT_DIFFERS,
    SUBJECT_REMOVED, SUBJECT_UNVERIFIED, PARTIAL_CARE_PROFILE, AFFECTED_QUANTITY_UNKNOWN,
    LIGHT_SCHEDULE_MISSING, LIGHT_MEASUREMENT_MISSING, CO2_PATTERN_UNKNOWN, DOSING_HISTORY_MISSING
}
enum class ObservationAction {
    RECORD_FOLLOW_UP, REVIEW_WATER_ANALYSIS, VERIFY_SUBJECT_PROFILE, OPEN_ALGAE_CONTROL,
    RECORD_OPERATING_OBSERVATIONS, REVIEW_LIVESTOCK_CONDITION
}

data class ObservationWaterWindow(val maximumAgeMillis: Long, val revision: String) {
    init { require(maximumAgeMillis >= 0L && revision.isNotBlank()) }
}
enum class ObservationWaterRelation { AVAILABLE_BEFORE_OBSERVATION, OUTSIDE_WINDOW, MISSING }

data class ObservationWaterEvidence(
    val analysis: WaterAnalysisSnapshot?,
    val ageAtObservationMillis: Long?,
    val relation: ObservationWaterRelation,
    val policyRevision: String
)

data class ObservationAssessment(
    val engineRevision: String,
    val state: ObservationAssessmentState,
    val gaps: Set<ObservationGap>,
    val actions: Set<ObservationAction>,
    val waterFindings: List<WaterRuleFinding>
)

/** No nearest-time join, no future record, and no claim that a historic sample describes current water. */
object ObservationWaterEvidencePolicy {
    fun resolve(tankId: Long, observedAtMillis: Long, analysis: WaterAnalysisSnapshot?,
        window: ObservationWaterWindow): ObservationWaterEvidence {
        require(tankId > 0L && observedAtMillis > 0L)
        require(analysis == null || analysis.tankId == tankId)
        val age = analysis?.let { Math.subtractExact(observedAtMillis, it.measuredAtMillis) }
        val relation = when {
            age == null -> ObservationWaterRelation.MISSING
            age !in 0L..window.maximumAgeMillis -> ObservationWaterRelation.OUTSIDE_WINDOW
            else -> ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION
        }
        return ObservationWaterEvidence(analysis, age, relation, window.revision)
    }
}

internal fun observationWaterGaps(evidence: ObservationWaterEvidence,
    context: AquariumHealthContext): Set<ObservationGap> =
    buildSet {
        when (evidence.relation) {
            ObservationWaterRelation.MISSING -> add(ObservationGap.WATER_MISSING)
            ObservationWaterRelation.OUTSIDE_WINDOW -> add(ObservationGap.WATER_OUTSIDE_WINDOW)
            ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION -> Unit
        }
        if (evidence.analysis?.assessment?.contextRevision != context.revision && evidence.analysis != null) {
            add(ObservationGap.WATER_CONTEXT_DIFFERS)
        }
    }

/** Retain the original snapshot for history but never reuse stale/different-context findings as current advice. */
internal fun observationWaterFindings(evidence: ObservationWaterEvidence, context: AquariumHealthContext,
    kind: WaterAssessmentEntityKind, localId: Long): List<WaterRuleFinding> =
    if (evidence.relation == ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION &&
        evidence.analysis?.assessment?.contextRevision == context.revision) {
        evidence.analysis.assessment.findings.filter { it.entity.kind == kind && it.entity.localId == localId }
    } else emptyList()

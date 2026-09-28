package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAssessmentState
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterRelation
import com.aqua.aqualight.i18n.LocaleFormatter

internal object HealthObservationPresentation {
    fun state(value: ObservationAssessmentState) = when (value) {
        ObservationAssessmentState.RECORDED -> R.string.health_state_recorded
        ObservationAssessmentState.REVIEW_NEEDED -> R.string.health_state_review
        ObservationAssessmentState.INSUFFICIENT_DATA -> R.string.health_state_insufficient
    }

    fun findings(context: Context, observation: HealthObservation): String {
        val selected: List<Enum<*>> = when (observation) {
            is HealthObservation.Algae -> observation.locations.toList() + observation.appearances + observation.extent
            is HealthObservation.Plant -> observation.findings.toList()
            is HealthObservation.Livestock -> observation.findings.toList()
        }
        return selected.joinToString(" · ") { context.getString(HealthObservationLabels.label(it)) }
    }

    fun assessment(context: Context, row: HealthObservationSnapshot): String = buildList {
        add(context.getString(state(row.assessment.state)))
        row.subject?.let { subject ->
            val resolution = when (subject.resolution) {
                HealthEntityResolution.RESOLVED -> R.string.health_resolution_resolved
                HealthEntityResolution.PARTIAL -> R.string.health_resolution_partial
                else -> R.string.health_resolution_unverified
            }
            add(context.getString(R.string.health_profile_partial, context.getString(resolution)))
        }
        row.assessment.gaps.forEach { add(context.getString(HealthObservationLabels.label(it))) }
        row.assessment.actions.forEach { add(context.getString(HealthObservationLabels.label(it))) }
        add(context.getString(R.string.health_water_findings,
            LocaleFormatter.formatInteger(context, row.assessment.waterFindings.size)))
        addAll(WaterAssessmentFindingPresentation.findingLines(context, row.assessment.waterFindings))
    }.joinToString("\n\n")

    fun evidence(context: Context, row: HealthObservationSnapshot): String = buildList {
        addAll(reportedInputs(context, row))
        add(context.getString(R.string.health_context_time,
            LocaleFormatter.formatDateTime(context, row.evidence.context.capturedAtMillis)))
        val relation = when (row.evidence.relation) {
            ObservationWaterRelation.MISSING -> R.string.health_water_missing
            ObservationWaterRelation.OUTSIDE_WINDOW -> R.string.health_water_old
            ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION -> R.string.health_water_linked
        }
        add(context.getString(relation))
        row.evidence.water?.let { water ->
            add(context.getString(R.string.health_water_sample,
                LocaleFormatter.formatDateTime(context, water.identity.measuredAtMillis)))
            water.measurements.forEach { value ->
                add(listOf(context.getString(WaterAnalysisPresentation.parameterNameRes(value.parameter)),
                    WaterAnalysisPresentation.measurementValueText(context, value),
                    WaterAnalysisPresentation.measurementMetaText(context, value)).joinToString(" · "))
            }
        }
    }.joinToString("\n\n")

    private fun reportedInputs(context: Context, row: HealthObservationSnapshot): List<String> = buildList {
        val input = row.input.observation
        if (input is HealthObservation.Algae) {
            val operating = input.operatingEvidence
            operating.photoperiodMinutes?.let {
                add(context.getString(R.string.health_reported_photoperiod, LocaleFormatter.formatInteger(context, it)))
            }
            if (operating.lightMeasurement.isNotBlank()) {
                add(context.getString(R.string.health_reported_light, operating.lightMeasurement))
            }
            if (operating.dosingHistory.isNotBlank()) {
                add(context.getString(R.string.health_reported_dosing, operating.dosingHistory))
            }
            add(context.getString(R.string.health_reported_co2,
                context.getString(HealthObservationLabels.label(operating.co2Pattern))))
        }
        if (input is HealthObservation.Livestock) {
            val count = input.affectedQuantity?.let { LocaleFormatter.formatInteger(context, it) }
                ?: context.getString(R.string.health_algaeextent_unknown)
            val registered = row.subject?.quantity?.let { LocaleFormatter.formatInteger(context, it) }
                ?: context.getString(R.string.health_algaeextent_unknown)
            add(context.getString(R.string.health_affected_count, count, registered))
        }
    }
}

package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSemanticStatus
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentDirection
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentGap
import com.aqua.aqualight.application.aquarium.health.water.WaterRuleFinding
import com.aqua.aqualight.i18n.LocaleFormatter

internal object TankHealthWaterMetricDetailPresentation {

    @StringRes
    fun metricNameRes(id: TankHealthWaterMetricId): Int =
        when (id) {
            TankHealthWaterMetricId.Temperature -> R.string.tank_health_metric_temperature
            is TankHealthWaterMetricId.Parameter ->
                WaterAnalysisPresentation.parameterNameRes(id.value.toDomainParameter())
        }

    fun valueText(
        context: Context,
        record: WaterAnalysisSnapshot,
        id: TankHealthWaterMetricId
    ): String? =
        when (id) {
            TankHealthWaterMetricId.Temperature ->
                record.temperatureCelsius?.let { value ->
                    WaterAnalysisPresentation.temperatureValueText(context, value)
                }
            is TankHealthWaterMetricId.Parameter -> record.measurements
                .firstOrNull { measurement ->
                    measurement.parameter == id.value.toDomainParameter()
                }
                ?.let { measurement ->
                    WaterAnalysisPresentation.measurementValueText(context, measurement)
                }
        }

    fun summary(
        record: WaterAnalysisSnapshot,
        id: TankHealthWaterMetricId
    ): TankHealthWaterCompatibilitySummary {
        val parameter = assessmentParameter(record, id)
        return TankHealthWaterMetricAssessment.summarize(record.assessment, parameter)
    }

    fun findings(
        record: WaterAnalysisSnapshot,
        id: TankHealthWaterMetricId
    ): List<WaterRuleFinding> {
        val parameter = assessmentParameter(record, id)
        return TankHealthWaterMetricAssessment.findings(record.assessment, parameter)
    }

    private fun assessmentParameter(
        record: WaterAnalysisSnapshot,
        id: TankHealthWaterMetricId
    ): AquariumWaterParameter? =
        when (id) {
            TankHealthWaterMetricId.Temperature -> AquariumWaterParameter.TEMPERATURE_C
            is TankHealthWaterMetricId.Parameter -> {
                val domainParameter = id.value.toDomainParameter()
                val measurement = record.measurements.firstOrNull { candidate ->
                    candidate.parameter == domainParameter
                }
                if (measurement?.semanticStatus == WaterMeasurementSemanticStatus.LEGACY_UNASSESSED) {
                    null
                } else {
                    TankHealthWaterMetricAssessment.assessmentParameter(domainParameter)
                }
            }
        }

    @StringRes
    fun entityKindRes(finding: WaterRuleFinding): Int =
        if (finding.entity.kind == WaterAssessmentEntityKind.PLANT) {
            R.string.tank_health_metric_detail_entity_plant
        } else {
            R.string.tank_health_metric_detail_entity_livestock
        }

    fun entityName(finding: WaterRuleFinding): String =
        finding.entity.displayName.ifBlank { finding.entity.catalogId }

    @StringRes
    fun findingResultRes(finding: WaterRuleFinding): Int =
        when (finding.direction) {
            WaterAssessmentDirection.WITHIN ->
                R.string.tank_health_metric_detail_result_suitable
            WaterAssessmentDirection.ABOVE ->
                R.string.tank_health_metric_detail_result_high
            WaterAssessmentDirection.BELOW ->
                R.string.tank_health_metric_detail_result_low
            null -> R.string.tank_health_metric_detail_result_unassessed
        }

    fun findingColorRes(finding: WaterRuleFinding): Int =
        when (finding.direction) {
            WaterAssessmentDirection.WITHIN -> R.color.snackbar_success
            WaterAssessmentDirection.ABOVE,
            WaterAssessmentDirection.BELOW -> R.color.snackbar_error
            null -> R.color.aqua_content_muted
        }

    fun rangeText(context: Context, finding: WaterRuleFinding): String? {
        val range = finding.expectedRange ?: return null
        val locale = LocaleFormatter.appLocale(context)
        val bounds = buildList {
            range.minimum?.let { minimum ->
                val operator = if (range.minimumInclusive) "≥" else ">"
                add("$operator ${WaterAnalysisPresentation.formatMeasuredNumber(minimum, locale)}")
            }
            range.maximum?.let { maximum ->
                val operator = if (range.maximumInclusive) "≤" else "<"
                add("$operator ${WaterAnalysisPresentation.formatMeasuredNumber(maximum, locale)}")
            }
        }
        if (bounds.isEmpty()) return null

        val unit = unitText(context, finding.parameter)
        return bounds.joinToString(" · ").let { text ->
            if (unit.isBlank()) text else "$text $unit"
        }
    }

    @StringRes
    fun gapReasonRes(gap: WaterAssessmentGap?): Int =
        when (gap) {
            WaterAssessmentGap.MEASUREMENT_MISSING -> R.string.water_gap_measurement
            WaterAssessmentGap.REQUIREMENT_MISSING -> R.string.water_gap_requirement
            WaterAssessmentGap.UNPARSEABLE_REQUIREMENT -> R.string.water_gap_parse
            WaterAssessmentGap.INFORMATIONAL -> R.string.water_gap_informational
            WaterAssessmentGap.SOURCE_UNRESOLVED -> R.string.water_gap_source
            WaterAssessmentGap.CATALOG_UNAVAILABLE -> R.string.water_gap_catalog
            WaterAssessmentGap.CATALOG_ENTRY_MISSING -> R.string.water_gap_identity
            WaterAssessmentGap.CUSTOM_UNVERIFIED -> R.string.water_gap_custom
            WaterAssessmentGap.PARTIAL_PLANT -> R.string.water_gap_plant
            WaterAssessmentGap.UNKNOWN_TANK_TYPE -> R.string.water_gap_tank
            WaterAssessmentGap.UNKNOWN_REQUIREMENT_SEMANTIC -> R.string.water_gap_semantic
            WaterAssessmentGap.RULE_MISSING -> R.string.water_gap_rule
            WaterAssessmentGap.INCOMPATIBLE_HABITAT -> R.string.water_gap_habitat
            WaterAssessmentGap.UNKNOWN_HABITAT -> R.string.water_gap_unknown_habitat
            null -> R.string.tank_health_metric_detail_result_unassessed
        }

    private fun unitText(context: Context, parameter: AquariumWaterParameter): String =
        when (parameter) {
            AquariumWaterParameter.TEMPERATURE_C ->
                context.getString(R.string.tank_health_analysis_temperature_unit)
            AquariumWaterParameter.GH_DGH ->
                context.getString(R.string.tank_health_analysis_unit_dgh)
            AquariumWaterParameter.KH_DKH,
            AquariumWaterParameter.ALKALINITY_DKH ->
                context.getString(R.string.tank_health_analysis_unit_dkh)
            AquariumWaterParameter.TDS_PPM,
            AquariumWaterParameter.CALCIUM_PPM,
            AquariumWaterParameter.MAGNESIUM_PPM ->
                context.getString(R.string.tank_health_analysis_unit_ppm)
            AquariumWaterParameter.NITRATE_PPM,
            AquariumWaterParameter.PHOSPHATE_PPM ->
                context.getString(R.string.tank_health_analysis_unit_mg_l)
            AquariumWaterParameter.PH,
            AquariumWaterParameter.SPECIFIC_GRAVITY,
            AquariumWaterParameter.PAR -> ""
        }
}

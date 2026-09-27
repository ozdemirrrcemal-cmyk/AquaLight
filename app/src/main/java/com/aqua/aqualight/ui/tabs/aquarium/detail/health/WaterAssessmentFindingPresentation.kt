package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentDirection
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntity
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentGap
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessment
import com.aqua.aqualight.i18n.LocaleFormatter

/** Uses only the frozen event. Names and ranges never come from the current tank or catalog. */
internal object WaterAssessmentFindingPresentation {
    private val parameters = mapOf(
        AquariumWaterParameter.TEMPERATURE_C to R.string.tank_health_metric_temperature,
        AquariumWaterParameter.PH to R.string.tank_health_test_ph,
        AquariumWaterParameter.GH_DGH to R.string.tank_health_test_general_hardness,
        AquariumWaterParameter.NITRATE_PPM to R.string.tank_health_test_nitrate,
        AquariumWaterParameter.PHOSPHATE_PPM to R.string.tank_health_test_phosphate
    )
    private val gaps = mapOf(
        WaterAssessmentGap.MEASUREMENT_MISSING to R.string.water_gap_measurement,
        WaterAssessmentGap.REQUIREMENT_MISSING to R.string.water_gap_requirement,
        WaterAssessmentGap.UNPARSEABLE_REQUIREMENT to R.string.water_gap_parse,
        WaterAssessmentGap.INFORMATIONAL to R.string.water_gap_informational,
        WaterAssessmentGap.SOURCE_UNRESOLVED to R.string.water_gap_source,
        WaterAssessmentGap.CATALOG_UNAVAILABLE to R.string.water_gap_catalog,
        WaterAssessmentGap.CATALOG_ENTRY_MISSING to R.string.water_gap_identity,
        WaterAssessmentGap.CUSTOM_UNVERIFIED to R.string.water_gap_custom,
        WaterAssessmentGap.PARTIAL_PLANT to R.string.water_gap_plant,
        WaterAssessmentGap.UNKNOWN_TANK_TYPE to R.string.water_gap_tank,
        WaterAssessmentGap.UNKNOWN_REQUIREMENT_SEMANTIC to R.string.water_gap_semantic,
        WaterAssessmentGap.RULE_MISSING to R.string.water_gap_rule,
        WaterAssessmentGap.INCOMPATIBLE_HABITAT to R.string.water_gap_habitat,
        WaterAssessmentGap.UNKNOWN_HABITAT to R.string.water_gap_unknown_habitat
    )

    fun lines(context: Context, result: WaterQualityAssessment): List<String> = buildList {
        result.findings.filter { it.direction != null }.forEach { finding ->
            val status = when (finding.direction) {
                WaterAssessmentDirection.BELOW -> R.string.water_finding_below
                WaterAssessmentDirection.ABOVE -> R.string.water_finding_above
                else -> R.string.water_finding_within
            }
            add(context.getString(R.string.water_finding_line, label(context, finding.entity),
                context.getString(parameters.getValue(finding.parameter)), context.getString(status),
                range(context, requireNotNull(finding.expectedRange)), unit(finding.parameter)))
        }
        result.conflicts.forEach { conflict ->
            add(context.getString(R.string.water_finding_conflict, label(context, conflict.first.entity),
                label(context, conflict.second.entity), context.getString(parameters.getValue(conflict.parameter))))
        }
        result.habitatConflicts.forEach { conflict ->
            add(context.getString(R.string.water_finding_habitat, label(context, conflict.entity)))
        }
        result.findings.mapNotNull { it.gap }.groupingBy { it }.eachCount().toSortedMap().forEach { (gap, count) ->
            add(context.getString(R.string.water_gap_line, count, context.getString(gaps.getValue(gap))))
        }
        if (result.recommendations.any { it.code == "VERIFY_TEST_RESULT" }) {
            add(context.getString(R.string.water_recommendation_verify))
        }
        if (result.conflicts.isNotEmpty() || result.habitatConflicts.isNotEmpty()) {
            add(context.getString(R.string.water_recommendation_habitat))
        }
    }

    private fun label(context: Context, entity: WaterAssessmentEntity): String = context.getString(
        if (entity.kind == WaterAssessmentEntityKind.PLANT) R.string.water_entity_plant
        else R.string.water_entity_livestock,
        entity.displayName.ifBlank { entity.catalogId }, entity.localId
    )

    private fun range(context: Context, range: LivestockParameterRange): String = buildList {
        fun number(value: Double) =
            WaterAnalysisPresentation.formatMeasuredNumber(value, LocaleFormatter.appLocale(context))
        range.minimum?.let { add("${if (range.minimumInclusive) "≥" else ">"} ${number(it)}") }
        range.maximum?.let { add("${if (range.maximumInclusive) "≤" else "<"} ${number(it)}") }
    }.joinToString("; ")

    private fun unit(parameter: AquariumWaterParameter): String = when (parameter) {
        AquariumWaterParameter.TEMPERATURE_C -> "°C"
        AquariumWaterParameter.GH_DGH -> "dGH"
        AquariumWaterParameter.NITRATE_PPM -> "mg/L NO₃⁻"
        AquariumWaterParameter.PHOSPHATE_PPM -> "mg/L PO₄³⁻"
        else -> ""
    }
}

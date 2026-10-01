package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentDirection
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessment

internal object TankHealthWaterMetricAssessment {

    @StringRes
    fun statusRes(
        assessment: WaterQualityAssessment?,
        parameter: AquariumWaterParameter?
    ): Int {
        if (assessment == null) {
            return R.string.tank_health_metric_assessment_unavailable
        }
        if (parameter == null) {
            return R.string.tank_health_metric_assessment_not_comparable
        }

        val directions = assessment.findings
            .asSequence()
            .filter { finding -> finding.parameter == parameter }
            .mapNotNull { finding -> finding.direction }
            .toSet()

        return when (directions) {
            emptySet<WaterAssessmentDirection>() ->
                R.string.tank_health_metric_assessment_not_comparable
            setOf(WaterAssessmentDirection.WITHIN) ->
                R.string.tank_health_metric_assessment_within
            setOf(WaterAssessmentDirection.ABOVE) ->
                R.string.tank_health_metric_assessment_above
            setOf(WaterAssessmentDirection.BELOW) ->
                R.string.tank_health_metric_assessment_below
            else -> R.string.tank_health_metric_assessment_mixed
        }
    }

    fun assessmentParameter(parameter: WaterParameter): AquariumWaterParameter? =
        assessmentParameters[parameter]

    private val assessmentParameters = mapOf(
        WaterParameter.PH to AquariumWaterParameter.PH,
        WaterParameter.GH to AquariumWaterParameter.GH_DGH,
        WaterParameter.KH to AquariumWaterParameter.KH_DKH,
        WaterParameter.TDS to AquariumWaterParameter.TDS_PPM,
        WaterParameter.SPECIFIC_GRAVITY to AquariumWaterParameter.SPECIFIC_GRAVITY,
        WaterParameter.TOTAL_ALKALINITY to AquariumWaterParameter.ALKALINITY_DKH,
        WaterParameter.CALCIUM to AquariumWaterParameter.CALCIUM_PPM,
        WaterParameter.MAGNESIUM to AquariumWaterParameter.MAGNESIUM_PPM,
        WaterParameter.NITRATE to AquariumWaterParameter.NITRATE_PPM,
        WaterParameter.PHOSPHATE to AquariumWaterParameter.PHOSPHATE_PPM
    )
}

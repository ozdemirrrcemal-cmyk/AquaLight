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
        when (parameter) {
            WaterParameter.PH -> AquariumWaterParameter.PH
            WaterParameter.GH -> AquariumWaterParameter.GH_DGH
            WaterParameter.KH -> AquariumWaterParameter.KH_DKH
            WaterParameter.TDS -> AquariumWaterParameter.TDS_PPM
            WaterParameter.SPECIFIC_GRAVITY -> AquariumWaterParameter.SPECIFIC_GRAVITY
            WaterParameter.TOTAL_ALKALINITY -> AquariumWaterParameter.ALKALINITY_DKH
            WaterParameter.CALCIUM -> AquariumWaterParameter.CALCIUM_PPM
            WaterParameter.MAGNESIUM -> AquariumWaterParameter.MAGNESIUM_PPM
            WaterParameter.NITRATE -> AquariumWaterParameter.NITRATE_PPM
            WaterParameter.PHOSPHATE -> AquariumWaterParameter.PHOSPHATE_PPM
            WaterParameter.NITRITE,
            WaterParameter.AMMONIA_AMMONIUM,
            WaterParameter.TOTAL_AMMONIA_NITROGEN,
            WaterParameter.FREE_AMMONIA_NH3,
            WaterParameter.EC,
            WaterParameter.CO2,
            WaterParameter.IRON,
            WaterParameter.POTASSIUM,
            WaterParameter.SALINITY,
            WaterParameter.COPPER,
            WaterParameter.DISSOLVED_OXYGEN -> null
        }
}

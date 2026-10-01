package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentDirection
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessment
import com.aqua.aqualight.application.aquarium.health.water.WaterRuleFinding

internal enum class TankHealthWaterCompatibilityStatus {
    SUITABLE,
    PARTIAL,
    INCOMPATIBLE,
    UNEVALUATED
}

internal data class TankHealthWaterCompatibilitySummary(
    val status: TankHealthWaterCompatibilityStatus,
    val totalCount: Int,
    val comparedCount: Int,
    val withinCount: Int,
    val aboveCount: Int,
    val belowCount: Int,
    val unassessedCount: Int
)

internal object TankHealthWaterMetricAssessment {

    fun summarize(
        assessment: WaterQualityAssessment?,
        parameter: AquariumWaterParameter?
    ): TankHealthWaterCompatibilitySummary {
        if (assessment == null || parameter == null) {
            return emptySummary()
        }

        val findings = assessment.findings.filter { finding ->
            finding.parameter == parameter
        }
        if (findings.isEmpty()) {
            return emptySummary()
        }

        val within = findings.count { it.direction == WaterAssessmentDirection.WITHIN }
        val above = findings.count { it.direction == WaterAssessmentDirection.ABOVE }
        val below = findings.count { it.direction == WaterAssessmentDirection.BELOW }
        val compared = within + above + below
        val unassessed = findings.size - compared
        val outside = above + below
        val status = when {
            compared == 0 -> TankHealthWaterCompatibilityStatus.UNEVALUATED
            outside > 0 && within == 0 -> TankHealthWaterCompatibilityStatus.INCOMPATIBLE
            outside > 0 || unassessed > 0 -> TankHealthWaterCompatibilityStatus.PARTIAL
            else -> TankHealthWaterCompatibilityStatus.SUITABLE
        }

        return TankHealthWaterCompatibilitySummary(
            status = status,
            totalCount = findings.size,
            comparedCount = compared,
            withinCount = within,
            aboveCount = above,
            belowCount = below,
            unassessedCount = unassessed
        )
    }

    fun findings(
        assessment: WaterQualityAssessment?,
        parameter: AquariumWaterParameter?
    ): List<WaterRuleFinding> =
        if (assessment == null || parameter == null) {
            emptyList()
        } else {
            assessment.findings.filter { finding -> finding.parameter == parameter }
        }

    @StringRes
    fun statusRes(status: TankHealthWaterCompatibilityStatus): Int =
        when (status) {
            TankHealthWaterCompatibilityStatus.SUITABLE ->
                R.string.tank_health_metric_status_suitable
            TankHealthWaterCompatibilityStatus.PARTIAL ->
                R.string.tank_health_metric_status_partial
            TankHealthWaterCompatibilityStatus.INCOMPATIBLE ->
                R.string.tank_health_metric_status_incompatible
            TankHealthWaterCompatibilityStatus.UNEVALUATED ->
                R.string.tank_health_metric_status_unevaluated
        }

    @ColorRes
    fun statusColorRes(status: TankHealthWaterCompatibilityStatus): Int =
        when (status) {
            TankHealthWaterCompatibilityStatus.SUITABLE -> R.color.snackbar_success
            TankHealthWaterCompatibilityStatus.PARTIAL -> R.color.dialog_icon_warning
            TankHealthWaterCompatibilityStatus.INCOMPATIBLE -> R.color.snackbar_error
            TankHealthWaterCompatibilityStatus.UNEVALUATED -> R.color.aqua_content_muted
        }

    fun assessmentParameter(parameter: WaterParameter): AquariumWaterParameter? =
        assessmentParameters[parameter]

    private fun emptySummary() = TankHealthWaterCompatibilitySummary(
        status = TankHealthWaterCompatibilityStatus.UNEVALUATED,
        totalCount = 0,
        comparedCount = 0,
        withinCount = 0,
        aboveCount = 0,
        belowCount = 0,
        unassessedCount = 0
    )

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

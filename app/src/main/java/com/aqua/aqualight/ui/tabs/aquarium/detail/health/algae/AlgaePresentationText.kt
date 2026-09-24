package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeActionId
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeAnalysisPriority
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeDensity
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeFactorId
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeFactorStrength
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeMissingData
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeObservationLocation
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTrend

object AlgaePresentationText {

    @StringRes
    fun density(value: AlgaeDensity): Int = when (value) {
        AlgaeDensity.LOW -> R.string.algae_density_low
        AlgaeDensity.MEDIUM -> R.string.algae_density_medium
        AlgaeDensity.HIGH -> R.string.algae_density_high
    }

    @StringRes
    fun trend(value: AlgaeTrend): Int = when (value) {
        AlgaeTrend.INCREASING -> R.string.algae_trend_increasing
        AlgaeTrend.STABLE -> R.string.algae_trend_stable
        AlgaeTrend.DECREASING -> R.string.algae_trend_decreasing
    }

    @StringRes
    fun location(value: AlgaeObservationLocation): Int = when (value) {
        AlgaeObservationLocation.FRONT_GLASS -> R.string.algae_location_front_glass
        AlgaeObservationLocation.BACK_GLASS -> R.string.algae_location_back_glass
        AlgaeObservationLocation.SIDE_GLASS -> R.string.algae_location_side_glass
        AlgaeObservationLocation.PLANTS -> R.string.algae_location_plants
        AlgaeObservationLocation.ROOT_WOOD -> R.string.algae_location_root_wood
        AlgaeObservationLocation.ROCKS -> R.string.algae_location_rocks
        AlgaeObservationLocation.SUBSTRATE -> R.string.algae_location_substrate
        AlgaeObservationLocation.EQUIPMENT -> R.string.algae_location_equipment
        AlgaeObservationLocation.WATER_COLUMN -> R.string.algae_location_water_column
        AlgaeObservationLocation.OTHER -> R.string.algae_location_other
    }

    @StringRes
    fun factor(value: AlgaeFactorId): Int = when (value) {
        AlgaeFactorId.LIGHT_DURATION -> R.string.algae_factor_light_duration
        AlgaeFactorId.LIGHT_INTENSITY -> R.string.algae_factor_light_intensity
        AlgaeFactorId.CO2_STABILITY -> R.string.algae_factor_co2_stability
        AlgaeFactorId.ORGANIC_LOAD -> R.string.algae_factor_organic_load
        AlgaeFactorId.FILTER_MAINTENANCE -> R.string.algae_factor_filter_maintenance
        AlgaeFactorId.WATER_CHANGE_INTERVAL -> R.string.algae_factor_water_change_interval
        AlgaeFactorId.NITROGEN_WASTE -> R.string.algae_factor_nitrogen_waste
        AlgaeFactorId.NUTRIENT_IMBALANCE -> R.string.algae_factor_nutrient_imbalance
        AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT ->
            R.string.algae_factor_phosphate_imbalance
        AlgaeFactorId.LOW_NITRATE_CONTEXT -> R.string.algae_factor_low_nitrate
        AlgaeFactorId.IMMATURE_TANK -> R.string.algae_factor_immature_tank
        AlgaeFactorId.PLANT_STRESS -> R.string.algae_factor_plant_stress
        AlgaeFactorId.FLOW_OR_OXYGENATION -> R.string.algae_factor_flow_oxygenation
        AlgaeFactorId.WARM_WATER -> R.string.algae_factor_warm_water
    }

    @StringRes
    fun factorStrength(value: AlgaeFactorStrength): Int = when (value) {
        AlgaeFactorStrength.HIGH -> R.string.algae_analysis_strength_high
        AlgaeFactorStrength.MEDIUM -> R.string.algae_analysis_strength_medium
        AlgaeFactorStrength.LOW -> R.string.algae_analysis_strength_low
    }

    @StringRes
    fun action(value: AlgaeActionId): Int = when (value) {
        AlgaeActionId.MANUAL_REMOVAL -> R.string.algae_action_manual_removal
        AlgaeActionId.TRIM_AFFECTED_LEAVES -> R.string.algae_action_trim_leaves
        AlgaeActionId.CLEAN_HARDSCAPE -> R.string.algae_action_clean_hardscape
        AlgaeActionId.SIPHON_SUBSTRATE -> R.string.algae_action_siphon_substrate
        AlgaeActionId.REVIEW_LIGHT_DURATION -> R.string.algae_action_review_light_duration
        AlgaeActionId.REVIEW_LIGHT_INTENSITY -> R.string.algae_action_review_light_intensity
        AlgaeActionId.VERIFY_CO2_STABILITY -> R.string.algae_action_verify_co2
        AlgaeActionId.ADD_CO2_SCHEDULE_DATA -> R.string.algae_action_add_co2_schedule
        AlgaeActionId.SERVICE_FILTER -> R.string.algae_action_service_filter
        AlgaeActionId.PERFORM_WATER_CHANGE -> R.string.algae_action_water_change
        AlgaeActionId.IMPROVE_FLOW_OR_OXYGENATION -> R.string.algae_action_improve_flow
        AlgaeActionId.REVIEW_FERTILIZER_PLAN -> R.string.algae_action_review_fertilizer
        AlgaeActionId.REVIEW_NO3_PO4_BALANCE -> R.string.algae_action_review_no3_po4
        AlgaeActionId.ALLOW_TANK_TO_MATURE -> R.string.algae_action_mature_tank
        AlgaeActionId.UV_FOR_GREEN_WATER -> R.string.algae_action_uv_green_water
        AlgaeActionId.TEMPORARY_BLACKOUT -> R.string.algae_action_blackout
        AlgaeActionId.RECHECK_IN_FEW_DAYS -> R.string.algae_action_recheck
    }

    @StringRes
    fun missing(value: AlgaeMissingData): Int = when (value) {
        AlgaeMissingData.LIGHT_SCHEDULE -> R.string.algae_missing_light
        AlgaeMissingData.CO2_SCHEDULE -> R.string.algae_missing_co2
        AlgaeMissingData.WATER_ANALYSIS -> R.string.algae_missing_water
        AlgaeMissingData.MAINTENANCE_HISTORY -> R.string.algae_missing_maintenance
    }

    @StringRes
    fun priorityTitle(value: AlgaeAnalysisPriority): Int = when (value) {
        AlgaeAnalysisPriority.ACTION_RECOMMENDED -> R.string.algae_analysis_priority_action
        AlgaeAnalysisPriority.REVIEW -> R.string.algae_analysis_priority_review
        AlgaeAnalysisPriority.MONITOR -> R.string.algae_analysis_priority_monitor
    }

    @StringRes
    fun priorityBody(value: AlgaeAnalysisPriority): Int = when (value) {
        AlgaeAnalysisPriority.ACTION_RECOMMENDED -> R.string.algae_analysis_priority_action_body
        AlgaeAnalysisPriority.REVIEW -> R.string.algae_analysis_priority_review_body
        AlgaeAnalysisPriority.MONITOR -> R.string.algae_analysis_priority_monitor_body
    }
}

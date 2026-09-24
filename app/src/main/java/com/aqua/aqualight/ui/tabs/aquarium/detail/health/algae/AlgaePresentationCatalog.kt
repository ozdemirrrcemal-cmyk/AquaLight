package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeActionId
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeFactorId
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeMissingData

object AlgaePresentationCatalog {

    @StringRes
    fun factorTitle(factor: AlgaeFactorId): Int = when (factor) {
        AlgaeFactorId.LIGHT_DURATION -> R.string.algae_factor_light_duration
        AlgaeFactorId.LIGHT_INTENSITY -> R.string.algae_factor_light_intensity
        AlgaeFactorId.CO2_STABILITY -> R.string.algae_factor_co2_stability
        AlgaeFactorId.ORGANIC_LOAD -> R.string.algae_factor_organic_load
        AlgaeFactorId.FILTER_MAINTENANCE -> R.string.algae_factor_filter_maintenance
        AlgaeFactorId.WATER_CHANGE_INTERVAL -> R.string.algae_factor_water_change_interval
        AlgaeFactorId.NITROGEN_WASTE -> R.string.algae_factor_nitrogen_waste
        AlgaeFactorId.NUTRIENT_IMBALANCE -> R.string.algae_factor_nutrient_imbalance
        AlgaeFactorId.LOW_PHOSPHATE_CONTEXT -> R.string.algae_factor_low_phosphate
        AlgaeFactorId.LOW_NITRATE_CONTEXT -> R.string.algae_factor_low_nitrate
        AlgaeFactorId.IMMATURE_TANK -> R.string.algae_factor_immature_tank
        AlgaeFactorId.PLANT_STRESS -> R.string.algae_factor_plant_stress
        AlgaeFactorId.LOW_PLANT_MASS -> R.string.algae_factor_low_plant_mass
        AlgaeFactorId.FLOW_OR_OXYGENATION -> R.string.algae_factor_flow_oxygenation
        AlgaeFactorId.WARM_WATER -> R.string.algae_factor_warm_water
    }

    @StringRes
    fun actionTitle(action: AlgaeActionId): Int = when (action) {
        AlgaeActionId.MANUAL_REMOVAL -> R.string.algae_action_manual_removal
        AlgaeActionId.TRIM_AFFECTED_LEAVES -> R.string.algae_action_trim_affected_leaves
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
        AlgaeActionId.ALLOW_TANK_TO_MATURE -> R.string.algae_action_allow_maturation
        AlgaeActionId.UV_FOR_GREEN_WATER -> R.string.algae_action_uv_green_water
        AlgaeActionId.TEMPORARY_BLACKOUT -> R.string.algae_action_temporary_blackout
        AlgaeActionId.RECHECK_IN_FEW_DAYS -> R.string.algae_action_recheck
    }

    @StringRes
    fun missingDataTitle(missingData: AlgaeMissingData): Int = when (missingData) {
        AlgaeMissingData.LIGHT_PROFILE -> R.string.algae_missing_light_profile
        AlgaeMissingData.CO2_SCHEDULE -> R.string.algae_missing_co2_schedule
        AlgaeMissingData.WATER_ANALYSIS -> R.string.algae_missing_water_analysis
        AlgaeMissingData.MAINTENANCE_HISTORY -> R.string.algae_missing_maintenance_history
        AlgaeMissingData.PLANT_MASS -> R.string.algae_missing_plant_mass
        AlgaeMissingData.TEMPERATURE_CONTEXT -> R.string.algae_missing_temperature_context
    }
}

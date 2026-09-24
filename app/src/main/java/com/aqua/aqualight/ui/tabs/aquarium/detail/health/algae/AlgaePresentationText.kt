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
    fun density(value: AlgaeDensity): Int = DENSITY_TEXT.getValue(value)

    @StringRes
    fun trend(value: AlgaeTrend): Int = TREND_TEXT.getValue(value)

    @StringRes
    fun location(value: AlgaeObservationLocation): Int = LOCATION_TEXT.getValue(value)

    @StringRes
    fun factor(value: AlgaeFactorId): Int = FACTOR_TEXT.getValue(value)

    @StringRes
    fun factorStrength(value: AlgaeFactorStrength): Int =
        FACTOR_STRENGTH_TEXT.getValue(value)

    @StringRes
    fun action(value: AlgaeActionId): Int = ACTION_TEXT.getValue(value)

    @StringRes
    fun missing(value: AlgaeMissingData): Int = MISSING_DATA_TEXT.getValue(value)

    @StringRes
    fun priorityTitle(value: AlgaeAnalysisPriority): Int =
        PRIORITY_TITLE_TEXT.getValue(value)

    @StringRes
    fun priorityBody(value: AlgaeAnalysisPriority): Int =
        PRIORITY_BODY_TEXT.getValue(value)

    private val DENSITY_TEXT = mapOf(
        AlgaeDensity.LOW to R.string.algae_density_low,
        AlgaeDensity.MEDIUM to R.string.algae_density_medium,
        AlgaeDensity.HIGH to R.string.algae_density_high
    )

    private val TREND_TEXT = mapOf(
        AlgaeTrend.INCREASING to R.string.algae_trend_increasing,
        AlgaeTrend.STABLE to R.string.algae_trend_stable,
        AlgaeTrend.DECREASING to R.string.algae_trend_decreasing
    )

    private val LOCATION_TEXT = mapOf(
        AlgaeObservationLocation.FRONT_GLASS to R.string.algae_location_front_glass,
        AlgaeObservationLocation.BACK_GLASS to R.string.algae_location_back_glass,
        AlgaeObservationLocation.SIDE_GLASS to R.string.algae_location_side_glass,
        AlgaeObservationLocation.PLANTS to R.string.algae_location_plants,
        AlgaeObservationLocation.ROOT_WOOD to R.string.algae_location_root_wood,
        AlgaeObservationLocation.ROCKS to R.string.algae_location_rocks,
        AlgaeObservationLocation.SUBSTRATE to R.string.algae_location_substrate,
        AlgaeObservationLocation.EQUIPMENT to R.string.algae_location_equipment,
        AlgaeObservationLocation.WATER_COLUMN to R.string.algae_location_water_column,
        AlgaeObservationLocation.OTHER to R.string.algae_location_other
    )

    private val FACTOR_TEXT = mapOf(
        AlgaeFactorId.LIGHT_DURATION to R.string.algae_factor_light_duration,
        AlgaeFactorId.LIGHT_INTENSITY to R.string.algae_factor_light_intensity,
        AlgaeFactorId.CO2_STABILITY to R.string.algae_factor_co2_stability,
        AlgaeFactorId.ORGANIC_LOAD to R.string.algae_factor_organic_load,
        AlgaeFactorId.FILTER_MAINTENANCE to R.string.algae_factor_filter_maintenance,
        AlgaeFactorId.WATER_CHANGE_INTERVAL to R.string.algae_factor_water_change_interval,
        AlgaeFactorId.NITROGEN_WASTE to R.string.algae_factor_nitrogen_waste,
        AlgaeFactorId.NUTRIENT_IMBALANCE to R.string.algae_factor_nutrient_imbalance,
        AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT to
            R.string.algae_factor_phosphate_imbalance,
        AlgaeFactorId.LOW_NITRATE_CONTEXT to R.string.algae_factor_low_nitrate,
        AlgaeFactorId.IMMATURE_TANK to R.string.algae_factor_immature_tank,
        AlgaeFactorId.PLANT_STRESS to R.string.algae_factor_plant_stress,
        AlgaeFactorId.FLOW_OR_OXYGENATION to R.string.algae_factor_flow_oxygenation,
        AlgaeFactorId.WARM_WATER to R.string.algae_factor_warm_water
    )

    private val FACTOR_STRENGTH_TEXT = mapOf(
        AlgaeFactorStrength.HIGH to R.string.algae_analysis_strength_high,
        AlgaeFactorStrength.MEDIUM to R.string.algae_analysis_strength_medium,
        AlgaeFactorStrength.LOW to R.string.algae_analysis_strength_low
    )

    private val ACTION_TEXT = mapOf(
        AlgaeActionId.MANUAL_REMOVAL to R.string.algae_action_manual_removal,
        AlgaeActionId.TRIM_AFFECTED_LEAVES to R.string.algae_action_trim_leaves,
        AlgaeActionId.CLEAN_HARDSCAPE to R.string.algae_action_clean_hardscape,
        AlgaeActionId.SIPHON_SUBSTRATE to R.string.algae_action_siphon_substrate,
        AlgaeActionId.REVIEW_LIGHT_DURATION to R.string.algae_action_review_light_duration,
        AlgaeActionId.REVIEW_LIGHT_INTENSITY to R.string.algae_action_review_light_intensity,
        AlgaeActionId.VERIFY_CO2_STABILITY to R.string.algae_action_verify_co2,
        AlgaeActionId.ADD_CO2_SCHEDULE_DATA to R.string.algae_action_add_co2_schedule,
        AlgaeActionId.SERVICE_FILTER to R.string.algae_action_service_filter,
        AlgaeActionId.PERFORM_WATER_CHANGE to R.string.algae_action_water_change,
        AlgaeActionId.IMPROVE_FLOW_OR_OXYGENATION to R.string.algae_action_improve_flow,
        AlgaeActionId.REVIEW_FERTILIZER_PLAN to R.string.algae_action_review_fertilizer,
        AlgaeActionId.REVIEW_NO3_PO4_BALANCE to R.string.algae_action_review_no3_po4,
        AlgaeActionId.ALLOW_TANK_TO_MATURE to R.string.algae_action_mature_tank,
        AlgaeActionId.UV_FOR_GREEN_WATER to R.string.algae_action_uv_green_water,
        AlgaeActionId.TEMPORARY_BLACKOUT to R.string.algae_action_blackout,
        AlgaeActionId.RECHECK_IN_FEW_DAYS to R.string.algae_action_recheck
    )

    private val MISSING_DATA_TEXT = mapOf(
        AlgaeMissingData.LIGHT_PROFILE to R.string.algae_missing_light,
        AlgaeMissingData.CO2_SCHEDULE to R.string.algae_missing_co2,
        AlgaeMissingData.WATER_ANALYSIS to R.string.algae_missing_water,
        AlgaeMissingData.MAINTENANCE_HISTORY to R.string.algae_missing_maintenance,
        AlgaeMissingData.TEMPERATURE_CONTEXT to R.string.algae_missing_temperature
    )

    private val PRIORITY_TITLE_TEXT = mapOf(
        AlgaeAnalysisPriority.ACTION_RECOMMENDED to R.string.algae_analysis_priority_action,
        AlgaeAnalysisPriority.REVIEW to R.string.algae_analysis_priority_review,
        AlgaeAnalysisPriority.MONITOR to R.string.algae_analysis_priority_monitor
    )

    private val PRIORITY_BODY_TEXT = mapOf(
        AlgaeAnalysisPriority.ACTION_RECOMMENDED to
            R.string.algae_analysis_priority_action_body,
        AlgaeAnalysisPriority.REVIEW to R.string.algae_analysis_priority_review_body,
        AlgaeAnalysisPriority.MONITOR to R.string.algae_analysis_priority_monitor_body
    )
}

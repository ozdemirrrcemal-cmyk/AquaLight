package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeLocation
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeAppearance
import com.aqua.aqualight.application.aquarium.health.observation.AlgaeExtent
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import com.aqua.aqualight.application.aquarium.health.observation.LivestockFinding
import com.aqua.aqualight.application.aquarium.health.observation.ReportedCo2Pattern
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.application.aquarium.health.observation.ObservationGap
import com.aqua.aqualight.application.aquarium.health.observation.ObservationAction

internal object HealthObservationLabels {
    fun label(value: Enum<*>): Int = requireNotNull(labels[value])

    private val labels = mapOf<Enum<*>, Int>(
        AlgaeLocation.GLASS to R.string.health_algaelocation_glass,
        AlgaeLocation.SUBSTRATE to R.string.health_algaelocation_substrate,
        AlgaeLocation.PLANT to R.string.health_algaelocation_plant,
        AlgaeLocation.DECORATION to R.string.health_algaelocation_decoration,
        AlgaeLocation.EQUIPMENT to R.string.health_algaelocation_equipment,
        AlgaeLocation.WATER to R.string.health_algaelocation_water,
        AlgaeLocation.UNKNOWN to R.string.health_algaelocation_unknown,
        AlgaeAppearance.FILM to R.string.health_algaeappearance_film,
        AlgaeAppearance.THREADS to R.string.health_algaeappearance_threads,
        AlgaeAppearance.SPOTS to R.string.health_algaeappearance_spots,
        AlgaeAppearance.CLOUDINESS to R.string.health_algaeappearance_cloudiness,
        AlgaeAppearance.UNKNOWN to R.string.health_algaeappearance_unknown,
        AlgaeExtent.LOCAL to R.string.health_algaeextent_local,
        AlgaeExtent.MULTIPLE_AREAS to R.string.health_algaeextent_multiple_areas,
        AlgaeExtent.WIDESPREAD to R.string.health_algaeextent_widespread,
        AlgaeExtent.UNKNOWN to R.string.health_algaeextent_unknown,
        PlantFinding.LEAF_DISCOLORATION to R.string.health_plantfinding_leaf_discoloration,
        PlantFinding.LEAF_DAMAGE to R.string.health_plantfinding_leaf_damage,
        PlantFinding.GROWTH_CHANGE to R.string.health_plantfinding_growth_change,
        PlantFinding.ROOT_CHANGE to R.string.health_plantfinding_root_change,
        PlantFinding.ALGAE_PRESENT to R.string.health_plantfinding_algae_present,
        PlantFinding.UNKNOWN to R.string.health_plantfinding_unknown,
        LivestockFinding.BEHAVIOR_CHANGE to R.string.health_livestockfinding_behavior_change,
        LivestockFinding.APPETITE_CHANGE to R.string.health_livestockfinding_appetite_change,
        LivestockFinding.BREATHING_CHANGE to R.string.health_livestockfinding_breathing_change,
        LivestockFinding.VISIBLE_MARKS to R.string.health_livestockfinding_visible_marks,
        LivestockFinding.INJURY to R.string.health_livestockfinding_injury,
        LivestockFinding.UNKNOWN to R.string.health_livestockfinding_unknown,
        ReportedCo2Pattern.STEADY to R.string.health_reportedco2pattern_steady,
        ReportedCo2Pattern.VARIABLE to R.string.health_reportedco2pattern_variable,
        ReportedCo2Pattern.NOT_USED to R.string.health_reportedco2pattern_not_used,
        ReportedCo2Pattern.UNKNOWN to R.string.health_reportedco2pattern_unknown,
        ObservationPhase.OBSERVATION to R.string.health_observationphase_observation,
        ObservationPhase.INTERVENTION to R.string.health_observationphase_intervention,
        ObservationPhase.FOLLOW_UP to R.string.health_observationphase_follow_up,
        ObservationGap.UNCERTAIN_OBSERVATION to R.string.health_observationgap_uncertain_observation,
        ObservationGap.WATER_MISSING to R.string.health_observationgap_water_missing,
        ObservationGap.WATER_OUTSIDE_WINDOW to R.string.health_observationgap_water_outside_window,
        ObservationGap.WATER_CONTEXT_DIFFERS to R.string.health_observationgap_water_context_differs,
        ObservationGap.SUBJECT_REMOVED to R.string.health_observationgap_subject_removed,
        ObservationGap.SUBJECT_UNVERIFIED to R.string.health_observationgap_subject_unverified,
        ObservationGap.PARTIAL_CARE_PROFILE to R.string.health_observationgap_partial_care_profile,
        ObservationGap.AFFECTED_QUANTITY_UNKNOWN to R.string.health_observationgap_affected_quantity_unknown,
        ObservationGap.LIGHT_SCHEDULE_MISSING to R.string.health_observationgap_light_schedule_missing,
        ObservationGap.LIGHT_MEASUREMENT_MISSING to R.string.health_observationgap_light_measurement_missing,
        ObservationGap.CO2_PATTERN_UNKNOWN to R.string.health_observationgap_co2_pattern_unknown,
        ObservationGap.DOSING_HISTORY_MISSING to R.string.health_observationgap_dosing_history_missing,
        ObservationAction.RECORD_FOLLOW_UP to R.string.health_observationaction_record_follow_up,
        ObservationAction.REVIEW_WATER_ANALYSIS to R.string.health_observationaction_review_water_analysis,
        ObservationAction.VERIFY_SUBJECT_PROFILE to R.string.health_observationaction_verify_subject_profile,
        ObservationAction.OPEN_ALGAE_CONTROL to R.string.health_observationaction_open_algae_control,
        ObservationAction.RECORD_OPERATING_OBSERVATIONS to
            R.string.health_observationaction_record_operating_observations,
        ObservationAction.REVIEW_LIVESTOCK_CONDITION to R.string.health_observationaction_review_livestock_condition
    )
}

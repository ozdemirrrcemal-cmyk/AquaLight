package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.R

object SmartCareRuleCatalog {

  val startupRules: List<SmartCareRule> = listOf(

    SmartCareRule(
      id = "startup_day_1_general_check",
      dayStart = 1,
      dayEnd = 1,
      conditions = emptyList(),
      taskType = SmartCareTaskType.GENERAL_CHECK,
      titleRes = R.string.maintenance_smart_rule_initial_setup_check_title,
      messageRes = R.string.maintenance_smart_rule_initial_setup_check_message,
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_light_6_hours_planted",
      dayStart = 1,
      dayEnd = 21,
      conditions = listOf(
        SmartCareCondition.PLANTED,
        SmartCareCondition.HAS_LIGHT
      ),
      taskType = SmartCareTaskType.LIGHTING,
      titleRes = R.string.maintenance_smart_rule_check_light_duration_title,
      messageRes = R.string.maintenance_smart_rule_check_light_duration_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_co2_daily_check",
      dayStart = 1,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.HAS_CO2
      ),
      taskType = SmartCareTaskType.CO2_CHECK,
      titleRes = R.string.maintenance_smart_rule_co2_check_title,
      messageRes = R.string.maintenance_smart_rule_co2_check_message,
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.EVERY_2_DAYS,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_active_soil_water_change_first_week",
      dayStart = 2,
      dayEnd = 7,
      conditions = listOf(
        SmartCareCondition.HAS_ACTIVE_SOIL
      ),
      taskType = SmartCareTaskType.WATER_CHANGE,
      titleRes = R.string.maintenance_smart_rule_early_water_change_title,
      messageRes = R.string.maintenance_smart_rule_early_water_change_message,
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.EVERY_2_DAYS,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_planted_water_change_week_1_4",
      dayStart = 3,
      dayEnd = 28,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.WATER_CHANGE,
      titleRes = R.string.maintenance_smart_rule_startup_water_change_title,
      messageRes = R.string.maintenance_smart_rule_startup_water_change_message,
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_plant_melt_check",
      dayStart = 3,
      dayEnd = 21,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.PLANT_CHECK,
      titleRes = R.string.maintenance_smart_rule_plant_adaptation_check_title,
      messageRes = R.string.maintenance_smart_rule_plant_adaptation_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.EVERY_3_DAYS,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_algae_check",
      dayStart = 5,
      dayEnd = 45,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.GLASS_CLEANING,
      titleRes = R.string.maintenance_smart_rule_algae_check_title,
      messageRes = R.string.maintenance_smart_rule_algae_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_fertilizer_unknown_week_2",
      dayStart = 8,
      dayEnd = 14,
      conditions = listOf(
        SmartCareCondition.PLANTED,
        SmartCareCondition.FERTILIZER_UNKNOWN
      ),
      taskType = SmartCareTaskType.FERTILIZER,
      titleRes = R.string.maintenance_smart_rule_review_fertilizer_plan_title,
      messageRes = R.string.maintenance_smart_rule_review_fertilizer_plan_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_fertilizer_selected_week_2",
      dayStart = 8,
      dayEnd = 21,
      conditions = listOf(
        SmartCareCondition.PLANTED,
        SmartCareCondition.HAS_FERTILIZER
      ),
      taskType = SmartCareTaskType.FERTILIZER,
      titleRes = R.string.maintenance_smart_rule_fertilizer_check_title,
      messageRes = R.string.maintenance_smart_rule_fertilizer_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_water_test_before_livestock",
      dayStart = 14,
      dayEnd = 30,
      conditions = listOf(
        SmartCareCondition.NO_LIVESTOCK
      ),
      taskType = SmartCareTaskType.WATER_TEST,
      titleRes = R.string.maintenance_smart_rule_water_test_before_livestock_title,
      messageRes = R.string.maintenance_smart_rule_water_test_before_livestock_message,
      priority = SmartCarePriority.CRITICAL,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      requiresWaterTest = true,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_cleanup_crew_suggestion",
      dayStart = 14,
      dayEnd = 30,
      conditions = listOf(
        SmartCareCondition.NO_LIVESTOCK
      ),
      taskType = SmartCareTaskType.LIVESTOCK_CHECK,
      titleRes = R.string.maintenance_smart_rule_livestock_readiness_check_title,
      messageRes = R.string.maintenance_smart_rule_livestock_readiness_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      requiresWaterTest = true,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_fish_addition_check",
      dayStart = 21,
      dayEnd = 45,
      conditions = listOf(
        SmartCareCondition.NO_LIVESTOCK
      ),
      taskType = SmartCareTaskType.LIVESTOCK_CHECK,
      titleRes = R.string.maintenance_smart_rule_fish_addition_check_title,
      messageRes = R.string.maintenance_smart_rule_fish_addition_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      requiresWaterTest = true,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_light_increase_after_week_3",
      dayStart = 22,
      dayEnd = 45,
      conditions = listOf(
        SmartCareCondition.PLANTED,
        SmartCareCondition.HAS_LIGHT
      ),
      taskType = SmartCareTaskType.LIGHTING,
      titleRes = R.string.maintenance_smart_rule_adjust_light_period_title,
      messageRes = R.string.maintenance_smart_rule_adjust_light_period_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_first_trim_check",
      dayStart = 21,
      dayEnd = 45,
      conditions = listOf(
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.PLANT_TRIM,
      titleRes = R.string.maintenance_smart_rule_first_trimming_check_title,
      messageRes = R.string.maintenance_smart_rule_first_trimming_check_message,
      priority = SmartCarePriority.LOW,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_weekly_water_change_after_day_30",
      dayStart = 31,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.STARTUP_PERIOD
      ),
      taskType = SmartCareTaskType.WATER_CHANGE,
      titleRes = R.string.maintenance_smart_rule_weekly_water_change_title,
      messageRes = R.string.maintenance_smart_rule_weekly_water_change_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_filter_flow_check",
      dayStart = 30,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.HAS_FILTER
      ),
      taskType = SmartCareTaskType.FILTER_CHECK,
      titleRes = R.string.maintenance_smart_rule_filter_flow_check_title,
      messageRes = R.string.maintenance_smart_rule_filter_flow_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.EVERY_2_WEEKS,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_shrimp_stability_warning",
      dayStart = 14,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.HAS_SHRIMP
      ),
      taskType = SmartCareTaskType.LIVESTOCK_CHECK,
      titleRes = R.string.maintenance_smart_rule_shrimp_stability_check_title,
      messageRes = R.string.maintenance_smart_rule_shrimp_stability_check_message,
      priority = SmartCarePriority.HIGH,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_fish_feeding_warning",
      dayStart = 1,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.HAS_FISH
      ),
      taskType = SmartCareTaskType.FEEDING,
      titleRes = R.string.maintenance_smart_rule_feeding_review_title,
      messageRes = R.string.maintenance_smart_rule_feeding_review_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.EVERY_3_DAYS,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "startup_day_90_complete",
      dayStart = 90,
      dayEnd = 90,
      conditions = listOf(
        SmartCareCondition.STARTUP_PERIOD
      ),
      taskType = SmartCareTaskType.GENERAL_CHECK,
      titleRes = R.string.maintenance_smart_rule_startup_phase_complete_title,
      messageRes = R.string.maintenance_smart_rule_startup_phase_complete_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.ONCE,
      sourceTags = listOf("SmartCare")
    )
  )

  val matureTankRules: List<SmartCareRule> = listOf(

    SmartCareRule(
      id = "mature_weekly_water_change",
      dayStart = 91,
      dayEnd = Int.MAX_VALUE,
      conditions = listOf(
        SmartCareCondition.MATURE_TANK
      ),
      taskType = SmartCareTaskType.WATER_CHANGE,
      titleRes = R.string.maintenance_smart_rule_weekly_water_change_title,
      messageRes = R.string.maintenance_smart_rule_weekly_water_change_mature_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "mature_weekly_plant_trim",
      dayStart = 91,
      dayEnd = Int.MAX_VALUE,
      conditions = listOf(
        SmartCareCondition.MATURE_TANK,
        SmartCareCondition.PLANTED
      ),
      taskType = SmartCareTaskType.PLANT_TRIM,
      titleRes = R.string.maintenance_smart_rule_plant_trimming_check_title,
      messageRes = R.string.maintenance_smart_rule_plant_trimming_check_message,
      priority = SmartCarePriority.LOW,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "mature_monthly_filter_check",
      dayStart = 91,
      dayEnd = Int.MAX_VALUE,
      conditions = listOf(
        SmartCareCondition.MATURE_TANK,
        SmartCareCondition.HAS_FILTER
      ),
      taskType = SmartCareTaskType.FILTER_CHECK,
      titleRes = R.string.maintenance_smart_rule_filter_maintenance_check_title,
      messageRes = R.string.maintenance_smart_rule_filter_maintenance_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.MONTHLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "mature_weekly_co2_check",
      dayStart = 91,
      dayEnd = Int.MAX_VALUE,
      conditions = listOf(
        SmartCareCondition.MATURE_TANK,
        SmartCareCondition.HAS_CO2
      ),
      taskType = SmartCareTaskType.CO2_CHECK,
      titleRes = R.string.maintenance_smart_rule_co2_system_check_title,
      messageRes = R.string.maintenance_smart_rule_co2_system_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    ),

    SmartCareRule(
      id = "mature_weekly_livestock_check",
      dayStart = 91,
      dayEnd = Int.MAX_VALUE,
      conditions = listOf(
        SmartCareCondition.MATURE_TANK,
        SmartCareCondition.HAS_LIVESTOCK
      ),
      taskType = SmartCareTaskType.LIVESTOCK_CHECK,
      titleRes = R.string.maintenance_smart_rule_livestock_health_check_title,
      messageRes = R.string.maintenance_smart_rule_livestock_health_check_message,
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    )
  )

  val allRules: List<SmartCareRule>
    get() = startupRules + matureTankRules
}

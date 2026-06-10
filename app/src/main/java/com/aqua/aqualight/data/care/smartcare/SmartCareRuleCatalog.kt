package com.aqua.aqualight.data.care.smartcare

object SmartCareRuleCatalog {

  val startupRules: List<SmartCareRule> = listOf(

    SmartCareRule(
      id = "startup_day_1_general_check",
      dayStart = 1,
      dayEnd = 1,
      conditions = emptyList(),
      taskType = SmartCareTaskType.GENERAL_CHECK,
      titleTr = "Initial setup check",
      messageTr = "Check that the filter is running, equipment is installed correctly, and water circulation is stable.",
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
      titleTr = "Check light duration",
      messageTr = "During the early setup phase, keeping the light period around 6 hours can help reduce algae risk.",
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
      titleTr = "CO₂ check",
      messageTr = "Check CO₂ timing, drop checker color, and livestock behavior.",
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
      titleTr = "Early water change",
      messageTr = "Active soil can release excess nutrients during the first days. A water change helps reduce algae risk.",
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
      titleTr = "Startup water change",
      messageTr = "Regular water changes during the first weeks help stabilize the aquarium and reduce excess nutrients.",
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
      titleTr = "Plant adaptation check",
      messageTr = "Check for melting leaves, weak stems, or newly planted sections that may need adjustment.",
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
      titleTr = "Algae check",
      messageTr = "Check glass, hardscape, and plant leaves for early algae signs.",
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
      titleTr = "Review fertilizer plan",
      messageTr = "Add your fertilizer product to improve automatic dosing recommendations.",
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
      titleTr = "Fertilizer check",
      messageTr = "Start carefully with reduced dosing and adjust based on plant response and algae signs.",
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
      titleTr = "Water test before livestock",
      messageTr = "Before adding livestock, check that the aquarium is stable and no ammonia or nitrite is detected.",
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
      titleTr = "Livestock readiness check",
      messageTr = "If water parameters are stable, you can start planning suitable cleanup crew or livestock gradually.",
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
      titleTr = "Fish addition check",
      messageTr = "Add fish only if the tank is stable. Start with a small number and avoid overstocking.",
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
      titleTr = "Adjust light period",
      messageTr = "If plant growth is stable and algae is under control, you can gradually increase the light period.",
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
      titleTr = "First trimming check",
      messageTr = "Check fast-growing plants and trim unhealthy or overgrown sections if needed.",
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
      titleTr = "Weekly water change",
      messageTr = "Continue weekly water changes while the aquarium matures.",
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
      titleTr = "Filter flow check",
      messageTr = "Check filter flow and make sure circulation is still strong and stable.",
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
      titleTr = "Shrimp stability check",
      messageTr = "Shrimp are sensitive to unstable water parameters. Check behavior and avoid sudden changes.",
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
      titleTr = "Feeding review",
      messageTr = "Review feeding amount and remove excess food if needed.",
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
      titleTr = "Startup phase complete",
      messageTr = "Your aquarium has reached the end of the startup phase. Continue with a regular maintenance routine.",
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
      titleTr = "Weekly water change",
      messageTr = "Perform a regular water change to keep water quality stable.",
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
      titleTr = "Plant trimming check",
      messageTr = "Check plant growth and trim overgrown or unhealthy sections if needed.",
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
      titleTr = "Filter maintenance check",
      messageTr = "Check filter flow and clean mechanical media only if the flow is reduced.",
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
      titleTr = "CO₂ system check",
      messageTr = "Check CO₂ timing, bubble rate, drop checker color, and livestock behavior.",
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
      titleTr = "Livestock health check",
      messageTr = "Check appetite, behavior, breathing, and visible signs of stress.",
      priority = SmartCarePriority.MEDIUM,
      repeatMode = SmartCareRepeatMode.WEEKLY,
      sourceTags = listOf("SmartCare")
    )
  )

  val allRules: List<SmartCareRule>
    get() = startupRules + matureTankRules
}
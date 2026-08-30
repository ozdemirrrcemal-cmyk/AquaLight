package com.aqua.aqualight.data.care.smartcare

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import java.util.Calendar
import java.util.concurrent.TimeUnit

object SmartCareTaskGenerator {

  private const val SMART_TASK_MIN_LEAD_MINUTES = 30
  private const val CARE_DAY_END_HOUR = 21

  fun generateForTanks(
    context: Context,
    tanks: List<SavedAquariumTank>,
    nowMillis: Long = System.currentTimeMillis()
  ): List<SmartCareGeneratedTask> {
    return tanks.flatMap {
      tank ->
      generateForTank(
        context = context,
        tank = tank,
        nowMillis = nowMillis
      )
    }
  }

  fun generateForTank(
    context: Context,
    tank: SavedAquariumTank,
    nowMillis: Long = System.currentTimeMillis()
  ): List<SmartCareGeneratedTask> {
    val profile = SmartCareProfileBuilder.build(
      tank = tank,
      nowMillis = nowMillis
    )

    val setupDay = profile.setupDay ?: return emptyList()

    return SmartCareRuleCatalog.allRules
      .filter {
        rule ->
        isRuleActiveForDay(
          rule = rule,
          setupDay = setupDay
        )
      }
      .filter {
        rule ->
        doesProfileMatchRule(
          profile = profile,
          rule = rule
        )
      }
      .filter {
        rule ->
        isRuleDueToday(
          rule = rule,
          setupDay = setupDay
        )
      }
      .map {
        rule ->
        createGeneratedTask(
          context = context,
          tank = tank,
          profile = profile,
          rule = rule,
          nowMillis = nowMillis
        )
      }
      .sortedWith(
        compareByDescending<SmartCareGeneratedTask> {
          getPriorityWeight(it.priority)
        }.thenBy {
          getTaskTypeWeight(it.taskType)
        }.thenBy {
          it.dueAtMillis
        }
      )
      .let {
        tasks ->
        applyTaskDensityPolicy(
          tasks = tasks,
          profile = profile
        )
      }
  }

  private fun isRuleActiveForDay(
    rule: SmartCareRule,
    setupDay: Int
  ): Boolean {
    return setupDay in rule.dayStart..rule.dayEnd
  }

  private fun doesProfileMatchRule(
    profile: SmartCareTankProfile,
    rule: SmartCareRule
  ): Boolean {
    return rule.conditions.all {
      condition ->
      profile.conditions.contains(condition)
    }
  }

  private fun isRuleDueToday(
    rule: SmartCareRule,
    setupDay: Int
  ): Boolean {
    val daysFromStart = setupDay - rule.dayStart

    if (daysFromStart < 0) {
      return false
    }

    return when (rule.repeatMode) {
      SmartCareRepeatMode.ONCE -> {
        setupDay == rule.dayStart
      }

      SmartCareRepeatMode.DAILY -> {
        true
      }

      SmartCareRepeatMode.EVERY_2_DAYS -> {
        daysFromStart % 2 == 0
      }

      SmartCareRepeatMode.EVERY_3_DAYS -> {
        daysFromStart % 3 == 0
      }

      SmartCareRepeatMode.WEEKLY -> {
        daysFromStart % 7 == 0
      }

      SmartCareRepeatMode.EVERY_2_WEEKS -> {
        daysFromStart % 14 == 0
      }

      SmartCareRepeatMode.MONTHLY -> {
        daysFromStart % 30 == 0
      }
    }
  }

  private fun createGeneratedTask(
    context: Context,
    tank: SavedAquariumTank,
    profile: SmartCareTankProfile,
    rule: SmartCareRule,
    nowMillis: Long
  ): SmartCareGeneratedTask {
    val dueAtMillis = getDueTimeForRule(
      rule = rule,
      nowMillis = nowMillis
    )

    return SmartCareGeneratedTask(
      id = buildGeneratedTaskId(
        tankId = profile.tankId,
        rule = rule,
        setupDay = profile.setupDay
      ),
      ownerUid = tank.ownerUid,
      tankId = profile.tankId,
      tankName = profile.tankName,
      ruleId = rule.id,
      taskType = rule.taskType,
      titleTr = context.getString(rule.titleRes),
      messageTr = buildShortMessage(
        context = context,
        tank = tank,
        rule = rule,
        profile = profile
      ),
      priority = rule.priority,
      dueAtMillis = dueAtMillis,
      setupDay = profile.setupDay,
      requiresWaterTest = rule.requiresWaterTest,
      sourceTags = rule.sourceTags,
      waterChangePercent = getWaterChangePercent(
        rule = rule,
        profile = profile
      )
    )
  }

  private fun buildGeneratedTaskId(
    tankId: Long,
    rule: SmartCareRule,
    setupDay: Int?
  ): String {
    return "smart_${tankId}_${rule.id}_${setupDay ?: 0}"
  }

  private fun getWaterChangePercent(
    rule: SmartCareRule,
    profile: SmartCareTankProfile
  ): Int? {
    if (rule.taskType != SmartCareTaskType.WATER_CHANGE) {
      return null
    }

    val setupDay = profile.setupDay ?: return 30

    return when {
      profile.hasActiveSoil && setupDay in 1..7 -> {
        50
      }

      profile.hasActiveSoil && setupDay in 8..21 -> {
        40
      }

      setupDay in 1..28 -> {
        30
      }

      setupDay in 29..90 -> {
        30
      }

      else -> {
        25
      }
    }
  }

  private fun buildShortMessage(
    context: Context,
    tank: SavedAquariumTank,
    rule: SmartCareRule,
    profile: SmartCareTankProfile
  ): String {
    val setupDay = profile.setupDay

    return when (rule.taskType) {
      SmartCareTaskType.WATER_CHANGE -> {
        val percent = getWaterChangePercent(
          rule = rule,
          profile = profile
        ) ?: 30

        if (setupDay != null) {
          context.getString(
            R.string.maintenance_smart_msg_water_change_day,
            setupDay,
            percent
          )
        } else {
          context.getString(
            R.string.maintenance_smart_msg_water_change,
            percent
          )
        }
      }

      SmartCareTaskType.FERTILIZER -> {
        buildFertilizerMessage(
          context = context,
          tank = tank,
          profile = profile
        )
      }

      SmartCareTaskType.CO2_CHECK -> {
        context.getString(R.string.maintenance_smart_msg_co2_check)
      }

      SmartCareTaskType.FEEDING -> {
        context.getString(R.string.maintenance_smart_msg_feeding)
      }

      SmartCareTaskType.LIGHTING -> {
        context.getString(R.string.maintenance_smart_msg_lighting)
      }

      SmartCareTaskType.WATER_TEST -> {
        context.getString(R.string.maintenance_smart_msg_water_test)
      }

      SmartCareTaskType.PLANT_CHECK -> {
        context.getString(R.string.maintenance_smart_msg_plant_check)
      }

      SmartCareTaskType.PLANT_TRIM -> {
        context.getString(R.string.maintenance_smart_msg_plant_trim)
      }

      SmartCareTaskType.FILTER_CHECK -> {
        context.getString(R.string.maintenance_smart_msg_filter_check)
      }

      SmartCareTaskType.GLASS_CLEANING -> {
        context.getString(R.string.maintenance_smart_msg_glass_cleaning)
      }

      SmartCareTaskType.LIVESTOCK_CHECK -> {
        context.getString(R.string.maintenance_smart_msg_livestock_check)
      }

      SmartCareTaskType.GENERAL_CHECK -> {
        context.getString(rule.messageRes)
      }
    }
  }

  private fun buildFertilizerMessage(
    context: Context,
    tank: SavedAquariumTank,
    profile: SmartCareTankProfile
  ): String {
    val fertilizerRule = findSelectedFertilizerRule(
      tank = tank
    )

    if (fertilizerRule == null) {
      return if (profile.setupDay != null) {
        context.getString(
          R.string.maintenance_smart_msg_fertilizer_missing_day,
          profile.setupDay
        )
      } else {
        context.getString(R.string.maintenance_smart_msg_fertilizer_missing)
      }
    }

    val recommendation = SmartFertilizerDoseCalculator.calculate(
      rule = fertilizerRule,
      grossVolumeL = profile.grossVolumeL,
      setupDay = profile.setupDay,
      hasActiveSoil = profile.hasActiveSoil
    )

    val productName = fertilizerRule.productName

    val frequencyText = getFrequencyText(
      context = context,
      frequency = fertilizerRule.frequency
    )

    val setupDay = profile.setupDay

    if (
      setupDay != null &&
      setupDay <= 7 &&
      recommendation.startupDoseFactor == 0.0
    ) {
      return context.getString(
        R.string.maintenance_smart_msg_delay_fertilizer_day,
        setupDay,
        productName
      )
    }

    if (
      setupDay != null &&
      setupDay <= 30
    ) {
      return context.getString(
        R.string.maintenance_smart_msg_reduced_fertilizer_day,
        setupDay,
        recommendation.startupDoseMl.toString(),
        productName
      )
    }

    return context.getString(
      R.string.maintenance_smart_msg_normal_fertilizer,
      recommendation.normalDoseMl.toString(),
      productName,
      frequencyText
    )
  }

  private fun findSelectedFertilizerRule(
    tank: SavedAquariumTank
  ): FertilizerDoseRule? {
    val materialText = tank.materials.joinToString(
      separator = " "
    ) {
      material ->
      listOf(
        material.brand,
        material.name,
        material.note,
        material.categoryKey,
        material.categoryTitle
      ).joinToString(
        separator = " "
      )
    }.lowercase()

    if (materialText.isBlank()) {
      return null
    }

    return FertilizerDoseCatalog.rules.firstOrNull {
      rule ->
      val productName = rule.productName.lowercase()
      val brandName = rule.brand.name.lowercase()

      materialText.contains(productName) ||
        productName.split(" ").all {
          token ->
          token.length <= 2 || materialText.contains(token)
        } ||
        materialText.contains(brandName)
    }
  }

  private fun getFrequencyText(
    context: Context,
    frequency: FertilizerFrequency
  ): String {
    return when (frequency) {
      FertilizerFrequency.DAILY -> {
        context.getString(R.string.maintenance_smart_frequency_daily)
      }

      FertilizerFrequency.WEEKLY -> {
        context.getString(R.string.maintenance_smart_frequency_weekly)
      }

      FertilizerFrequency.ONCE_OR_TWICE_WEEKLY -> {
        context.getString(R.string.maintenance_smart_frequency_once_or_twice_weekly)
      }

      FertilizerFrequency.TWICE_WEEKLY -> {
        context.getString(R.string.maintenance_smart_frequency_twice_weekly)
      }

      FertilizerFrequency.TWO_TO_THREE_TIMES_WEEKLY -> {
        context.getString(R.string.maintenance_smart_frequency_two_to_three_times_weekly)
      }

      FertilizerFrequency.AS_NEEDED -> {
        context.getString(R.string.maintenance_smart_frequency_as_needed)
      }
    }
  }

  private fun applyTaskDensityPolicy(
    tasks: List<SmartCareGeneratedTask>,
    profile: SmartCareTankProfile
  ): List<SmartCareGeneratedTask> {
    if (tasks.isEmpty()) {
      return emptyList()
    }

    val uniqueTasks = tasks.distinctBy {
      task ->
      getTaskDensityKey(
        task = task
      )
    }

    val maxTaskCount = getMaxTaskCountForSetupDay(
      setupDay = profile.setupDay
    )

    return uniqueTasks
      .take(maxTaskCount)
      .sortedWith(
        compareByDescending<SmartCareGeneratedTask> {
          getPriorityWeight(it.priority)
        }.thenBy {
          it.dueAtMillis
        }
      )
  }

  private fun getTaskDensityKey(
    task: SmartCareGeneratedTask
  ): String {
    return when (task.taskType) {
      SmartCareTaskType.WATER_CHANGE -> {
        "water_change"
      }

      SmartCareTaskType.WATER_TEST -> {
        "water_test"
      }

      SmartCareTaskType.LIGHTING -> {
        "lighting"
      }

      SmartCareTaskType.CO2_CHECK -> {
        "co2_check"
      }

      SmartCareTaskType.FERTILIZER -> {
        "fertilizer"
      }

      SmartCareTaskType.PLANT_CHECK -> {
        "plant_check"
      }

      SmartCareTaskType.PLANT_TRIM -> {
        "plant_trim"
      }

      SmartCareTaskType.FILTER_CHECK -> {
        "filter_check"
      }

      SmartCareTaskType.GLASS_CLEANING -> {
        "glass_cleaning"
      }

      SmartCareTaskType.LIVESTOCK_CHECK -> {
        "livestock_check"
      }

      SmartCareTaskType.FEEDING -> {
        "feeding"
      }

      SmartCareTaskType.GENERAL_CHECK -> {
        "general_check"
      }
    }
  }

  private fun getMaxTaskCountForSetupDay(
    setupDay: Int?
  ): Int {
    if (setupDay == null) {
      return 3
    }

    return when (setupDay) {
      in 1..7 -> {
        5
      }

      in 8..30 -> {
        4
      }

      in 31..90 -> {
        3
      }

      else -> {
        3
      }
    }
  }

  private fun getTaskTypeWeight(
    taskType: SmartCareTaskType
  ): Int {
    return when (taskType) {
      SmartCareTaskType.WATER_TEST -> {
        12
      }

      SmartCareTaskType.WATER_CHANGE -> {
        11
      }

      SmartCareTaskType.CO2_CHECK -> {
        10
      }

      SmartCareTaskType.FEEDING -> {
        9
      }

      SmartCareTaskType.FERTILIZER -> {
        8
      }

      SmartCareTaskType.LIGHTING -> {
        7
      }

      SmartCareTaskType.LIVESTOCK_CHECK -> {
        6
      }

      SmartCareTaskType.PLANT_CHECK -> {
        5
      }

      SmartCareTaskType.PLANT_TRIM -> {
        4
      }

      SmartCareTaskType.FILTER_CHECK -> {
        3
      }

      SmartCareTaskType.GLASS_CLEANING -> {
        2
      }

      SmartCareTaskType.GENERAL_CHECK -> {
        1
      }
    }
  }

  private fun getDueTimeForRule(
    rule: SmartCareRule,
    nowMillis: Long
  ): Long {
    val preferredDueAtMillis = getPreferredDueTimeForDate(
      taskType = rule.taskType,
      baseMillis = nowMillis,
      addDays = 0
    )

    val minimumAllowedMillis = nowMillis + TimeUnit.MINUTES.toMillis(
      SMART_TASK_MIN_LEAD_MINUTES.toLong()
    )

    if (preferredDueAtMillis >= minimumAllowedMillis) {
      return preferredDueAtMillis
    }

    return if (rule.repeatMode == SmartCareRepeatMode.ONCE) {
      getNextAvailableOneTimeSlot(
        taskType = rule.taskType,
        nowMillis = nowMillis
      )
    } else {
      getPreferredDueTimeForDate(
        taskType = rule.taskType,
        baseMillis = nowMillis,
        addDays = 1
      )
    }
  }

  private fun getNextAvailableOneTimeSlot(
    taskType: SmartCareTaskType,
    nowMillis: Long
  ): Long {
    val candidate = roundUpToNextHalfHour(
      millis = nowMillis + TimeUnit.MINUTES.toMillis(
        SMART_TASK_MIN_LEAD_MINUTES.toLong()
      )
    )

    val candidateCalendar = Calendar.getInstance().apply {
      timeInMillis = candidate
    }

    if (candidateCalendar.get(Calendar.HOUR_OF_DAY) >= CARE_DAY_END_HOUR) {
      return getPreferredDueTimeForDate(
        taskType = taskType,
        baseMillis = nowMillis,
        addDays = 1
      )
    }

    return candidate
  }

  private fun roundUpToNextHalfHour(
    millis: Long
  ): Long {
    val calendar = Calendar.getInstance().apply {
      timeInMillis = millis
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val minute = calendar.get(Calendar.MINUTE)

    when {
      minute == 0 || minute == 30 -> {
        // keep current minute
      }

      minute < 30 -> {
        calendar.set(Calendar.MINUTE, 30)
      }

      else -> {
        calendar.add(Calendar.HOUR_OF_DAY, 1)
        calendar.set(Calendar.MINUTE, 0)
      }
    }

    return calendar.timeInMillis
  }

  private fun getPreferredDueTimeForDate(
    taskType: SmartCareTaskType,
    baseMillis: Long,
    addDays: Int
  ): Long {
    val dueTime = getPreferredDueTime(
      taskType = taskType
    )

    return Calendar.getInstance().apply {
      timeInMillis = baseMillis
      add(Calendar.DAY_OF_YEAR, addDays)
      set(Calendar.HOUR_OF_DAY, dueTime.first)
      set(Calendar.MINUTE, dueTime.second)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
  }

  private fun getPreferredDueTime(
    taskType: SmartCareTaskType
  ): Pair<Int, Int> {
    return when (taskType) {
      SmartCareTaskType.CO2_CHECK -> {
        9 to 0
      }

      SmartCareTaskType.LIGHTING -> {
        9 to 30
      }

      SmartCareTaskType.GENERAL_CHECK -> {
        10 to 0
      }

      SmartCareTaskType.FERTILIZER -> {
        10 to 30
      }

      SmartCareTaskType.PLANT_CHECK -> {
        13 to 0
      }

      SmartCareTaskType.PLANT_TRIM -> {
        13 to 0
      }

      SmartCareTaskType.GLASS_CLEANING -> {
        15 to 0
      }

      SmartCareTaskType.FILTER_CHECK -> {
        15 to 0
      }

      SmartCareTaskType.WATER_CHANGE -> {
        18 to 0
      }

      SmartCareTaskType.WATER_TEST -> {
        19 to 0
      }

      SmartCareTaskType.FEEDING -> {
        19 to 30
      }

      SmartCareTaskType.LIVESTOCK_CHECK -> {
        20 to 0
      }
    }
  }

  private fun getPriorityWeight(
    priority: SmartCarePriority
  ): Int {
    return when (priority) {
      SmartCarePriority.CRITICAL -> {
        4
      }

      SmartCarePriority.HIGH -> {
        3
      }

      SmartCarePriority.MEDIUM -> {
        2
      }

      SmartCarePriority.LOW -> {
        1
      }
    }
  }
}

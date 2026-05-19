package com.aqua.aqualight.ui.tabs.maintenance.smartcare

import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import java.util.Calendar

object SmartCareTaskGenerator {

  fun generateForTanks(
    tanks: List<SavedAquariumTank>,
    nowMillis: Long = System.currentTimeMillis()
  ): List<SmartCareGeneratedTask> {
    return tanks.flatMap { tank ->
      generateForTank(
        tank = tank,
        nowMillis = nowMillis
      )
    }
  }

  fun generateForTank(
    tank: SavedAquariumTank,
    nowMillis: Long = System.currentTimeMillis()
  ): List<SmartCareGeneratedTask> {
    val profile = SmartCareProfileBuilder.build(
      tank = tank,
      nowMillis = nowMillis
    )

    val setupDay = profile.setupDay ?: return emptyList()

    return SmartCareRuleCatalog.allRules
      .filter { rule ->
        isRuleActiveForDay(
          rule = rule,
          setupDay = setupDay
        )
      }
      .filter { rule ->
        doesProfileMatchRule(
          profile = profile,
          rule = rule
        )
      }
      .filter { rule ->
        isRuleDueToday(
          rule = rule,
          setupDay = setupDay
        )
      }
      .map { rule ->
        createGeneratedTask(
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
          it.dueAtMillis
        }
      )
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
    return rule.conditions.all { condition ->
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
    tank: SavedAquariumTank,
    profile: SmartCareTankProfile,
    rule: SmartCareRule,
    nowMillis: Long
  ): SmartCareGeneratedTask {
    val dueAtMillis = getDueTimeForToday(
      taskType = rule.taskType,
      nowMillis = nowMillis
    )

    return SmartCareGeneratedTask(
      id = buildGeneratedTaskId(
        tankId = profile.tankId,
        rule = rule,
        setupDay = profile.setupDay
      ),
      tankId = profile.tankId,
      tankName = profile.tankName,
      ruleId = rule.id,
      taskType = rule.taskType,
      titleTr = rule.titleTr,
      messageTr = buildShortMessage(
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
      profile.hasActiveSoil && setupDay in 1..7 -> 50
      profile.hasActiveSoil && setupDay in 8..21 -> 40
      setupDay in 1..28 -> 30
      setupDay in 29..90 -> 30
      else -> 25
    }
  }

  private fun buildShortMessage(
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
          "Day $setupDay. Change about $percent% of the water to help keep the aquarium stable."
        } else {
          "Change about $percent% of the water to help keep the aquarium stable."
        }
      }

      SmartCareTaskType.FERTILIZER -> {
        buildFertilizerMessage(
          tank = tank,
          profile = profile
        )
      }

      SmartCareTaskType.CO2_CHECK -> {
        "Check CO₂ timing, drop checker color, and livestock behavior."
      }

      SmartCareTaskType.FEEDING -> {
        "Feed lightly and remove excess food if needed."
      }

      SmartCareTaskType.LIGHTING -> {
        "Check the light period and keep it stable."
      }

      SmartCareTaskType.WATER_TEST -> {
        "Test water before making livestock or dosing decisions."
      }

      SmartCareTaskType.PLANT_CHECK -> {
        "Check plant health, melting leaves, and weak growth."
      }

      SmartCareTaskType.PLANT_TRIM -> {
        "Trim overgrown or unhealthy plant sections if needed."
      }

      SmartCareTaskType.FILTER_CHECK -> {
        "Check filter flow and clean only if flow is reduced."
      }

      SmartCareTaskType.GLASS_CLEANING -> {
        "Check glass and hardscape for early algae signs."
      }

      SmartCareTaskType.LIVESTOCK_CHECK -> {
        "Check livestock behavior, appetite, and visible stress signs."
      }

      SmartCareTaskType.GENERAL_CHECK -> {
        rule.messageTr
      }
    }
  }

  private fun buildFertilizerMessage(
    tank: SavedAquariumTank,
    profile: SmartCareTankProfile
  ): String {
    val fertilizerRule = findSelectedFertilizerRule(
      tank = tank
    )

    if (fertilizerRule == null) {
      return if (profile.setupDay != null) {
        "Day ${profile.setupDay}. Add your fertilizer product to improve automatic dosing recommendations."
      } else {
        "Add your fertilizer product to improve automatic dosing recommendations."
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
      frequency = fertilizerRule.frequency
    )

    val setupDay = profile.setupDay

    if (
      setupDay != null &&
      setupDay <= 7 &&
      recommendation.startupDoseFactor == 0.0
    ) {
      return "Day $setupDay. Delay $productName dosing for now and monitor plant response."
    }

    if (
      setupDay != null &&
      setupDay <= 30
    ) {
      return "Day $setupDay. Use about ${recommendation.startupDoseMl} mL of $productName as a reduced startup dose."
    }

    return "Dose about ${recommendation.normalDoseMl} mL of $productName $frequencyText."
  }

  private fun findSelectedFertilizerRule(
    tank: SavedAquariumTank
  ): FertilizerDoseRule? {
    val materialText = tank.materials.joinToString(
      separator = " "
    ) { material ->
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

    return FertilizerDoseCatalog.rules.firstOrNull { rule ->
      val productName = rule.productName.lowercase()
      val brandName = rule.brand.name.lowercase()

      materialText.contains(productName) ||
        productName.split(" ").all { token ->
          token.length <= 2 || materialText.contains(token)
        } ||
        materialText.contains(brandName)
    }
  }

  private fun getFrequencyText(
    frequency: FertilizerFrequency
  ): String {
    return when (frequency) {
      FertilizerFrequency.DAILY -> "daily"
      FertilizerFrequency.WEEKLY -> "weekly"
      FertilizerFrequency.ONCE_OR_TWICE_WEEKLY -> "once or twice weekly"
      FertilizerFrequency.TWICE_WEEKLY -> "twice weekly"
      FertilizerFrequency.TWO_TO_THREE_TIMES_WEEKLY -> "2–3 times weekly"
      FertilizerFrequency.AS_NEEDED -> "as needed"
    }
  }

  private fun getDueTimeForToday(
    taskType: SmartCareTaskType,
    nowMillis: Long
  ): Long {
    val hour = when (taskType) {
      SmartCareTaskType.CO2_CHECK -> 8
      SmartCareTaskType.LIGHTING -> 9
      SmartCareTaskType.FEEDING -> 9
      SmartCareTaskType.GENERAL_CHECK -> 9
      SmartCareTaskType.FERTILIZER -> 10
      SmartCareTaskType.WATER_CHANGE -> 11
      SmartCareTaskType.PLANT_CHECK -> 12
      SmartCareTaskType.PLANT_TRIM -> 12
      SmartCareTaskType.FILTER_CHECK -> 13
      SmartCareTaskType.GLASS_CLEANING -> 13
      SmartCareTaskType.WATER_TEST -> 18
      SmartCareTaskType.LIVESTOCK_CHECK -> 18
    }

    return Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
  }

  private fun getPriorityWeight(
    priority: SmartCarePriority
  ): Int {
    return when (priority) {
      SmartCarePriority.CRITICAL -> 4
      SmartCarePriority.HIGH -> 3
      SmartCarePriority.MEDIUM -> 2
      SmartCarePriority.LOW -> 1
    }
  }
}
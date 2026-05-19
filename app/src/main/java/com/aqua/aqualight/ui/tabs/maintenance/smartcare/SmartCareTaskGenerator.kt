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
      messageTr = enrichMessage(
        profile = profile,
        rule = rule
      ),
      priority = rule.priority,
      dueAtMillis = dueAtMillis,
      setupDay = profile.setupDay,
      requiresWaterTest = rule.requiresWaterTest,
      sourceTags = rule.sourceTags
    )
  }

  private fun buildGeneratedTaskId(
    tankId: Long,
    rule: SmartCareRule,
    setupDay: Int?
  ): String {
    return "smart_${tankId}_${rule.id}_${setupDay ?: 0}"
  }

  private fun enrichMessage(
    profile: SmartCareTankProfile,
    rule: SmartCareRule
  ): String {
    val setupDayText = profile.setupDay?.let { day ->
      "Kurulum günü: $day. "
    }.orEmpty()

    val volumeText = if (profile.estimatedWaterVolumeL > 0.0) {
      "Tahmini su hacmi: ${profile.estimatedWaterVolumeL} L. "
    } else {
      ""
    }

    return when (rule.taskType) {
      SmartCareTaskType.FERTILIZER -> {
        setupDayText + volumeText + rule.messageTr
      }

      SmartCareTaskType.WATER_CHANGE -> {
        setupDayText + rule.messageTr
      }

      SmartCareTaskType.WATER_TEST -> {
        setupDayText + rule.messageTr
      }

      else -> {
        rule.messageTr
      }
    }
  }

  private fun getDueTimeForToday(
    taskType: SmartCareTaskType,
    nowMillis: Long
  ): Long {
    val hour = when (taskType) {
      SmartCareTaskType.CO2_CHECK -> 8
      SmartCareTaskType.LIGHTING -> 9
      SmartCareTaskType.FERTILIZER -> 10
      SmartCareTaskType.WATER_CHANGE -> 11
      SmartCareTaskType.WATER_TEST -> 18
      SmartCareTaskType.FEEDING -> 9
      SmartCareTaskType.PLANT_CHECK -> 12
      SmartCareTaskType.PLANT_TRIM -> 12
      SmartCareTaskType.FILTER_CHECK -> 13
      SmartCareTaskType.GLASS_CLEANING -> 13
      SmartCareTaskType.LIVESTOCK_CHECK -> 18
      SmartCareTaskType.GENERAL_CHECK -> 9
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
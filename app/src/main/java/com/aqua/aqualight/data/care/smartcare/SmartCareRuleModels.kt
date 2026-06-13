package com.aqua.aqualight.data.care.smartcare

import androidx.annotation.StringRes

enum class SmartCareCondition {
  PLANTED,
  NO_PLANTS,

  HAS_CO2,
  NO_CO2,

  HAS_ACTIVE_SOIL,
  NO_ACTIVE_SOIL,

  HAS_FERTILIZER,
  FERTILIZER_UNKNOWN,

  HAS_LIVESTOCK,
  NO_LIVESTOCK,

  HAS_SHRIMP,
  HAS_FISH,

  HAS_LIGHT,
  HAS_FILTER,

  HIGH_TECH,
  LOW_TECH,

  STARTUP_PERIOD,
  MATURE_TANK
}

enum class SmartCareTaskType {
  WATER_CHANGE,
  WATER_TEST,
  LIGHTING,
  CO2_CHECK,
  FERTILIZER,
  PLANT_CHECK,
  PLANT_TRIM,
  FILTER_CHECK,
  GLASS_CLEANING,
  LIVESTOCK_CHECK,
  FEEDING,
  GENERAL_CHECK
}

enum class SmartCarePriority {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}

enum class SmartCareRepeatMode {
  ONCE,
  DAILY,
  EVERY_2_DAYS,
  EVERY_3_DAYS,
  WEEKLY,
  EVERY_2_WEEKS,
  MONTHLY
}

data class SmartCareRule(
  val id: String,
  val dayStart: Int,
  val dayEnd: Int,
  val conditions: List<SmartCareCondition>,
  val taskType: SmartCareTaskType,
  @StringRes val titleRes: Int,
  @StringRes val messageRes: Int,
  val priority: SmartCarePriority,
  val repeatMode: SmartCareRepeatMode,
  val requiresWaterTest: Boolean = false,
  val blocksIfCompletedToday: Boolean = true,
  val sourceTags: List<String>
)

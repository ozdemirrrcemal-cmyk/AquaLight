package com.aqua.aqualight.data.care.model

data class CareTask(
  val id: Long,
  val ownerUid: String = "",
  val tankId: Long,
  val title: String,
  val description: String,
  val type: CareTaskType,
  val source: CareTaskSource,
  val status: CareTaskStatus,
  val dueAtMillis: Long,
  val completedAtMillis: Long?,
  val repeatEnabled: Boolean,
  val repeatIntervalDays: Int,
  val reminderEnabled: Boolean,
  val missedReminderEnabled: Boolean,
  val missedReminderDays: Int,
  val waterChangePercent: Int?,
  val note: String,
  val generatedRuleKey: String,
  val createdAtMillis: Long,
  val updatedAtMillis: Long
)

enum class CareTaskSource {
  MANUAL,
  AUTOMATIC
}

enum class CareTaskStatus {
  PENDING,
  COMPLETED
}

enum class CareTaskType {
  WATER_CHANGE,
  FEEDING,

  FILTER_MAINTENANCE,
  FILTER_CHANGE,
  PRE_FILTER_CLEANING,
  PIPE_CLEANING,
  DIFFUSER_CLEANING,
  HOSE_CLEANING,

  GLASS_CLEANING,
  ALGAE_CLEANING,

  PLANT_TRIM,
  FERTILIZER_DOSING,
  PLANT_HEALTH_CHECK,

  CO2_CHECK,
  LIGHT_CHECK,

  WATER_TEST,
  TEMPERATURE_CHECK,
  SUBSTRATE_CLEANING,

  LIVESTOCK_CHECK,
  DEVICE_CHECK,

  CUSTOM
}

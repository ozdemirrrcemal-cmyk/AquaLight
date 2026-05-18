package com.aqua.aqualight.ui.tabs.maintenance.model

data class CareTaskUi(
  val id: Long,
  val tankId: Long,
  val tankName: String,
  val title: String,
  val description: String,
  val type: CareTaskType,
  val typeTitle: String,
  val source: CareTaskSource,
  val sourceLabel: String,
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
  val iconRes: Int,
  val accentColor: String,
  val isOverdue: Boolean,
  val primaryTimeText: String,
  val secondaryText: String
)
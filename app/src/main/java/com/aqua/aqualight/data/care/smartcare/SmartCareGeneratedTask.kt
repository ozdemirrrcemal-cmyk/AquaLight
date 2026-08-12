package com.aqua.aqualight.data.care.smartcare

data class SmartCareGeneratedTask(
  val id: String,
  val ownerUid: String = "",
  val tankId: Long,
  val tankName: String,
  val ruleId: String,
  val taskType: SmartCareTaskType,
  val titleTr: String,
  val messageTr: String,
  val priority: SmartCarePriority,
  val dueAtMillis: Long,
  val setupDay: Int?,
  val requiresWaterTest: Boolean,
  val sourceTags: List<String>,
  val waterChangePercent: Int? = null
)

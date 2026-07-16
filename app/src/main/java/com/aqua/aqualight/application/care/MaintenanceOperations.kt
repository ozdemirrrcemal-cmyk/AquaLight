package com.aqua.aqualight.application.care

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import kotlinx.coroutines.flow.Flow

/** Owner-scoped application boundary for maintenance and Smart Care workflows. */
interface MaintenanceOperations {
    val tasks: Flow<List<CareTaskSnapshot>>

    fun task(taskId: Long): Flow<CareTaskSnapshot?>

    suspend fun syncSmartCareTasks(tanks: List<AquariumTankSnapshot>)

    suspend fun completeTask(taskId: Long)

    suspend fun deleteTask(taskId: Long)

    suspend fun updateCompletedTaskDate(taskId: Long, completedAtMillis: Long)

    suspend fun addCompletedActivity(input: CompletedCareActivityInput)

    suspend fun deleteManualTask(taskId: Long)

    suspend fun addManualTask(input: ManualCareTaskInput)

    suspend fun updateManualTask(taskId: Long, input: ManualCareTaskInput)
}

data class CareTaskSnapshot(
    val id: Long,
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
    val createdAtMillis: Long
)

data class CompletedCareActivityInput(
    val tankId: Long,
    val type: CareTaskType,
    val completedAtMillis: Long,
    val waterChangePercent: Int? = null,
    val note: String = ""
)

data class ManualCareTaskInput(
    val tankId: Long,
    val title: String,
    val description: String,
    val type: CareTaskType,
    val dueAtMillis: Long,
    val repeatEnabled: Boolean,
    val repeatIntervalDays: Int,
    val reminderEnabled: Boolean,
    val missedReminderEnabled: Boolean,
    val missedReminderDays: Int,
    val waterChangePercent: Int?,
    val note: String
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

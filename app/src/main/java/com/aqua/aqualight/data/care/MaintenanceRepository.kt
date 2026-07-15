package com.aqua.aqualight.data.care

import android.content.Context
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.care.smartcare.SmartCareTaskGenerator
import kotlinx.coroutines.flow.Flow

/** Testable maintenance data boundary used by the UI layer. */
interface MaintenanceRepository {
    val tasksFlow: Flow<List<CareTask>>

    fun taskFlow(taskId: Long): Flow<CareTask?>

    suspend fun syncSmartCareTasks(tanks: List<SavedAquariumTank>)

    suspend fun completeTask(taskId: Long)

    suspend fun deleteTask(taskId: Long)

    suspend fun updateCompletedTaskDate(taskId: Long, completedAtMillis: Long)

    suspend fun addCompletedActivity(
        tankId: Long,
        type: CareTaskType,
        completedAtMillis: Long,
        waterChangePercent: Int?,
        note: String
    )

    suspend fun deleteManualTask(taskId: Long)

    suspend fun addManualTask(
        tankId: Long,
        title: String,
        description: String,
        type: CareTaskType,
        dueAtMillis: Long,
        repeatEnabled: Boolean,
        repeatIntervalDays: Int,
        reminderEnabled: Boolean,
        missedReminderEnabled: Boolean,
        missedReminderDays: Int,
        waterChangePercent: Int?,
        note: String
    )

    suspend fun updateManualTask(
        taskId: Long,
        tankId: Long,
        title: String,
        description: String,
        type: CareTaskType,
        dueAtMillis: Long,
        repeatEnabled: Boolean,
        repeatIntervalDays: Int,
        reminderEnabled: Boolean,
        missedReminderEnabled: Boolean,
        missedReminderDays: Int,
        waterChangePercent: Int?,
        note: String
    )
}

class DefaultMaintenanceRepository(
    context: Context,
    private val manager: CareTaskDataStoreManager
) : MaintenanceRepository {

    private val appContext = context.applicationContext

    override val tasksFlow: Flow<List<CareTask>> = manager.tasksFlow

    override fun taskFlow(taskId: Long): Flow<CareTask?> = manager.taskFlow(taskId)

    override suspend fun syncSmartCareTasks(tanks: List<SavedAquariumTank>) {
        if (tanks.isEmpty()) return
        manager.syncAutomaticTasks(
            generatedTasks = SmartCareTaskGenerator.generateForTanks(
                context = appContext,
                tanks = tanks
            )
        )
    }

    override suspend fun completeTask(taskId: Long) {
        manager.completeTask(taskId)
    }

    override suspend fun deleteTask(taskId: Long) {
        manager.deleteTask(taskId)
    }

    override suspend fun updateCompletedTaskDate(
        taskId: Long,
        completedAtMillis: Long
    ) {
        manager.updateCompletedTaskDate(taskId, completedAtMillis)
    }

    override suspend fun addCompletedActivity(
        tankId: Long,
        type: CareTaskType,
        completedAtMillis: Long,
        waterChangePercent: Int?,
        note: String
    ) {
        val typeDefinition = CareTaskTypeCatalog.get(type)
        manager.addCompletedActivity(
            tankId = tankId,
            title = typeDefinition.title(appContext),
            description = typeDefinition.defaultDescription(appContext),
            type = type,
            completedAtMillis = completedAtMillis,
            waterChangePercent = waterChangePercent,
            note = note
        )
    }

    override suspend fun deleteManualTask(taskId: Long) {
        manager.deleteManualTask(taskId)
    }

    override suspend fun addManualTask(
        tankId: Long,
        title: String,
        description: String,
        type: CareTaskType,
        dueAtMillis: Long,
        repeatEnabled: Boolean,
        repeatIntervalDays: Int,
        reminderEnabled: Boolean,
        missedReminderEnabled: Boolean,
        missedReminderDays: Int,
        waterChangePercent: Int?,
        note: String
    ) {
        manager.addManualTask(
            tankId = tankId,
            title = title,
            description = description,
            type = type,
            dueAtMillis = dueAtMillis,
            repeatEnabled = repeatEnabled,
            repeatIntervalDays = repeatIntervalDays,
            reminderEnabled = reminderEnabled,
            missedReminderEnabled = missedReminderEnabled,
            missedReminderDays = missedReminderDays,
            waterChangePercent = waterChangePercent,
            note = note
        )
    }

    override suspend fun updateManualTask(
        taskId: Long,
        tankId: Long,
        title: String,
        description: String,
        type: CareTaskType,
        dueAtMillis: Long,
        repeatEnabled: Boolean,
        repeatIntervalDays: Int,
        reminderEnabled: Boolean,
        missedReminderEnabled: Boolean,
        missedReminderDays: Int,
        waterChangePercent: Int?,
        note: String
    ) {
        manager.updateManualTask(
            taskId = taskId,
            tankId = tankId,
            title = title,
            description = description,
            type = type,
            dueAtMillis = dueAtMillis,
            repeatEnabled = repeatEnabled,
            repeatIntervalDays = repeatIntervalDays,
            reminderEnabled = reminderEnabled,
            missedReminderEnabled = missedReminderEnabled,
            missedReminderDays = missedReminderDays,
            waterChangePercent = waterChangePercent,
            note = note
        )
    }
}

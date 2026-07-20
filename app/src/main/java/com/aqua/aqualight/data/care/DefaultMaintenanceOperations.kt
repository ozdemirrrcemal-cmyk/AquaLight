package com.aqua.aqualight.data.care

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CompletedCareActivityInput
import com.aqua.aqualight.application.care.MaintenanceOperations
import com.aqua.aqualight.application.care.ManualCareTaskInput
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.smartcare.SmartCareTaskGenerator
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.aqua.aqualight.application.care.CareTaskSource as AppCareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus as AppCareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType as AppCareTaskType
import com.aqua.aqualight.data.care.model.CareTaskType as DataCareTaskType

class DefaultMaintenanceOperations(
    context: Context,
    private val manager: CareTaskDataStoreManager,
    private val notificationPreferences: NotificationPreferenceUseCase
) : MaintenanceOperations {

    private val appContext = context.applicationContext

    override val tasks: Flow<List<CareTaskSnapshot>> = manager.tasksFlow.map { tasks ->
        tasks.map(CareTask::toApplicationSnapshot)
    }

    override fun task(taskId: Long): Flow<CareTaskSnapshot?> =
        manager.taskFlow(taskId).map { task -> task?.toApplicationSnapshot() }

    override suspend fun syncSmartCareTasks(tanks: List<AquariumTankSnapshot>) {
        if (tanks.isEmpty()) return

        withCurrentOwnerScope { ownerUid ->
            manager.syncAutomaticTasks(
                generatedTasks = SmartCareTaskGenerator.generateForTanks(
                    context = appContext,
                    tanks = tanks.map { tank ->
                        tank.toDataTank(ownerUid)
                    }
                )
            )
            notificationPreferences.reconcileOwner(ownerUid)
        }
    }

    override suspend fun completeTask(taskId: Long) = withCurrentOwnerScope { ownerUid ->
        manager.completeTask(taskId)
        notificationPreferences.reconcileOwner(ownerUid)
    }

    override suspend fun deleteTask(taskId: Long) = withCurrentOwnerScope { ownerUid ->
        manager.deleteTask(taskId)
        notificationPreferences.cancelCareTask(ownerUid, taskId)
    }

    override suspend fun updateCompletedTaskDate(
        taskId: Long,
        completedAtMillis: Long
    ) = withCurrentOwnerScope {
        manager.updateCompletedTaskDate(taskId, completedAtMillis)
    }

    override suspend fun addCompletedActivity(
        input: CompletedCareActivityInput
    ) = withCurrentOwnerScope {
        val dataType = input.type.toDataType()
        val typeDefinition = CareTaskTypeCatalog.get(dataType)
        manager.addCompletedActivity(
            tankId = input.tankId,
            title = typeDefinition.title(appContext),
            description = typeDefinition.defaultDescription(appContext),
            type = dataType,
            completedAtMillis = input.completedAtMillis,
            waterChangePercent = input.waterChangePercent,
            note = input.note
        )
    }

    override suspend fun deleteManualTask(taskId: Long) =
        withCurrentOwnerScope { ownerUid ->
            manager.deleteManualTask(taskId)
            notificationPreferences.cancelCareTask(ownerUid, taskId)
        }

    override suspend fun addManualTask(
        input: ManualCareTaskInput
    ) = withCurrentOwnerScope { ownerUid ->
        val taskId = manager.addManualTask(
            tankId = input.tankId,
            title = input.title,
            description = input.description,
            type = input.type.toDataType(),
            dueAtMillis = input.dueAtMillis,
            repeatEnabled = input.repeatEnabled,
            repeatIntervalDays = input.repeatIntervalDays,
            reminderEnabled = input.reminderEnabled,
            missedReminderEnabled = input.missedReminderEnabled,
            missedReminderDays = input.missedReminderDays,
            waterChangePercent = input.waterChangePercent,
            note = input.note
        )
        notificationPreferences.scheduleCareTask(ownerUid, taskId)
    }

    override suspend fun updateManualTask(
        taskId: Long,
        input: ManualCareTaskInput
    ) = withCurrentOwnerScope { ownerUid ->
        manager.updateManualTask(
            taskId = taskId,
            tankId = input.tankId,
            title = input.title,
            description = input.description,
            type = input.type.toDataType(),
            dueAtMillis = input.dueAtMillis,
            repeatEnabled = input.repeatEnabled,
            repeatIntervalDays = input.repeatIntervalDays,
            reminderEnabled = input.reminderEnabled,
            missedReminderEnabled = input.missedReminderEnabled,
            missedReminderDays = input.missedReminderDays,
            waterChangePercent = input.waterChangePercent,
            note = input.note
        )
        notificationPreferences.scheduleCareTask(ownerUid, taskId)
    }
}

internal fun CareTask.toApplicationSnapshot(): CareTaskSnapshot = CareTaskSnapshot(
    id = id,
    tankId = tankId,
    title = title,
    description = description,
    type = AppCareTaskType.valueOf(type.name),
    source = AppCareTaskSource.valueOf(source.name),
    status = AppCareTaskStatus.valueOf(status.name),
    dueAtMillis = dueAtMillis,
    completedAtMillis = completedAtMillis,
    repeatEnabled = repeatEnabled,
    repeatIntervalDays = repeatIntervalDays,
    reminderEnabled = reminderEnabled,
    missedReminderEnabled = missedReminderEnabled,
    missedReminderDays = missedReminderDays,
    waterChangePercent = waterChangePercent,
    note = note,
    createdAtMillis = createdAtMillis,
    generatedRuleKey = generatedRuleKey
)

internal fun AppCareTaskType.toDataType(): DataCareTaskType =
    DataCareTaskType.valueOf(name)

internal fun AquariumTankSnapshot.toDataTank(
    ownerUid: String
): SavedAquariumTank = SavedAquariumTank(
    id = id,
    ownerUid = ownerUid,
    name = name,
    description = description,
    photoUri = photoUri,
    setupDateEpochDay = setupDateEpochDay,
    widthCm = widthCm,
    lengthCm = lengthCm,
    heightCm = heightCm,
    sizeUnit = sizeUnit,
    volumeUnit = volumeUnit,
    tankType = tankType,
    tankStyle = tankStyle,
    createdAtMillis = createdAtMillis,
    smartCareEnabled = smartCareEnabled,
    careRemindersEnabled = careRemindersEnabled,
    plants = plants.map(AquariumPlantTag::toDataPlant),
    materials = materials.map(AquariumMaterialSelection::toDataMaterial),
    livestock = livestock.map(AquariumLivestock::toDataLivestock)
)

private fun AquariumPlantTag.toDataPlant(): SavedAquariumPlant = SavedAquariumPlant(
    id = id,
    plantName = plantName,
    category = category,
    markerX = markerX,
    markerY = markerY
)

private fun AquariumMaterialSelection.toDataMaterial(): SavedAquariumMaterial =
    SavedAquariumMaterial(
        id = id,
        productId = productId,
        categoryKey = categoryKey,
        categoryTitle = categoryTitle,
        name = name,
        brand = brand,
        note = note
    )

private fun AquariumLivestock.toDataLivestock(): SavedAquariumLivestock =
    SavedAquariumLivestock(
        id = id,
        name = name,
        category = category,
        quantity = quantity,
        addedDateEpochDay = addedDateEpochDay,
        note = note
    )

package com.aqua.aqualight.data.care

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.care.smartcare.SmartCareGeneratedTask
import com.aqua.aqualight.data.care.smartcare.SmartCareTaskType
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.careTasksDataStore: DataStore<CareTasksStore> by dataStore(
    fileName = "care_tasks.pb",
    serializer = CareTasksCommercialSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(LocalDataRecoveryTracker.Area.CARE_TASKS)
        CareTaskStoreRules.defaultStore()
    }
)

/**
 * Pure owner-scoped care-task persistence.
 *
 * This class never schedules alarms, reads notification preferences, renders
 * notifications, or resolves the notification composition root. Application
 * operations reconcile notification state after a successful mutation.
 */
class CareTaskDataStoreManager private constructor(
    private val context: Context
) {
    private val tankDataStoreManager = AquariumTankDataStoreManager(context)

    val tasksFlow: Flow<List<CareTask>> = context.careTasksDataStore.data.map { store ->
        CareTaskStoreRules.validateStore(store)
            .tasksList
            .filter { storedTask -> storedTask.belongsToCurrentUser() }
            .map { storedTask -> storedTask.toCareTaskStrict() }
    }

    val pendingTasksFlow: Flow<List<CareTask>> = tasksFlow.map { tasks ->
        tasks.filter { task -> task.status == CareTaskStatus.PENDING }
            .sortedBy { task -> task.dueAtMillis }
    }

    val historyTasksFlow: Flow<List<CareTask>> = tasksFlow.map { tasks ->
        tasks.filter { task -> task.status == CareTaskStatus.COMPLETED }
            .sortedByDescending { task -> task.completedAtMillis ?: 0L }
    }

    fun tasksForTankFlow(tankId: Long): Flow<List<CareTask>> {
        CareTaskStoreRules.requireValidTankId(tankId)
        return tasksFlow.map { tasks ->
            tasks.filter { task -> task.tankId == tankId }
                .sortedBy { task -> task.dueAtMillis }
        }
    }

    fun taskFlow(taskId: Long): Flow<CareTask?> {
        requirePositiveTaskId(taskId)
        return tasksFlow.map { tasks -> tasks.firstOrNull { task -> task.id == taskId } }
    }

    suspend fun addTask(task: CareTask) {
        val ownerUid = resolveActiveOwner(task.ownerUid)
        requireTankExistsForOwner(ownerUid, task.tankId)
        val scopedTask = task.copy(ownerUid = ownerUid)
        CareTaskStoreRules.validateTask(scopedTask, ownerUid)

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            requireUniqueTaskId(currentStore.tasksList, ownerUid, scopedTask.id)
            currentStore.appendValidated(scopedTask)
        }
    }

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
    ): Long {
        val ownerUid = UserDataScope.requireCurrentUid()
        requireTankExistsForOwner(ownerUid, tankId)
        var createdTaskId = 0L

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val now = System.currentTimeMillis()
            val task = CareTask(
                id = CareTaskStoreRules.nextUniqueId(currentStore.tasksList, now),
                ownerUid = ownerUid,
                tankId = tankId,
                title = title.trim(),
                description = description.trim(),
                type = type,
                source = CareTaskSource.MANUAL,
                status = CareTaskStatus.PENDING,
                dueAtMillis = dueAtMillis,
                completedAtMillis = null,
                repeatEnabled = repeatEnabled,
                repeatIntervalDays = if (repeatEnabled) {
                    repeatIntervalDays
                } else {
                    CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS
                },
                reminderEnabled = reminderEnabled,
                missedReminderEnabled = reminderEnabled && missedReminderEnabled,
                missedReminderDays = if (reminderEnabled && missedReminderEnabled) {
                    missedReminderDays
                } else {
                    CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS
                },
                waterChangePercent = waterChangePercent.takeIf {
                    type == CareTaskType.WATER_CHANGE
                },
                note = note.trim(),
                generatedRuleKey = "",
                createdAtMillis = now,
                updatedAtMillis = now
            )
            CareTaskStoreRules.validateTask(task, ownerUid)
            createdTaskId = task.id
            currentStore.appendValidated(task)
        }

        check(createdTaskId > 0L) {
            "Care task creation completed without a generated task id."
        }
        return createdTaskId
    }

    suspend fun addCompletedActivity(
        tankId: Long,
        title: String,
        description: String,
        type: CareTaskType,
        completedAtMillis: Long,
        waterChangePercent: Int?,
        note: String
    ) {
        val ownerUid = UserDataScope.requireCurrentUid()
        requireTankExistsForOwner(ownerUid, tankId)

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val now = System.currentTimeMillis()
            val task = CareTask(
                id = CareTaskStoreRules.nextUniqueId(currentStore.tasksList, now),
                ownerUid = ownerUid,
                tankId = tankId,
                title = title.trim(),
                description = description.trim(),
                type = type,
                source = CareTaskSource.MANUAL,
                status = CareTaskStatus.COMPLETED,
                dueAtMillis = completedAtMillis,
                completedAtMillis = completedAtMillis,
                repeatEnabled = false,
                repeatIntervalDays = CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS,
                reminderEnabled = false,
                missedReminderEnabled = false,
                missedReminderDays = CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS,
                waterChangePercent = waterChangePercent.takeIf {
                    type == CareTaskType.WATER_CHANGE
                },
                note = note.trim(),
                generatedRuleKey = "",
                createdAtMillis = now,
                updatedAtMillis = now
            )
            CareTaskStoreRules.validateTask(task, ownerUid)
            currentStore.appendValidated(task)
        }
    }

    suspend fun addOrUpdateAutomaticTask(task: CareTask) {
        require(task.source == CareTaskSource.AUTOMATIC) {
            "addOrUpdateAutomaticTask only accepts automatic tasks."
        }
        val ownerUid = resolveActiveOwner(task.ownerUid)
        requireTankExistsForOwner(ownerUid, task.tankId)
        if (!isSmartCareEnabledForTank(ownerUid, task.tankId)) return

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val currentTasks = currentStore.tasksList
            val existing = currentTasks.firstOrNull { stored ->
                stored.belongsToOwner(ownerUid) &&
                    stored.tankId == task.tankId &&
                    stored.source == CareTaskSource.AUTOMATIC.name &&
                    stored.status == CareTaskStatus.PENDING.name &&
                    stored.generatedRuleKey == task.generatedRuleKey.trim() &&
                    task.generatedRuleKey.trim().isNotBlank()
            }
            val now = System.currentTimeMillis()
            val persisted = if (existing == null) {
                task.copy(
                    id = CareTaskStoreRules.nextUniqueId(currentTasks, now),
                    ownerUid = ownerUid,
                    title = task.title.trim(),
                    description = task.description.trim(),
                    source = CareTaskSource.AUTOMATIC,
                    status = CareTaskStatus.PENDING,
                    completedAtMillis = null,
                    repeatEnabled = false,
                    repeatIntervalDays = CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS,
                    missedReminderEnabled = false,
                    missedReminderDays = CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS,
                    waterChangePercent = task.waterChangePercent.takeIf {
                        task.type == CareTaskType.WATER_CHANGE
                    },
                    note = task.note.trim(),
                    generatedRuleKey = task.generatedRuleKey.trim(),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            } else {
                val current = existing.toCareTaskStrict()
                task.copy(
                    id = current.id,
                    ownerUid = ownerUid,
                    title = task.title.trim(),
                    description = task.description.trim(),
                    source = CareTaskSource.AUTOMATIC,
                    status = CareTaskStatus.PENDING,
                    dueAtMillis = current.dueAtMillis,
                    completedAtMillis = null,
                    repeatEnabled = false,
                    repeatIntervalDays = CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS,
                    missedReminderEnabled = false,
                    missedReminderDays = CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS,
                    waterChangePercent = task.waterChangePercent.takeIf {
                        task.type == CareTaskType.WATER_CHANGE
                    },
                    note = task.note.trim(),
                    generatedRuleKey = task.generatedRuleKey.trim(),
                    createdAtMillis = current.createdAtMillis,
                    updatedAtMillis = now
                )
            }
            CareTaskStoreRules.validateTask(persisted, ownerUid)
            if (existing == null) {
                currentStore.appendValidated(persisted)
            } else {
                currentStore.replaceValidatedTask(ownerUid, persisted)
            }
        }
    }

    suspend fun syncAutomaticTasks(generatedTasks: List<SmartCareGeneratedTask>) {
        val ownerUid = UserDataScope.requireCurrentUid()
        val allowed = filterGeneratedTasksBySmartCareSettings(ownerUid, generatedTasks)
        if (allowed.isEmpty()) return

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val now = System.currentTimeMillis()
            val updatedTasks = currentStore.tasksList.toMutableList()

            allowed.forEach { generated ->
                val generatedKey = generated.id.trim()
                if (generatedKey.isBlank()) {
                    throw StoreInvariantViolation(
                        "Automatic care-task generatedRuleKey must not be blank."
                    )
                }

                val exactIndex = updatedTasks.indexOfFirst { stored ->
                    stored.belongsToOwner(ownerUid) &&
                        stored.tankId == generated.tankId &&
                        stored.source == CareTaskSource.AUTOMATIC.name &&
                        stored.generatedRuleKey == generatedKey
                }
                if (exactIndex >= 0) {
                    val existing = updatedTasks[exactIndex].toCareTaskStrict()
                    if (existing.status == CareTaskStatus.COMPLETED) return@forEach
                    val updated = existing.copy(
                        title = generated.titleTr.trim(),
                        description = generated.messageTr.trim(),
                        type = generated.taskType.toCareTaskType(),
                        reminderEnabled = true,
                        waterChangePercent = generated.waterChangePercentForType(),
                        note = "",
                        generatedRuleKey = generatedKey,
                        updatedAtMillis = now
                    )
                    CareTaskStoreRules.validateTask(updated, ownerUid)
                    updatedTasks[exactIndex] = updated.toStoredCareTaskStrict()
                    return@forEach
                }

                val rulePrefix = getAutomaticRulePrefix(
                    generated.tankId,
                    generated.ruleId.trim()
                )
                val sameRuleIndex = updatedTasks.indexOfFirst { stored ->
                    stored.belongsToOwner(ownerUid) &&
                        stored.tankId == generated.tankId &&
                        stored.source == CareTaskSource.AUTOMATIC.name &&
                        stored.status == CareTaskStatus.PENDING.name &&
                        stored.generatedRuleKey.startsWith(rulePrefix)
                }
                if (sameRuleIndex >= 0) return@forEach

                val newTask = generated.toAutomaticCareTask(
                    ownerUid = ownerUid,
                    taskId = CareTaskStoreRules.nextUniqueId(updatedTasks, now),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                CareTaskStoreRules.validateTask(newTask, ownerUid)
                updatedTasks += newTask.toStoredCareTaskStrict()
            }
            currentStore.replaceAllValidated(updatedTasks)
        }
    }

    suspend fun updateTask(task: CareTask) {
        val ownerUid = resolveActiveOwner(task.ownerUid)
        requireTankExistsForOwner(ownerUid, task.tankId)

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val currentTask = currentStore.tasksList.firstOrNull { stored ->
                stored.id == task.id && stored.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: throw IllegalArgumentException(
                "Care task not found for the active owner."
            )
            val updated = task.copy(
                ownerUid = ownerUid,
                title = task.title.trim(),
                description = task.description.trim(),
                waterChangePercent = task.waterChangePercent.takeIf {
                    task.type == CareTaskType.WATER_CHANGE
                },
                note = task.note.trim(),
                generatedRuleKey = task.generatedRuleKey.trim(),
                createdAtMillis = currentTask.createdAtMillis,
                updatedAtMillis = System.currentTimeMillis()
            )
            CareTaskStoreRules.validateTask(updated, ownerUid)
            currentStore.replaceValidatedTask(ownerUid, updated)
        }
    }

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
    ) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()
        requireTankExistsForOwner(ownerUid, tankId)

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val current = currentStore.tasksList.firstOrNull { stored ->
                stored.id == taskId && stored.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: throw IllegalArgumentException(
                "Care task not found for the active owner."
            )
            require(current.source == CareTaskSource.MANUAL) {
                "Only manual tasks can be edited through updateManualTask."
            }
            val updated = current.copy(
                tankId = tankId,
                title = title.trim(),
                description = description.trim(),
                type = type,
                dueAtMillis = dueAtMillis,
                repeatEnabled = repeatEnabled,
                repeatIntervalDays = if (repeatEnabled) {
                    repeatIntervalDays
                } else {
                    CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS
                },
                reminderEnabled = reminderEnabled,
                missedReminderEnabled = reminderEnabled && missedReminderEnabled,
                missedReminderDays = if (reminderEnabled && missedReminderEnabled) {
                    missedReminderDays
                } else {
                    CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS
                },
                waterChangePercent = waterChangePercent.takeIf {
                    type == CareTaskType.WATER_CHANGE
                },
                note = note.trim(),
                updatedAtMillis = System.currentTimeMillis()
            )
            CareTaskStoreRules.validateTask(updated, ownerUid)
            currentStore.replaceValidatedTask(ownerUid, updated)
        }
    }

    suspend fun completeTask(taskId: Long) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val now = System.currentTimeMillis()
            val target = currentStore.tasksList.firstOrNull { stored ->
                stored.id == taskId && stored.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: throw IllegalArgumentException(
                "Care task not found for the active owner."
            )
            val completed = target.copy(
                status = CareTaskStatus.COMPLETED,
                completedAtMillis = now,
                updatedAtMillis = now
            )
            CareTaskStoreRules.validateTask(completed, ownerUid)
            val updatedTasks = currentStore.tasksList.map { stored ->
                if (stored.id == taskId && stored.belongsToOwner(ownerUid)) {
                    completed.toStoredCareTaskStrict()
                } else {
                    stored
                }
            }.toMutableList()

            if (target.repeatEnabled) {
                val next = target.copy(
                    id = CareTaskStoreRules.nextUniqueId(updatedTasks, now),
                    status = CareTaskStatus.PENDING,
                    dueAtMillis = now + TimeUnit.DAYS.toMillis(
                        target.repeatIntervalDays.toLong()
                    ),
                    completedAtMillis = null,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                CareTaskStoreRules.validateTask(next, ownerUid)
                updatedTasks += next.toStoredCareTaskStrict()
            }

            currentStore.replaceAllValidated(updatedTasks)
        }
    }

    suspend fun updateCompletedTaskDate(taskId: Long, completedAtMillis: Long) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()
        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val current = currentStore.tasksList.firstOrNull { stored ->
                stored.id == taskId && stored.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: throw IllegalArgumentException(
                "Care task not found for the active owner."
            )
            require(current.status == CareTaskStatus.COMPLETED) {
                "Only completed tasks may change their completion date."
            }
            val updated = current.copy(
                dueAtMillis = completedAtMillis,
                completedAtMillis = completedAtMillis,
                updatedAtMillis = System.currentTimeMillis()
            )
            CareTaskStoreRules.validateTask(updated, ownerUid)
            currentStore.replaceValidatedTask(ownerUid, updated)
        }
    }

    suspend fun deleteManualTask(taskId: Long) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()
        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val target = currentStore.tasksList.firstOrNull { stored ->
                stored.id == taskId && stored.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: return@updateData currentStore
            require(target.source == CareTaskSource.MANUAL) {
                "Only manual tasks can be removed through deleteManualTask."
            }
            currentStore.removeValidatedTask(ownerUid, taskId)
        }
    }

    suspend fun deleteTask(taskId: Long) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()
        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            if (currentStore.tasksList.any { stored ->
                    stored.id == taskId && stored.belongsToOwner(ownerUid)
                }
            ) {
                currentStore.removeValidatedTask(ownerUid, taskId)
            } else {
                currentStore
            }
        }
    }

    suspend fun deleteTasksForTank(tankId: Long) {
        CareTaskStoreRules.requireValidTankId(tankId)
        val ownerUid = UserDataScope.requireCurrentUid()
        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            currentStore.replaceAllValidated(
                currentStore.tasksList.filterNot { stored ->
                    stored.tankId == tankId && stored.belongsToOwner(ownerUid)
                }
            )
        }
    }

    suspend fun repairOrphanedTankTasks(ownerUid: String): Int {
        val targetOwnerUid = requireOwnerUid(ownerUid)
        val validTankIds = tankDataStoreManager.tanksSnapshotForOwner(targetOwnerUid)
            .mapTo(mutableSetOf()) { tank -> tank.id }
        var removedCount = 0

        context.careTasksDataStore.updateData { currentStore ->
            val retained = currentStore.tasksList.filterNot { stored ->
                val remove = stored.belongsToOwner(targetOwnerUid) &&
                    stored.tankId !in validTankIds
                if (remove) removedCount += 1
                remove
            }
            if (removedCount == 0) {
                currentStore
            } else {
                currentStore.replaceAllValidated(retained)
            }
        }
        return removedCount
    }

    suspend fun clearAllTasks(ownerUid: String? = null) {
        val targetOwnerUid = ownerUid?.let(::requireOwnerUid)
            ?: UserDataScope.requireCurrentUid()
        context.careTasksDataStore.updateData { currentStore ->
            currentStore.replaceAllValidated(
                currentStore.tasksList.filterNot { stored ->
                    stored.belongsToOwner(targetOwnerUid)
                }
            )
        }
    }

    private suspend fun isSmartCareEnabledForTank(ownerUid: String, tankId: Long): Boolean {
        CareTaskStoreRules.requireValidTankId(tankId)
        return tankDataStoreManager.tanksSnapshotForOwner(ownerUid)
            .firstOrNull { tank -> tank.id == tankId }
            ?.smartCareEnabled == true
    }

    private suspend fun filterGeneratedTasksBySmartCareSettings(
        ownerUid: String,
        generatedTasks: List<SmartCareGeneratedTask>
    ): List<SmartCareGeneratedTask> {
        if (generatedTasks.isEmpty()) return emptyList()
        val tanksById = tankDataStoreManager.tanksSnapshotForOwner(ownerUid)
            .associateBy { tank -> tank.id }
        return generatedTasks.filter { generated ->
            val generatedOwner = UserDataScope.normalizeOwnerUid(generated.ownerUid)
            if (generatedOwner.isNotBlank() && generatedOwner != ownerUid) {
                throw StoreInvariantViolation(
                    "Generated care task owner does not match the active owner."
                )
            }
            tanksById[generated.tankId]?.smartCareEnabled == true
        }
    }

    private suspend fun requireTankExistsForOwner(ownerUid: String, tankId: Long) {
        CareTaskStoreRules.requireValidTankId(tankId)
        if (tankDataStoreManager.tanksSnapshotForOwner(ownerUid).none { tank ->
                tank.id == tankId
            }
        ) {
            throw StoreInvariantViolation(
                "Care task references a tank that does not exist for the active owner."
            )
        }
    }

    private fun resolveActiveOwner(requestedOwnerUid: String): String {
        val activeOwnerUid = UserDataScope.requireCurrentUid()
        val requested = UserDataScope.normalizeOwnerUid(requestedOwnerUid)
        if (requested.isNotBlank() && requested != activeOwnerUid) {
            throw StoreInvariantViolation(
                "Care task owner does not match the active owner."
            )
        }
        return activeOwnerUid
    }

    private fun requireOwnerScope(expectedOwnerUid: String) {
        if (UserDataScope.requireCurrentUid() != expectedOwnerUid) {
            throw StoreInvariantViolation(
                "The active owner changed while a care-task write was in progress."
            )
        }
    }

    private fun requireOwnerUid(value: String): String {
        val ownerUid = UserDataScope.normalizeOwnerUid(value)
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        return ownerUid
    }

    private fun requirePositiveTaskId(taskId: Long) {
        require(taskId > 0L) { "taskId must be positive" }
    }

    private fun requireUniqueTaskId(
        currentTasks: List<StoredCareTask>,
        ownerUid: String,
        taskId: Long
    ) {
        if (currentTasks.any { stored ->
                stored.id == taskId && stored.belongsToOwner(ownerUid)
            }
        ) {
            throw StoreInvariantViolation(
                "Duplicate care-task id $taskId for the active owner."
            )
        }
    }

    private fun CareTasksStore.appendValidated(task: CareTask): CareTasksStore {
        return CareTaskStoreRules.validateStore(
            toBuilder().addTasks(task.toStoredCareTaskStrict()).build()
        )
    }

    private fun CareTasksStore.replaceValidatedTask(
        ownerUid: String,
        task: CareTask
    ): CareTasksStore {
        var replaced = false
        val updatedTasks = tasksList.map { stored ->
            if (stored.id == task.id && stored.belongsToOwner(ownerUid)) {
                replaced = true
                task.toStoredCareTaskStrict()
            } else {
                stored
            }
        }
        if (!replaced) {
            throw IllegalArgumentException(
                "Care task not found for the active owner."
            )
        }
        return replaceAllValidated(updatedTasks)
    }

    private fun CareTasksStore.removeValidatedTask(
        ownerUid: String,
        taskId: Long
    ): CareTasksStore {
        return replaceAllValidated(
            tasksList.filterNot { stored ->
                stored.id == taskId && stored.belongsToOwner(ownerUid)
            }
        )
    }

    private fun CareTasksStore.replaceAllValidated(
        tasks: Iterable<StoredCareTask>
    ): CareTasksStore {
        return CareTaskStoreRules.validateStore(
            toBuilder().clearTasks().addAllTasks(tasks).build()
        )
    }

    private fun StoredCareTask.toCareTaskStrict(): CareTask {
        CareTaskStoreRules.validateStoredTask(this)
        return CareTask(
            id = id,
            ownerUid = ownerUid,
            tankId = tankId,
            title = title,
            description = description,
            type = CareTaskType.valueOf(type),
            source = CareTaskSource.valueOf(source),
            status = CareTaskStatus.valueOf(status),
            dueAtMillis = dueAtMillis,
            completedAtMillis = completedAtMillis.takeIf { value -> value > 0L },
            repeatEnabled = repeatEnabled,
            repeatIntervalDays = repeatIntervalDays,
            reminderEnabled = reminderEnabled,
            missedReminderEnabled = missedReminderEnabled,
            missedReminderDays = missedReminderDays,
            waterChangePercent = waterChangePercent.takeIf { value -> value > 0 },
            note = note,
            generatedRuleKey = generatedRuleKey,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        ).also(CareTaskStoreRules::validateTask)
    }

    private fun CareTask.toStoredCareTaskStrict(): StoredCareTask {
        CareTaskStoreRules.validateTask(this)
        return StoredCareTask.newBuilder()
            .setId(id)
            .setOwnerUid(ownerUid)
            .setTankId(tankId)
            .setTitle(title)
            .setDescription(description)
            .setType(type.name)
            .setSource(source.name)
            .setStatus(status.name)
            .setDueAtMillis(dueAtMillis)
            .setCompletedAtMillis(completedAtMillis ?: 0L)
            .setRepeatEnabled(repeatEnabled)
            .setRepeatIntervalDays(repeatIntervalDays)
            .setReminderEnabled(reminderEnabled)
            .setMissedReminderEnabled(missedReminderEnabled)
            .setMissedReminderDays(missedReminderDays)
            .setWaterChangePercent(waterChangePercent ?: 0)
            .setNote(note)
            .setGeneratedRuleKey(generatedRuleKey)
            .setCreatedAtMillis(createdAtMillis)
            .setUpdatedAtMillis(updatedAtMillis)
            .build()
            .also(CareTaskStoreRules::validateStoredTask)
    }

    private fun StoredCareTask.belongsToCurrentUser(): Boolean {
        return UserDataScope.belongsToCurrentUser(ownerUid)
    }

    private fun StoredCareTask.belongsToOwner(ownerUid: String): Boolean {
        return UserDataScope.belongsToOwner(this.ownerUid, ownerUid)
    }

    private fun SmartCareGeneratedTask.toAutomaticCareTask(
        ownerUid: String,
        taskId: Long,
        createdAtMillis: Long,
        updatedAtMillis: Long
    ): CareTask {
        return CareTask(
            id = taskId,
            ownerUid = ownerUid,
            tankId = tankId,
            title = titleTr.trim(),
            description = messageTr.trim(),
            type = taskType.toCareTaskType(),
            source = CareTaskSource.AUTOMATIC,
            status = CareTaskStatus.PENDING,
            dueAtMillis = dueAtMillis,
            completedAtMillis = null,
            repeatEnabled = false,
            repeatIntervalDays = CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS,
            reminderEnabled = true,
            missedReminderEnabled = false,
            missedReminderDays = CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS,
            waterChangePercent = waterChangePercentForType(),
            note = "",
            generatedRuleKey = id.trim(),
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun SmartCareGeneratedTask.waterChangePercentForType(): Int? {
        return waterChangePercent.takeIf {
            taskType == SmartCareTaskType.WATER_CHANGE
        }
    }

    private fun SmartCareTaskType.toCareTaskType(): CareTaskType {
        return when (this) {
            SmartCareTaskType.WATER_CHANGE -> CareTaskType.WATER_CHANGE
            SmartCareTaskType.WATER_TEST -> CareTaskType.WATER_TEST
            SmartCareTaskType.LIGHTING -> CareTaskType.LIGHT_CHECK
            SmartCareTaskType.CO2_CHECK -> CareTaskType.CO2_CHECK
            SmartCareTaskType.FERTILIZER -> CareTaskType.FERTILIZER_DOSING
            SmartCareTaskType.PLANT_CHECK -> CareTaskType.PLANT_HEALTH_CHECK
            SmartCareTaskType.PLANT_TRIM -> CareTaskType.PLANT_TRIM
            SmartCareTaskType.FILTER_CHECK -> CareTaskType.FILTER_MAINTENANCE
            SmartCareTaskType.GLASS_CLEANING -> CareTaskType.GLASS_CLEANING
            SmartCareTaskType.LIVESTOCK_CHECK -> CareTaskType.LIVESTOCK_CHECK
            SmartCareTaskType.FEEDING -> CareTaskType.FEEDING
            SmartCareTaskType.GENERAL_CHECK -> CareTaskType.CUSTOM
        }
    }

    private fun getAutomaticRulePrefix(tankId: Long, ruleId: String): String {
        CareTaskStoreRules.requireValidTankId(tankId)
        require(ruleId.isNotBlank()) {
            "Automatic care rule id must not be blank."
        }
        return "smart_${tankId}_${ruleId}_"
    }

    companion object {
        fun create(context: Context): CareTaskDataStoreManager {
            return CareTaskDataStoreManager(context.applicationContext)
        }
    }
}

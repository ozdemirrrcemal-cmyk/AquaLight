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
import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler
import com.aqua.aqualight.data.care.smartcare.SmartCareGeneratedTask
import com.aqua.aqualight.data.care.smartcare.SmartCareTaskType
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.careTasksDataStore: DataStore<CareTasksStore> by dataStore(
    fileName = "care_tasks.pb",
    serializer = CareTasksCommercialSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.CARE_TASKS
        )
        CareTaskStoreRules.defaultStore()
    }
)

class CareTaskDataStoreManager private constructor(
    private val context: Context
) {

    private val tankDataStoreManager = AquariumTankDataStoreManager(
        context
    )

    private val userPreferencesManager = UserPreferencesManager.create(
        context
    )

    val tasksFlow: Flow<List<CareTask>> =
        context.careTasksDataStore.data.map { store ->
            CareTaskStoreRules.validateStore(store)
                .tasksList
                .filter { storedTask ->
                    storedTask.belongsToCurrentUser()
                }
                .map { storedTask ->
                    storedTask.toCareTaskStrict()
                }
        }

    val pendingTasksFlow: Flow<List<CareTask>> =
        tasksFlow.map { tasks ->
            tasks
                .filter { task ->
                    task.status == CareTaskStatus.PENDING
                }
                .sortedBy { task ->
                    task.dueAtMillis
                }
        }

    val historyTasksFlow: Flow<List<CareTask>> =
        tasksFlow.map { tasks ->
            tasks
                .filter { task ->
                    task.status == CareTaskStatus.COMPLETED
                }
                .sortedByDescending { task ->
                    task.completedAtMillis ?: 0L
                }
        }

    fun tasksForTankFlow(
        tankId: Long
    ): Flow<List<CareTask>> {
        CareTaskStoreRules.requireValidTankId(tankId)

        return tasksFlow.map { tasks ->
            tasks
                .filter { task ->
                    task.tankId == tankId
                }
                .sortedBy { task ->
                    task.dueAtMillis
                }
        }
    }

    fun taskFlow(
        taskId: Long
    ): Flow<CareTask?> {
        requirePositiveTaskId(taskId)

        return tasksFlow.map { tasks ->
            tasks.firstOrNull { task ->
                task.id == taskId
            }
        }
    }

    /**
     * Strict low-level insertion API. The caller supplies the ID, therefore the
     * method rejects blank/mismatched owners, invalid tank references and any
     * owner-scoped duplicate instead of silently replacing data.
     */
    suspend fun addTask(
        task: CareTask
    ) {
        val ownerUid = resolveActiveOwner(task.ownerUid)
        requireTankExistsForOwner(ownerUid, task.tankId)

        val scopedTask = task.copy(
            ownerUid = ownerUid
        )
        CareTaskStoreRules.validateTask(
            task = scopedTask,
            expectedOwnerUid = ownerUid
        )

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            requireUniqueTaskId(
                currentTasks = currentStore.tasksList,
                ownerUid = ownerUid,
                taskId = scopedTask.id
            )
            currentStore.appendValidated(scopedTask)
        }

        scheduleTaskReminderIfAllowed(scopedTask)
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
    ) {
        val ownerUid = UserDataScope.requireCurrentUid()
        requireTankExistsForOwner(ownerUid, tankId)

        var taskToSchedule: CareTask? = null

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val now = System.currentTimeMillis()
            val task = CareTask(
                id = CareTaskStoreRules.nextUniqueId(
                    currentTasks = currentStore.tasksList,
                    nowMillis = now
                ),
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
                waterChangePercent = if (type == CareTaskType.WATER_CHANGE) {
                    waterChangePercent
                } else {
                    null
                },
                note = note.trim(),
                generatedRuleKey = "",
                createdAtMillis = now,
                updatedAtMillis = now
            )

            CareTaskStoreRules.validateTask(
                task = task,
                expectedOwnerUid = ownerUid
            )
            taskToSchedule = task
            currentStore.appendValidated(task)
        }

        taskToSchedule?.let { task ->
            scheduleTaskReminderIfAllowed(task)
        }
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
                id = CareTaskStoreRules.nextUniqueId(
                    currentTasks = currentStore.tasksList,
                    nowMillis = now
                ),
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
                waterChangePercent = if (type == CareTaskType.WATER_CHANGE) {
                    waterChangePercent
                } else {
                    null
                },
                note = note.trim(),
                generatedRuleKey = "",
                createdAtMillis = now,
                updatedAtMillis = now
            )

            CareTaskStoreRules.validateTask(
                task = task,
                expectedOwnerUid = ownerUid
            )
            currentStore.appendValidated(task)
        }
    }

    suspend fun addOrUpdateAutomaticTask(
        task: CareTask
    ) {
        require(task.source == CareTaskSource.AUTOMATIC) {
            "addOrUpdateAutomaticTask only accepts automatic tasks."
        }

        val ownerUid = resolveActiveOwner(task.ownerUid)
        requireTankExistsForOwner(ownerUid, task.tankId)

        if (!isSmartCareEnabledForTank(ownerUid, task.tankId)) {
            return
        }

        var taskToSchedule: CareTask? = null

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val currentTasks = currentStore.tasksList
            val existingPendingAutoTask = currentTasks.firstOrNull { storedTask ->
                storedTask.belongsToOwner(ownerUid) &&
                    storedTask.tankId == task.tankId &&
                    storedTask.source == CareTaskSource.AUTOMATIC.name &&
                    storedTask.status == CareTaskStatus.PENDING.name &&
                    storedTask.generatedRuleKey == task.generatedRuleKey.trim() &&
                    task.generatedRuleKey.trim().isNotBlank()
            }
            val now = System.currentTimeMillis()

            val persistedTask = if (existingPendingAutoTask == null) {
                task.copy(
                    id = CareTaskStoreRules.nextUniqueId(
                        currentTasks = currentTasks,
                        nowMillis = now
                    ),
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
                    waterChangePercent = if (task.type == CareTaskType.WATER_CHANGE) {
                        task.waterChangePercent
                    } else {
                        null
                    },
                    note = task.note.trim(),
                    generatedRuleKey = task.generatedRuleKey.trim(),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            } else {
                val existingTask = existingPendingAutoTask.toCareTaskStrict()
                task.copy(
                    id = existingTask.id,
                    ownerUid = ownerUid,
                    title = task.title.trim(),
                    description = task.description.trim(),
                    source = CareTaskSource.AUTOMATIC,
                    status = CareTaskStatus.PENDING,
                    dueAtMillis = existingTask.dueAtMillis,
                    completedAtMillis = null,
                    repeatEnabled = false,
                    repeatIntervalDays = CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS,
                    missedReminderEnabled = false,
                    missedReminderDays = CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS,
                    waterChangePercent = if (task.type == CareTaskType.WATER_CHANGE) {
                        task.waterChangePercent
                    } else {
                        null
                    },
                    note = task.note.trim(),
                    generatedRuleKey = task.generatedRuleKey.trim(),
                    createdAtMillis = existingTask.createdAtMillis,
                    updatedAtMillis = now
                )
            }

            CareTaskStoreRules.validateTask(
                task = persistedTask,
                expectedOwnerUid = ownerUid
            )
            taskToSchedule = persistedTask

            if (existingPendingAutoTask == null) {
                currentStore.appendValidated(persistedTask)
            } else {
                currentStore.replaceValidatedTask(
                    ownerUid = ownerUid,
                    task = persistedTask
                )
            }
        }

        taskToSchedule?.let { scheduledTask ->
            scheduleTaskReminderIfAllowed(scheduledTask)
        }
    }

    suspend fun syncAutomaticTasks(
        generatedTasks: List<SmartCareGeneratedTask>
    ) {
        val ownerUid = UserDataScope.requireCurrentUid()
        val allowedGeneratedTasks = filterGeneratedTasksBySmartCareSettings(
            ownerUid = ownerUid,
            generatedTasks = generatedTasks
        )

        if (allowedGeneratedTasks.isEmpty()) {
            return
        }

        val tasksToSchedule = mutableListOf<CareTask>()

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            tasksToSchedule.clear()

            val now = System.currentTimeMillis()
            val updatedTasks = currentStore.tasksList.toMutableList()

            allowedGeneratedTasks.forEach { generatedTask ->
                val generatedKey = generatedTask.id.trim()
                if (generatedKey.isBlank()) {
                    throw StoreInvariantViolation(
                        "Automatic care-task generatedRuleKey must not be blank."
                    )
                }

                val existingExactIndex = updatedTasks.indexOfFirst { storedTask ->
                    storedTask.belongsToOwner(ownerUid) &&
                        storedTask.tankId == generatedTask.tankId &&
                        storedTask.source == CareTaskSource.AUTOMATIC.name &&
                        storedTask.generatedRuleKey == generatedKey
                }

                if (existingExactIndex >= 0) {
                    val existingTask = updatedTasks[existingExactIndex].toCareTaskStrict()
                    if (existingTask.status == CareTaskStatus.COMPLETED) {
                        return@forEach
                    }

                    val updatedTask = existingTask.copy(
                        title = generatedTask.titleTr.trim(),
                        description = generatedTask.messageTr.trim(),
                        type = generatedTask.taskType.toCareTaskType(),
                        reminderEnabled = true,
                        waterChangePercent = generatedTask.waterChangePercentForType(),
                        note = "",
                        generatedRuleKey = generatedKey,
                        updatedAtMillis = now
                    )

                    CareTaskStoreRules.validateTask(
                        task = updatedTask,
                        expectedOwnerUid = ownerUid
                    )
                    updatedTasks[existingExactIndex] = updatedTask.toStoredCareTaskStrict()
                    tasksToSchedule += updatedTask
                    return@forEach
                }

                val rulePrefix = getAutomaticRulePrefix(
                    tankId = generatedTask.tankId,
                    ruleId = generatedTask.ruleId.trim()
                )
                val existingSameRuleIndex = updatedTasks.indexOfFirst { storedTask ->
                    storedTask.belongsToOwner(ownerUid) &&
                        storedTask.tankId == generatedTask.tankId &&
                        storedTask.source == CareTaskSource.AUTOMATIC.name &&
                        storedTask.status == CareTaskStatus.PENDING.name &&
                        storedTask.generatedRuleKey.startsWith(rulePrefix)
                }

                if (existingSameRuleIndex >= 0) {
                    tasksToSchedule += updatedTasks[existingSameRuleIndex].toCareTaskStrict()
                    return@forEach
                }

                val taskId = CareTaskStoreRules.nextUniqueId(
                    currentTasks = updatedTasks,
                    nowMillis = now
                )
                val newTask = generatedTask.toAutomaticCareTask(
                    ownerUid = ownerUid,
                    taskId = taskId,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )

                CareTaskStoreRules.validateTask(
                    task = newTask,
                    expectedOwnerUid = ownerUid
                )
                updatedTasks += newTask.toStoredCareTaskStrict()
                tasksToSchedule += newTask
            }

            currentStore.replaceAllValidated(updatedTasks)
        }

        tasksToSchedule
            .distinctBy { task -> task.id }
            .forEach { task ->
                scheduleTaskReminderIfAllowed(task)
            }
    }

    suspend fun updateTask(
        task: CareTask
    ) {
        val ownerUid = resolveActiveOwner(task.ownerUid)
        requireTankExistsForOwner(ownerUid, task.tankId)
        var taskToSchedule: CareTask? = null

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val currentTask = currentStore.tasksList.firstOrNull { storedTask ->
                storedTask.id == task.id && storedTask.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: throw IllegalArgumentException(
                "Care task not found for the active owner."
            )

            val updatedTask = task.copy(
                ownerUid = ownerUid,
                title = task.title.trim(),
                description = task.description.trim(),
                waterChangePercent = if (task.type == CareTaskType.WATER_CHANGE) {
                    task.waterChangePercent
                } else {
                    null
                },
                note = task.note.trim(),
                generatedRuleKey = task.generatedRuleKey.trim(),
                createdAtMillis = currentTask.createdAtMillis,
                updatedAtMillis = System.currentTimeMillis()
            )

            CareTaskStoreRules.validateTask(
                task = updatedTask,
                expectedOwnerUid = ownerUid
            )
            taskToSchedule = updatedTask
            currentStore.replaceValidatedTask(
                ownerUid = ownerUid,
                task = updatedTask
            )
        }

        taskToSchedule?.let { updatedTask ->
            scheduleTaskReminderIfAllowed(updatedTask)
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
        var taskToSchedule: CareTask? = null

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val currentTask = currentStore.tasksList.firstOrNull { storedTask ->
                storedTask.id == taskId && storedTask.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: throw IllegalArgumentException(
                "Care task not found for the active owner."
            )

            require(currentTask.source == CareTaskSource.MANUAL) {
                "Only manual tasks can be edited through updateManualTask."
            }

            val updatedTask = currentTask.copy(
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
                waterChangePercent = if (type == CareTaskType.WATER_CHANGE) {
                    waterChangePercent
                } else {
                    null
                },
                note = note.trim(),
                updatedAtMillis = System.currentTimeMillis()
            )

            CareTaskStoreRules.validateTask(
                task = updatedTask,
                expectedOwnerUid = ownerUid
            )
            taskToSchedule = updatedTask
            currentStore.replaceValidatedTask(
                ownerUid = ownerUid,
                task = updatedTask
            )
        }

        taskToSchedule?.let { scheduledTask ->
            scheduleTaskReminderIfAllowed(scheduledTask)
        }
    }

    suspend fun completeTask(
        taskId: Long
    ) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()
        var completedTaskId: Long? = null
        var nextTaskToSchedule: CareTask? = null

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val now = System.currentTimeMillis()
            val targetTask = currentStore.tasksList.firstOrNull { storedTask ->
                storedTask.id == taskId && storedTask.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: throw IllegalArgumentException(
                "Care task not found for the active owner."
            )

            val completedTask = targetTask.copy(
                status = CareTaskStatus.COMPLETED,
                completedAtMillis = now,
                updatedAtMillis = now
            )
            CareTaskStoreRules.validateTask(
                task = completedTask,
                expectedOwnerUid = ownerUid
            )

            val updatedTasks = currentStore.tasksList.map { storedTask ->
                if (storedTask.id == taskId && storedTask.belongsToOwner(ownerUid)) {
                    completedTask.toStoredCareTaskStrict()
                } else {
                    storedTask
                }
            }.toMutableList()

            if (targetTask.repeatEnabled) {
                val nextDueAtMillis = now + TimeUnit.DAYS.toMillis(
                    targetTask.repeatIntervalDays.toLong()
                )
                val nextTask = targetTask.copy(
                    id = CareTaskStoreRules.nextUniqueId(
                        currentTasks = updatedTasks,
                        nowMillis = now
                    ),
                    status = CareTaskStatus.PENDING,
                    dueAtMillis = nextDueAtMillis,
                    completedAtMillis = null,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                CareTaskStoreRules.validateTask(
                    task = nextTask,
                    expectedOwnerUid = ownerUid
                )
                updatedTasks += nextTask.toStoredCareTaskStrict()
                nextTaskToSchedule = nextTask
            }

            completedTaskId = completedTask.id
            currentStore.replaceAllValidated(updatedTasks)
        }

        completedTaskId?.let { id ->
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = id,
                ownerUid = ownerUid
            )
        }

        nextTaskToSchedule?.let { nextTask ->
            scheduleTaskReminderIfAllowed(nextTask)
        }
    }

    suspend fun updateCompletedTaskDate(
        taskId: Long,
        completedAtMillis: Long
    ) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val currentTask = currentStore.tasksList.firstOrNull { storedTask ->
                storedTask.id == taskId && storedTask.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: throw IllegalArgumentException(
                "Care task not found for the active owner."
            )

            require(currentTask.status == CareTaskStatus.COMPLETED) {
                "Only completed tasks may change their completion date."
            }

            val updatedTask = currentTask.copy(
                dueAtMillis = completedAtMillis,
                completedAtMillis = completedAtMillis,
                updatedAtMillis = System.currentTimeMillis()
            )
            CareTaskStoreRules.validateTask(
                task = updatedTask,
                expectedOwnerUid = ownerUid
            )
            currentStore.replaceValidatedTask(
                ownerUid = ownerUid,
                task = updatedTask
            )
        }
    }

    suspend fun deleteManualTask(
        taskId: Long
    ) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()
        var deletedTaskId: Long? = null

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val targetTask = currentStore.tasksList.firstOrNull { storedTask ->
                storedTask.id == taskId && storedTask.belongsToOwner(ownerUid)
            }?.toCareTaskStrict() ?: return@updateData currentStore

            require(targetTask.source == CareTaskSource.MANUAL) {
                "Only manual tasks can be removed through deleteManualTask."
            }

            deletedTaskId = targetTask.id
            currentStore.removeValidatedTask(
                ownerUid = ownerUid,
                taskId = taskId
            )
        }

        deletedTaskId?.let { id ->
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = id,
                ownerUid = ownerUid
            )
        }
    }

    suspend fun deleteTask(
        taskId: Long
    ) {
        requirePositiveTaskId(taskId)
        val ownerUid = UserDataScope.requireCurrentUid()
        var deleted = false

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            deleted = currentStore.tasksList.any { storedTask ->
                storedTask.id == taskId && storedTask.belongsToOwner(ownerUid)
            }
            if (!deleted) {
                currentStore
            } else {
                currentStore.removeValidatedTask(
                    ownerUid = ownerUid,
                    taskId = taskId
                )
            }
        }

        if (deleted) {
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = taskId,
                ownerUid = ownerUid
            )
        }
    }

    suspend fun deleteTasksForTank(
        tankId: Long
    ) {
        CareTaskStoreRules.requireValidTankId(tankId)
        val ownerUid = UserDataScope.requireCurrentUid()
        val deletedTaskIds = mutableListOf<Long>()

        context.careTasksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            deletedTaskIds.clear()

            currentStore.tasksList.forEach { storedTask ->
                if (storedTask.tankId == tankId && storedTask.belongsToOwner(ownerUid)) {
                    deletedTaskIds += storedTask.id
                }
            }

            if (deletedTaskIds.isEmpty()) {
                currentStore
            } else {
                currentStore.replaceAllValidated(
                    currentStore.tasksList.filterNot { storedTask ->
                        storedTask.tankId == tankId && storedTask.belongsToOwner(ownerUid)
                    }
                )
            }
        }

        deletedTaskIds.forEach { taskId ->
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = taskId,
                ownerUid = ownerUid
            )
        }
    }

    /**
     * Removes owner-scoped tasks whose authoritative tank record no longer exists.
     * This is a crash-recovery boundary for the two independent Proto DataStores.
     */
    suspend fun repairOrphanedTankTasks(
        ownerUid: String
    ): Int {
        val targetOwnerUid = requireOwnerUid(ownerUid)
        val validTankIds = tankDataStoreManager
            .tanksSnapshotForOwner(targetOwnerUid)
            .map { tank -> tank.id }
            .toSet()
        val removedTasks = mutableListOf<CareTask>()

        context.careTasksDataStore.updateData { currentStore ->
            removedTasks.clear()
            currentStore.tasksList.forEach { storedTask ->
                if (
                    storedTask.belongsToOwner(targetOwnerUid) &&
                    storedTask.tankId !in validTankIds
                ) {
                    removedTasks += storedTask.toCareTaskStrict()
                }
            }

            if (removedTasks.isEmpty()) {
                currentStore
            } else {
                currentStore.replaceAllValidated(
                    currentStore.tasksList.filterNot { storedTask ->
                        storedTask.belongsToOwner(targetOwnerUid) &&
                            storedTask.tankId !in validTankIds
                    }
                )
            }
        }

        removedTasks.forEach { task ->
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = task.id,
                ownerUid = targetOwnerUid
            )
        }

        return removedTasks.size
    }

    suspend fun clearAllTasks(
        ownerUid: String? = null,
        cancelReminders: Boolean = true
    ) {
        val targetOwnerUid = ownerUid
            ?.let(::requireOwnerUid)
            ?: UserDataScope.requireCurrentUid()

        val deletedTasks = if (cancelReminders) {
            context.careTasksDataStore.data
                .first()
                .tasksList
                .filter { storedTask ->
                    storedTask.belongsToOwner(targetOwnerUid)
                }
                .map { storedTask ->
                    storedTask.toCareTaskStrict()
                }
        } else {
            emptyList()
        }

        context.careTasksDataStore.updateData { currentStore ->
            currentStore.replaceAllValidated(
                currentStore.tasksList.filterNot { storedTask ->
                    storedTask.belongsToOwner(targetOwnerUid)
                }
            )
        }

        deletedTasks.forEach { task ->
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = task.id,
                ownerUid = targetOwnerUid
            )
        }
    }

    suspend fun cancelPendingRemindersForTank(
        tankId: Long
    ) {
        CareTaskStoreRules.requireValidTankId(tankId)
        val pendingTasks = pendingTasksFlow.first().filter { task ->
            task.tankId == tankId
        }

        pendingTasks.forEach { task ->
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = task.id,
                ownerUid = task.ownerUid
            )
        }
    }

    suspend fun reschedulePendingRemindersForTank(
        tankId: Long
    ) {
        CareTaskStoreRules.requireValidTankId(tankId)
        val pendingTasks = pendingTasksFlow.first().filter { task ->
            task.tankId == tankId
        }

        pendingTasks.forEach { task ->
            scheduleTaskReminderIfAllowed(task)
        }
    }

    private suspend fun scheduleTaskReminderIfAllowed(
        task: CareTask
    ) {
        CareTaskStoreRules.validateTask(task)

        CareTaskReminderScheduler.cancel(
            context = context,
            taskId = task.id,
            ownerUid = task.ownerUid
        )

        if (!shouldScheduleTaskReminder(task)) {
            return
        }

        CareTaskReminderScheduler.schedule(
            context = context,
            task = task
        )
    }

    private suspend fun shouldScheduleTaskReminder(
        task: CareTask
    ): Boolean {
        if (task.status != CareTaskStatus.PENDING || !task.reminderEnabled) {
            return false
        }

        val globalNotificationsEnabled =
            userPreferencesManager.notificationsEnabled.first()
        if (!globalNotificationsEnabled) {
            return false
        }

        val tank = tankDataStoreManager
            .tanksSnapshotForOwner(task.ownerUid)
            .firstOrNull { savedTank ->
                savedTank.id == task.tankId
            } ?: return false

        return tank.careRemindersEnabled
    }

    private suspend fun isSmartCareEnabledForTank(
        ownerUid: String,
        tankId: Long
    ): Boolean {
        CareTaskStoreRules.requireValidTankId(tankId)
        return tankDataStoreManager
            .tanksSnapshotForOwner(ownerUid)
            .firstOrNull { savedTank ->
                savedTank.id == tankId
            }
            ?.smartCareEnabled == true
    }

    private suspend fun filterGeneratedTasksBySmartCareSettings(
        ownerUid: String,
        generatedTasks: List<SmartCareGeneratedTask>
    ): List<SmartCareGeneratedTask> {
        if (generatedTasks.isEmpty()) {
            return emptyList()
        }

        val tanksById = tankDataStoreManager
            .tanksSnapshotForOwner(ownerUid)
            .associateBy { tank -> tank.id }

        return generatedTasks.filter { generatedTask ->
            val generatedOwner = UserDataScope.normalizeOwnerUid(generatedTask.ownerUid)
            if (generatedOwner.isNotBlank() && generatedOwner != ownerUid) {
                throw StoreInvariantViolation(
                    "Generated care task owner does not match the active owner."
                )
            }
            tanksById[generatedTask.tankId]?.smartCareEnabled == true
        }
    }

    private suspend fun requireTankExistsForOwner(
        ownerUid: String,
        tankId: Long
    ) {
        CareTaskStoreRules.requireValidTankId(tankId)
        val exists = tankDataStoreManager
            .tanksSnapshotForOwner(ownerUid)
            .any { tank -> tank.id == tankId }

        if (!exists) {
            throw StoreInvariantViolation(
                "Care task references a tank that does not exist for the active owner."
            )
        }
    }

    private fun resolveActiveOwner(
        requestedOwnerUid: String
    ): String {
        val activeOwnerUid = UserDataScope.requireCurrentUid()
        val requested = UserDataScope.normalizeOwnerUid(requestedOwnerUid)
        if (requested.isNotBlank() && requested != activeOwnerUid) {
            throw StoreInvariantViolation(
                "Care task owner does not match the active owner."
            )
        }
        return activeOwnerUid
    }

    private fun requireOwnerScope(
        expectedOwnerUid: String
    ) {
        if (UserDataScope.requireCurrentUid() != expectedOwnerUid) {
            throw StoreInvariantViolation(
                "The active owner changed while a care-task write was in progress."
            )
        }
    }

    private fun requireOwnerUid(
        value: String
    ): String {
        val ownerUid = UserDataScope.normalizeOwnerUid(value)
        require(ownerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }
        return ownerUid
    }

    private fun requirePositiveTaskId(
        taskId: Long
    ) {
        require(taskId > 0L) {
            "taskId must be positive"
        }
    }

    private fun requireUniqueTaskId(
        currentTasks: List<StoredCareTask>,
        ownerUid: String,
        taskId: Long
    ) {
        if (currentTasks.any { storedTask ->
                storedTask.id == taskId && storedTask.belongsToOwner(ownerUid)
            }
        ) {
            throw StoreInvariantViolation(
                "Duplicate care-task id $taskId for the active owner."
            )
        }
    }

    private fun CareTasksStore.appendValidated(
        task: CareTask
    ): CareTasksStore {
        val storedTask = task.toStoredCareTaskStrict()
        return CareTaskStoreRules.validateStore(
            toBuilder()
                .addTasks(storedTask)
                .build()
        )
    }

    private fun CareTasksStore.replaceValidatedTask(
        ownerUid: String,
        task: CareTask
    ): CareTasksStore {
        var replaced = false
        val updatedTasks = tasksList.map { storedTask ->
            if (storedTask.id == task.id && storedTask.belongsToOwner(ownerUid)) {
                replaced = true
                task.toStoredCareTaskStrict()
            } else {
                storedTask
            }
        }
        if (!replaced) {
            throw IllegalArgumentException("Care task not found for the active owner.")
        }
        return replaceAllValidated(updatedTasks)
    }

    private fun CareTasksStore.removeValidatedTask(
        ownerUid: String,
        taskId: Long
    ): CareTasksStore {
        return replaceAllValidated(
            tasksList.filterNot { storedTask ->
                storedTask.id == taskId && storedTask.belongsToOwner(ownerUid)
            }
        )
    }

    private fun CareTasksStore.replaceAllValidated(
        tasks: Iterable<StoredCareTask>
    ): CareTasksStore {
        return CareTaskStoreRules.validateStore(
            toBuilder()
                .clearTasks()
                .addAllTasks(tasks)
                .build()
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
        return UserDataScope.belongsToCurrentUser(
            recordOwnerUid = ownerUid
        )
    }

    private fun StoredCareTask.belongsToOwner(
        ownerUid: String
    ): Boolean {
        return UserDataScope.belongsToOwner(
            recordOwnerUid = this.ownerUid,
            ownerUid = ownerUid
        )
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
        return if (taskType == SmartCareTaskType.WATER_CHANGE) {
            waterChangePercent
        } else {
            null
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

    private fun getAutomaticRulePrefix(
        tankId: Long,
        ruleId: String
    ): String {
        CareTaskStoreRules.requireValidTankId(tankId)
        require(ruleId.isNotBlank()) {
            "Automatic care rule id must not be blank."
        }
        return "smart_${tankId}_${ruleId}_"
    }

    companion object {
        fun create(
            context: Context
        ): CareTaskDataStoreManager {
            return CareTaskDataStoreManager(
                context.applicationContext
            )
        }
    }
}

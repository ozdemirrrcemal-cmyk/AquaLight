package com.aqua.aqualight.data.care

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.aqua.aqualight.data.care.CareTasksStore
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler
import com.aqua.aqualight.data.care.smartcare.SmartCareGeneratedTask
import com.aqua.aqualight.data.care.smartcare.SmartCareTaskType
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

private object CareTasksSerializer : Serializer<CareTasksStore> {

  override val defaultValue: CareTasksStore = CareTasksStore
    .getDefaultInstance()

  override suspend fun readFrom(
    input: InputStream
  ): CareTasksStore {
    return try {
      CareTasksStore.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
      throw CorruptionException(
        message = "Cannot read care tasks proto.",
        cause = exception
      )
    }
  }

  override suspend fun writeTo(
    t: CareTasksStore,
    output: OutputStream
  ) {
    t.writeTo(output)
  }
}

private val Context.careTasksDataStore: DataStore<CareTasksStore> by dataStore(
  fileName = "care_tasks.pb",
  serializer = CareTasksSerializer
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
      store.tasksList
        .filter { storedTask ->
          storedTask.belongsToCurrentUser()
        }
        .map { storedTask ->
          storedTask.toCareTask()
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
    return tasksFlow.map { tasks ->
      tasks.firstOrNull { task ->
        task.id == taskId
      }
    }
  }

  suspend fun addTask(
    task: CareTask
  ) {
    val ownerUid = task.ownerUid.ifBlank {
      UserDataScope.requireCurrentUid()
    }

    val scopedTask = task.copy(
      ownerUid = ownerUid
    )

    context.careTasksDataStore.updateData { currentStore ->
      currentStore.toBuilder()
        .addTasks(scopedTask.toStoredCareTask())
        .build()
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
    val now = System.currentTimeMillis()

    val task = CareTask(
      id = now,
      ownerUid = UserDataScope.requireCurrentUid(),
      tankId = tankId,
      title = title,
      description = description,
      type = type,
      source = CareTaskSource.MANUAL,
      status = CareTaskStatus.PENDING,
      dueAtMillis = dueAtMillis,
      completedAtMillis = null,
      repeatEnabled = repeatEnabled,
      repeatIntervalDays = repeatIntervalDays.coerceAtLeast(1),
      reminderEnabled = reminderEnabled,
      missedReminderEnabled = missedReminderEnabled,
      missedReminderDays = missedReminderDays.coerceAtLeast(1),
      waterChangePercent = waterChangePercent,
      note = note,
      generatedRuleKey = "",
      createdAtMillis = now,
      updatedAtMillis = now
    )

    addTask(task)
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
    context.careTasksDataStore.updateData { currentStore ->
      val now = System.currentTimeMillis()

      val safeCompletedAtMillis = if (completedAtMillis > 0L) {
        completedAtMillis
      } else {
        now
      }

      val ownerUid = UserDataScope.requireCurrentUid()

      val task = CareTask(
        id = createNextTaskId(
          currentTasks = currentStore.tasksList
        ),
        ownerUid = ownerUid,
        tankId = tankId,
        title = title,
        description = description,
        type = type,
        source = CareTaskSource.MANUAL,
        status = CareTaskStatus.COMPLETED,
        dueAtMillis = safeCompletedAtMillis,
        completedAtMillis = safeCompletedAtMillis,
        repeatEnabled = false,
        repeatIntervalDays = 1,
        reminderEnabled = false,
        missedReminderEnabled = false,
        missedReminderDays = 1,
        waterChangePercent = if (type == CareTaskType.WATER_CHANGE) {
          waterChangePercent
        } else {
          null
        },
        note = note,
        generatedRuleKey = "",
        createdAtMillis = now,
        updatedAtMillis = now
      )

      currentStore.toBuilder()
        .addTasks(task.toStoredCareTask())
        .build()
    }
  }

  suspend fun addOrUpdateAutomaticTask(
    task: CareTask
  ) {
    val ownerUid = task.ownerUid.ifBlank {
      UserDataScope.requireCurrentUid()
    }

    val scopedTask = task.copy(
      ownerUid = ownerUid
    )

    if (
      scopedTask.source == CareTaskSource.AUTOMATIC &&
      !isSmartCareEnabledForTank(scopedTask.tankId)
    ) {
      return
    }

    var taskToSchedule: CareTask? = null

    context.careTasksDataStore.updateData { currentStore ->
      val currentTasks = currentStore.tasksList

      val existingPendingAutoTask = currentTasks.firstOrNull { storedTask ->
        storedTask.belongsToOwner(ownerUid) &&
          storedTask.tankId == scopedTask.tankId &&
          storedTask.source == CareTaskSource.AUTOMATIC.name &&
          storedTask.status == CareTaskStatus.PENDING.name &&
          storedTask.generatedRuleKey == scopedTask.generatedRuleKey &&
          scopedTask.generatedRuleKey.isNotBlank()
      }

      if (existingPendingAutoTask == null) {
        taskToSchedule = scopedTask

        currentStore.toBuilder()
          .addTasks(scopedTask.toStoredCareTask())
          .build()
      } else {
        val existingTask = existingPendingAutoTask.toCareTask()

        val updatedTask = scopedTask.copy(
          id = existingTask.id,
          dueAtMillis = existingTask.dueAtMillis,
          completedAtMillis = existingTask.completedAtMillis,
          createdAtMillis = existingTask.createdAtMillis,
          updatedAtMillis = System.currentTimeMillis()
        )

        taskToSchedule = updatedTask

        val updatedTasks = currentTasks.map { storedTask ->
          if (storedTask.id == existingTask.id && storedTask.belongsToOwner(ownerUid)) {
            updatedTask.toStoredCareTask()
          } else {
            storedTask
          }
        }

        currentStore.toBuilder()
          .clearTasks()
          .addAllTasks(updatedTasks)
          .build()
      }
    }

    taskToSchedule?.let { scheduledTask ->
      scheduleTaskReminderIfAllowed(scheduledTask)
    }
  }

  suspend fun syncAutomaticTasks(
    generatedTasks: List<SmartCareGeneratedTask>
  ) {
    val allowedGeneratedTasks = filterGeneratedTasksBySmartCareSettings(
      generatedTasks = generatedTasks
    )

    if (allowedGeneratedTasks.isEmpty()) {
      return
    }

    val tasksToSchedule = mutableListOf<CareTask>()

    val ownerUid = UserDataScope.requireCurrentUid()

    context.careTasksDataStore.updateData { currentStore ->
      tasksToSchedule.clear()

      val now = System.currentTimeMillis()
      val updatedTasks = currentStore.tasksList.toMutableList()

      var nextTaskId = createNextTaskId(
        currentTasks = updatedTasks
      )

      allowedGeneratedTasks.forEach { generatedTask ->
        if (generatedTask.id.isBlank()) {
          return@forEach
        }

        val existingExactIndex = updatedTasks.indexOfFirst { storedTask ->
          storedTask.belongsToOwner(ownerUid) &&
            storedTask.tankId == generatedTask.tankId &&
            storedTask.source == CareTaskSource.AUTOMATIC.name &&
            storedTask.generatedRuleKey == generatedTask.id
        }

        if (existingExactIndex >= 0) {
          val existingTask = updatedTasks[existingExactIndex].toCareTask()

          if (existingTask.status == CareTaskStatus.COMPLETED) {
            return@forEach
          }

          val updatedTask = existingTask.copy(
            title = generatedTask.titleTr,
            description = generatedTask.messageTr,
            type = generatedTask.taskType.toCareTaskType(),
            reminderEnabled = true,
            waterChangePercent = generatedTask.waterChangePercent,
            note = "",
            updatedAtMillis = now
          )

          updatedTasks[existingExactIndex] = updatedTask.toStoredCareTask()

          tasksToSchedule.add(updatedTask)

          return@forEach
        }

        val rulePrefix = getAutomaticRulePrefix(
          tankId = generatedTask.tankId,
          ruleId = generatedTask.ruleId
        )

        val existingSameRuleIndex = updatedTasks.indexOfFirst { storedTask ->
          storedTask.belongsToOwner(ownerUid) &&
            storedTask.tankId == generatedTask.tankId &&
            storedTask.source == CareTaskSource.AUTOMATIC.name &&
            storedTask.status == CareTaskStatus.PENDING.name &&
            storedTask.generatedRuleKey.startsWith(rulePrefix)
        }

        if (existingSameRuleIndex >= 0) {
          val existingSameRuleTask =
            updatedTasks[existingSameRuleIndex].toCareTask()

          tasksToSchedule.add(existingSameRuleTask)

          return@forEach
        }

        val newTask = generatedTask.copy(
          ownerUid = generatedTask.ownerUid.ifBlank {
            ownerUid
          }
        ).toAutomaticCareTask(
          taskId = nextTaskId,
          createdAtMillis = now,
          updatedAtMillis = now
        )

        nextTaskId += 1L

        updatedTasks.add(
          newTask.toStoredCareTask()
        )

        tasksToSchedule.add(newTask)
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }

    tasksToSchedule
      .distinctBy { task ->
        task.id
      }
      .forEach { task ->
        scheduleTaskReminderIfAllowed(task)
      }
  }

  suspend fun updateTask(
    task: CareTask
  ) {
    val updatedTask = task.copy(
      updatedAtMillis = System.currentTimeMillis()
    )

    context.careTasksDataStore.updateData { currentStore ->
      val updatedTasks = currentStore.tasksList.map { storedTask ->
        if (storedTask.id == task.id && storedTask.belongsToCurrentUser()) {
          updatedTask.toStoredCareTask()
        } else {
          storedTask
        }
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }

    scheduleTaskReminderIfAllowed(updatedTask)
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
    var taskToSchedule: CareTask? = null

    context.careTasksDataStore.updateData { currentStore ->
      val currentTasks = currentStore.tasksList

      val currentTask = currentTasks
        .firstOrNull { storedTask ->
          storedTask.id == taskId && storedTask.belongsToCurrentUser()
        }
        ?.toCareTask()

      if (
        currentTask == null ||
        currentTask.source != CareTaskSource.MANUAL
      ) {
        return@updateData currentStore
      }

      val now = System.currentTimeMillis()

      val updatedTask = currentTask.copy(
        tankId = tankId,
        title = title,
        description = description,
        type = type,
        dueAtMillis = dueAtMillis,
        repeatEnabled = repeatEnabled,
        repeatIntervalDays = repeatIntervalDays.coerceAtLeast(1),
        reminderEnabled = reminderEnabled,
        missedReminderEnabled = reminderEnabled && missedReminderEnabled,
        missedReminderDays = missedReminderDays.coerceAtLeast(1),
        waterChangePercent = if (type == CareTaskType.WATER_CHANGE) {
          waterChangePercent
        } else {
          null
        },
        note = note,
        updatedAtMillis = now
      )

      taskToSchedule = updatedTask

      val updatedTasks = currentTasks.map { storedTask ->
        if (storedTask.id == taskId && storedTask.belongsToCurrentUser()) {
          updatedTask.toStoredCareTask()
        } else {
          storedTask
        }
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }

    taskToSchedule?.let { scheduledTask ->
      scheduleTaskReminderIfAllowed(scheduledTask)
    }
  }

  suspend fun completeTask(
    taskId: Long
  ) {
    var completedTaskId: Long? = null
    var nextTaskToSchedule: CareTask? = null

    context.careTasksDataStore.updateData { currentStore ->
      val now = System.currentTimeMillis()
      val currentTasks = currentStore.tasksList

      val targetTask = currentTasks
        .firstOrNull { storedTask ->
          storedTask.id == taskId && storedTask.belongsToCurrentUser()
        }
        ?.toCareTask()

      if (targetTask == null) {
        return@updateData currentStore
      }

      completedTaskId = targetTask.id

      val completedTask = targetTask.copy(
        status = CareTaskStatus.COMPLETED,
        completedAtMillis = now,
        updatedAtMillis = now
      )

      val updatedTasks = currentTasks.map { storedTask ->
        if (storedTask.id == taskId && storedTask.belongsToCurrentUser()) {
          completedTask.toStoredCareTask()
        } else {
          storedTask
        }
      }.toMutableList()

      if (
        targetTask.repeatEnabled &&
        targetTask.repeatIntervalDays > 0
      ) {
        val nextDueAtMillis = now + TimeUnit.DAYS.toMillis(
          targetTask.repeatIntervalDays.toLong()
        )

        val nextTask = targetTask.copy(
          id = createNextTaskId(
            currentTasks = updatedTasks
          ),
          status = CareTaskStatus.PENDING,
          dueAtMillis = nextDueAtMillis,
          completedAtMillis = null,
          createdAtMillis = now,
          updatedAtMillis = now
        )

        nextTaskToSchedule = nextTask

        updatedTasks.add(
          nextTask.toStoredCareTask()
        )
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }

    completedTaskId?.let { id ->
      CareTaskReminderScheduler.cancel(
        context = context,
        taskId = id,
        ownerUid = UserDataScope.currentUid()
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
    context.careTasksDataStore.updateData { currentStore ->
      val currentTasks = currentStore.tasksList
      val now = System.currentTimeMillis()

      val updatedTasks = currentTasks.map { storedTask ->
        if (storedTask.id != taskId || !storedTask.belongsToCurrentUser()) {
          storedTask
        } else {
          val currentTask = storedTask.toCareTask()

          if (currentTask.status != CareTaskStatus.COMPLETED) {
            storedTask
          } else {
            currentTask.copy(
              dueAtMillis = completedAtMillis,
              completedAtMillis = completedAtMillis,
              updatedAtMillis = now
            ).toStoredCareTask()
          }
        }
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }
  }

  suspend fun deleteManualTask(
    taskId: Long
  ) {
    var deletedTaskId: Long? = null

    context.careTasksDataStore.updateData { currentStore ->
      val targetTask = currentStore.tasksList
        .firstOrNull { storedTask ->
          storedTask.id == taskId && storedTask.belongsToCurrentUser()
        }
        ?.toCareTask()

      if (
        targetTask == null ||
        targetTask.source != CareTaskSource.MANUAL
      ) {
        return@updateData currentStore
      }

      deletedTaskId = targetTask.id

      val updatedTasks = currentStore.tasksList.filterNot { storedTask ->
        storedTask.id == taskId && storedTask.belongsToCurrentUser()
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }

    deletedTaskId?.let { id ->
      CareTaskReminderScheduler.cancel(
        context = context,
        taskId = id,
        ownerUid = UserDataScope.currentUid()
      )
    }
  }

  suspend fun deleteTask(
    taskId: Long
  ) {
    var deletedTaskId: Long? = null

    context.careTasksDataStore.updateData { currentStore ->
      val exists = currentStore.tasksList.any { storedTask ->
        storedTask.id == taskId && storedTask.belongsToCurrentUser()
      }

      if (!exists) {
        return@updateData currentStore
      }

      deletedTaskId = taskId

      val updatedTasks = currentStore.tasksList.filterNot { storedTask ->
        storedTask.id == taskId && storedTask.belongsToCurrentUser()
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }

    deletedTaskId?.let { id ->
      CareTaskReminderScheduler.cancel(
        context = context,
        taskId = id,
        ownerUid = UserDataScope.currentUid()
      )
    }
  }

  suspend fun deleteTasksForTank(
    tankId: Long
  ) {
    val deletedTaskIds = mutableListOf<Long>()

    context.careTasksDataStore.updateData { currentStore ->
      deletedTaskIds.clear()

      currentStore.tasksList.forEach { storedTask ->
        if (storedTask.tankId == tankId && storedTask.belongsToCurrentUser()) {
          deletedTaskIds.add(storedTask.id)
        }
      }

      if (deletedTaskIds.isEmpty()) {
        return@updateData currentStore
      }

      val updatedTasks = currentStore.tasksList.filterNot { storedTask ->
        storedTask.tankId == tankId && storedTask.belongsToCurrentUser()
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }

    deletedTaskIds.forEach { taskId ->
      CareTaskReminderScheduler.cancel(
        context = context,
        taskId = taskId,
        ownerUid = UserDataScope.currentUid()
      )
    }
  }

  suspend fun clearAllTasks(
    ownerUid: String? = null,
    cancelReminders: Boolean = true
  ) {
    val targetOwnerUid = ownerUid.orCurrentOwnerUidOrReturn()

    val deletedTasks = if (cancelReminders) {
      context.careTasksDataStore.data
        .first()
        .tasksList
        .filter { storedTask ->
          storedTask.belongsToOwner(targetOwnerUid)
        }
        .map { storedTask ->
          storedTask.toCareTask()
        }
    } else {
      emptyList()
    }

    context.careTasksDataStore.updateData { currentStore ->
      val remainingTasks = currentStore.tasksList.filterNot { storedTask ->
        storedTask.belongsToOwner(targetOwnerUid)
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(remainingTasks)
        .build()
    }

    deletedTasks.forEach { task ->
      CareTaskReminderScheduler.cancel(
        context = context,
        taskId = task.id,
        ownerUid = task.ownerUid.ifBlank { targetOwnerUid }
      )
    }
  }

  suspend fun assignLegacyTasksToOwner(
    ownerUid: String
  ) {
    val targetOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

    if (targetOwnerUid.isBlank()) {
      return
    }

    context.careTasksDataStore.updateData { currentStore ->
      val updatedTasks = currentStore.tasksList.map { storedTask ->
        if (storedTask.ownerUid.isBlank()) {
          storedTask.toBuilder()
            .setOwnerUid(targetOwnerUid)
            .build()
        } else {
          storedTask
        }
      }

      currentStore.toBuilder()
        .clearTasks()
        .addAllTasks(updatedTasks)
        .build()
    }
  }

  suspend fun cancelPendingRemindersForTank(
    tankId: Long
  ) {
    val pendingTasks = pendingTasksFlow.first()
      .filter { task ->
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
    val pendingTasks = pendingTasksFlow.first()
      .filter { task ->
        task.tankId == tankId
      }

    pendingTasks.forEach { task ->
      scheduleTaskReminderIfAllowed(task)
    }
  }

  private suspend fun scheduleTaskReminderIfAllowed(
    task: CareTask
  ) {
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
    if (task.status != CareTaskStatus.PENDING) {
      return false
    }

    if (!task.reminderEnabled) {
      return false
    }

    if (task.tankId <= 0L) {
      return false
    }

    val globalNotificationsEnabled =
      userPreferencesManager.notificationsEnabled.first()

    if (!globalNotificationsEnabled) {
      return false
    }

    val tank = tankDataStoreManager.tanksFlow.first()
      .firstOrNull { savedTank ->
        savedTank.id == task.tankId
      } ?: return false

    return tank.careRemindersEnabled
  }

  private suspend fun isSmartCareEnabledForTank(
    tankId: Long
  ): Boolean {
    if (tankId <= 0L) {
      return false
    }

    val tank = tankDataStoreManager.tanksFlow.first()
      .firstOrNull { savedTank ->
        savedTank.id == tankId
      } ?: return false

    return tank.smartCareEnabled
  }

  private suspend fun filterGeneratedTasksBySmartCareSettings(
    generatedTasks: List<SmartCareGeneratedTask>
  ): List<SmartCareGeneratedTask> {
    if (generatedTasks.isEmpty()) {
      return emptyList()
    }

    val tanksById = tankDataStoreManager.tanksFlow.first()
      .associateBy { tank ->
        tank.id
      }

    return generatedTasks.filter { generatedTask ->
      tanksById[generatedTask.tankId]?.smartCareEnabled == true
    }
  }

  private fun StoredCareTask.toCareTask(): CareTask {
    return CareTask(
      id = id,
      ownerUid = ownerUid,
      tankId = tankId,
      title = title,
      description = description,
      type = parseCareTaskType(type),
      source = parseCareTaskSource(source),
      status = parseCareTaskStatus(status),
      dueAtMillis = dueAtMillis,
      completedAtMillis = completedAtMillis.takeIf { value ->
        value > 0L
      },
      repeatEnabled = repeatEnabled,
      repeatIntervalDays = repeatIntervalDays.coerceAtLeast(1),
      reminderEnabled = reminderEnabled,
      missedReminderEnabled = missedReminderEnabled,
      missedReminderDays = missedReminderDays.coerceAtLeast(1),
      waterChangePercent = waterChangePercent.takeIf { value ->
        value > 0
      },
      note = note,
      generatedRuleKey = generatedRuleKey,
      createdAtMillis = createdAtMillis,
      updatedAtMillis = updatedAtMillis
    )
  }

  private fun CareTask.toStoredCareTask(): StoredCareTask {
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
      .setRepeatIntervalDays(repeatIntervalDays.coerceAtLeast(1))
      .setReminderEnabled(reminderEnabled)
      .setMissedReminderEnabled(missedReminderEnabled)
      .setMissedReminderDays(missedReminderDays.coerceAtLeast(1))
      .setWaterChangePercent(waterChangePercent ?: 0)
      .setNote(note)
      .setGeneratedRuleKey(generatedRuleKey)
      .setCreatedAtMillis(createdAtMillis)
      .setUpdatedAtMillis(updatedAtMillis)
      .build()
  }

  private fun String?.orCurrentOwnerUidOrReturn(): String {
    val explicitOwnerUid = UserDataScope.normalizeOwnerUid(this)

    if (explicitOwnerUid.isNotBlank()) {
      return explicitOwnerUid
    }

    return UserDataScope.currentUid()
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

  private fun parseCareTaskType(
    value: String
  ): CareTaskType {
    return runCatching {
      CareTaskType.valueOf(value)
    }.getOrElse {
      CareTaskType.CUSTOM
    }
  }

  private fun parseCareTaskSource(
    value: String
  ): CareTaskSource {
    return runCatching {
      CareTaskSource.valueOf(value)
    }.getOrElse {
      CareTaskSource.MANUAL
    }
  }

  private fun parseCareTaskStatus(
    value: String
  ): CareTaskStatus {
    return runCatching {
      CareTaskStatus.valueOf(value)
    }.getOrElse {
      CareTaskStatus.PENDING
    }
  }

  private fun SmartCareGeneratedTask.toAutomaticCareTask(
    taskId: Long,
    createdAtMillis: Long,
    updatedAtMillis: Long
  ): CareTask {
    return CareTask(
      id = taskId,
      ownerUid = ownerUid,
      tankId = tankId,
      title = titleTr,
      description = messageTr,
      type = taskType.toCareTaskType(),
      source = CareTaskSource.AUTOMATIC,
      status = CareTaskStatus.PENDING,
      dueAtMillis = dueAtMillis,
      completedAtMillis = null,
      repeatEnabled = false,
      repeatIntervalDays = 1,
      reminderEnabled = true,
      missedReminderEnabled = false,
      missedReminderDays = 1,
      waterChangePercent = waterChangePercent,
      note = "",
      generatedRuleKey = id,
      createdAtMillis = createdAtMillis,
      updatedAtMillis = updatedAtMillis
    )
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
    return "smart_${tankId}_${ruleId}_"
  }

  private fun createNextTaskId(
    currentTasks: List<StoredCareTask>
  ): Long {
    val now = System.currentTimeMillis()

    val maxExistingId = currentTasks.maxOfOrNull { task ->
      task.id
    } ?: 0L

    return maxOf(
      now,
      maxExistingId + 1L
    )
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
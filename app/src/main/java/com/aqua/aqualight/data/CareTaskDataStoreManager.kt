package com.aqua.aqualight.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.aqua.aqualight.data.care.CareTasksStore
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTask
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskSource
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskStatus
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskType
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.flow.Flow
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

  val tasksFlow: Flow<List<CareTask>> =
  context.careTasksDataStore.data.map {
    store ->
    store.tasksList.map {
      storedTask ->
      storedTask.toCareTask()
    }
  }

  val pendingTasksFlow: Flow<List<CareTask>> =
  tasksFlow.map {
    tasks ->
    tasks
    .filter {
      task ->
      task.status == CareTaskStatus.PENDING
    }
    .sortedBy {
      task ->
      task.dueAtMillis
    }
  }

  val historyTasksFlow: Flow<List<CareTask>> =
  tasksFlow.map {
    tasks ->
    tasks
    .filter {
      task ->
      task.status == CareTaskStatus.COMPLETED
    }
    .sortedByDescending {
      task ->
      task.completedAtMillis ?: 0L
    }
  }

  fun tasksForTankFlow(
    tankId: Long
  ): Flow<List<CareTask>> {
    return tasksFlow.map {
      tasks ->
      tasks
      .filter {
        task ->
        task.tankId == tankId
      }
      .sortedBy {
        task ->
        task.dueAtMillis
      }
    }
  }

  fun taskFlow(
    taskId: Long
  ): Flow<CareTask?> {
    return tasksFlow.map {
      tasks ->
      tasks.firstOrNull {
        task ->
        task.id == taskId
      }
    }
  }

  suspend fun addTask(
    task: CareTask
  ) {
    context.careTasksDataStore.updateData {
      currentStore ->
      currentStore.toBuilder()
      .addTasks(task.toStoredCareTask())
      .build()
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
  ) {
    val now = System.currentTimeMillis()

    val task = CareTask(
      id = now,
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

  suspend fun addOrUpdateAutomaticTask(
    task: CareTask
  ) {
    context.careTasksDataStore.updateData {
      currentStore ->
      val currentTasks = currentStore.tasksList

      val existingPendingAutoTask = currentTasks.firstOrNull {
        storedTask ->
        storedTask.tankId == task.tankId &&
        storedTask.source == CareTaskSource.AUTOMATIC.name &&
        storedTask.status == CareTaskStatus.PENDING.name &&
        storedTask.generatedRuleKey == task.generatedRuleKey &&
        task.generatedRuleKey.isNotBlank()
      }

      if (existingPendingAutoTask == null) {
        currentStore.toBuilder()
        .addTasks(task.toStoredCareTask())
        .build()
      } else {
        val updatedTasks = currentTasks.map {
          storedTask ->
          if (storedTask.id == existingPendingAutoTask.id) {
            task.copy(
              id = existingPendingAutoTask.id,
              createdAtMillis = existingPendingAutoTask.createdAtMillis,
              updatedAtMillis = System.currentTimeMillis()
            ).toStoredCareTask()
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
  }

  suspend fun updateTask(
    task: CareTask
  ) {
    context.careTasksDataStore.updateData {
      currentStore ->
      val updatedTasks = currentStore.tasksList.map {
        storedTask ->
        if (storedTask.id == task.id) {
          task.copy(
            updatedAtMillis = System.currentTimeMillis()
          ).toStoredCareTask()
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
    context.careTasksDataStore.updateData {
      currentStore ->
      val currentTasks = currentStore.tasksList

      val currentTask = currentTasks
      .firstOrNull {
        storedTask ->
        storedTask.id == taskId
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

      val updatedTasks = currentTasks.map {
        storedTask ->
        if (storedTask.id == taskId) {
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

  suspend fun completeTask(
    taskId: Long
  ) {
    context.careTasksDataStore.updateData {
      currentStore ->
      val now = System.currentTimeMillis()
      val currentTasks = currentStore.tasksList

      val targetTask = currentTasks
      .firstOrNull {
        storedTask ->
        storedTask.id == taskId
      }
      ?.toCareTask()

      if (targetTask == null) {
        return@updateData currentStore
      }

      val completedTask = targetTask.copy(
        status = CareTaskStatus.COMPLETED,
        completedAtMillis = now,
        updatedAtMillis = now
      )

      val updatedTasks = currentTasks.map {
        storedTask ->
        if (storedTask.id == taskId) {
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

        updatedTasks.add(
          nextTask.toStoredCareTask()
        )
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
    context.careTasksDataStore.updateData {
      currentStore ->
      val targetTask = currentStore.tasksList
      .firstOrNull {
        storedTask ->
        storedTask.id == taskId
      }
      ?.toCareTask()

      if (
        targetTask == null ||
        targetTask.source != CareTaskSource.MANUAL
      ) {
        return@updateData currentStore
      }

      val updatedTasks = currentStore.tasksList.filterNot {
        storedTask ->
        storedTask.id == taskId
      }

      currentStore.toBuilder()
      .clearTasks()
      .addAllTasks(updatedTasks)
      .build()
    }
  }

  suspend fun deleteTask(
    taskId: Long
  ) {
    context.careTasksDataStore.updateData {
      currentStore ->
      val updatedTasks = currentStore.tasksList.filterNot {
        storedTask ->
        storedTask.id == taskId
      }

      currentStore.toBuilder()
      .clearTasks()
      .addAllTasks(updatedTasks)
      .build()
    }
  }

  suspend fun deleteTasksForTank(
    tankId: Long
  ) {
    context.careTasksDataStore.updateData {
      currentStore ->
      val updatedTasks = currentStore.tasksList.filterNot {
        storedTask ->
        storedTask.tankId == tankId
      }

      currentStore.toBuilder()
      .clearTasks()
      .addAllTasks(updatedTasks)
      .build()
    }
  }

  private fun StoredCareTask.toCareTask(): CareTask {
    return CareTask(
      id = id,
      tankId = tankId,
      title = title,
      description = description,
      type = parseCareTaskType(type),
      source = parseCareTaskSource(source),
      status = parseCareTaskStatus(status),
      dueAtMillis = dueAtMillis,
      completedAtMillis = completedAtMillis.takeIf {
        value ->
        value > 0L
      },
      repeatEnabled = repeatEnabled,
      repeatIntervalDays = repeatIntervalDays.coerceAtLeast(1),
      reminderEnabled = reminderEnabled,
      missedReminderEnabled = missedReminderEnabled,
      missedReminderDays = missedReminderDays.coerceAtLeast(1),
      waterChangePercent = waterChangePercent.takeIf {
        value ->
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

  private fun createNextTaskId(
    currentTasks: List<StoredCareTask>
  ): Long {
    val now = System.currentTimeMillis()
    val maxExistingId = currentTasks.maxOfOrNull {
      task ->
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
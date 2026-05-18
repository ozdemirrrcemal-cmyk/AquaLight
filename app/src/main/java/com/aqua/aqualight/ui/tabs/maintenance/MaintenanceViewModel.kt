package com.aqua.aqualight.ui.tabs.maintenance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.CareTaskDataStoreManager
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTask
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskSource
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskStatus
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskType
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskTypeCatalog
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MaintenanceViewModel(
  application: Application
) : AndroidViewModel(application) {

  private val careTaskDataStoreManager =
    CareTaskDataStoreManager.create(application)

  private val selectedTabFlow = MutableStateFlow(
    MaintenanceTab.ALL
  )

  private val tanksFlow = MutableStateFlow<List<SavedAquariumTank>>(
    emptyList()
  )
  
  val tanks: StateFlow<List<SavedAquariumTank>> = tanksFlow

  val selectedTab: StateFlow<MaintenanceTab> = selectedTabFlow

  val taskItems: StateFlow<List<CareTaskUi>> =
    combine(
      careTaskDataStoreManager.tasksFlow,
      tanksFlow,
      selectedTabFlow
    ) { tasks, tanks, selectedTab ->
      val filteredTasks = filterTasksByTab(
        tasks = tasks,
        tab = selectedTab
      )

      filteredTasks.map { task ->
        task.toCareTaskUi(
          tankName = getTankName(
            tankId = task.tankId,
            tanks = tanks
          )
        )
      }
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = emptyList()
    )

  fun setTanks(
    tanks: List<SavedAquariumTank>
  ) {
    tanksFlow.value = tanks
  }

  fun selectTab(
    tab: MaintenanceTab
  ) {
    selectedTabFlow.value = tab
  }

  fun completeTask(
    taskId: Long
  ) {
    viewModelScope.launch {
      careTaskDataStoreManager.completeTask(
        taskId = taskId
      )
    }
  }

  fun deleteTask(
    taskId: Long
  ) {
    viewModelScope.launch {
      careTaskDataStoreManager.deleteTask(
        taskId = taskId
      )
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
    careTaskDataStoreManager.addManualTask(
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

  private fun filterTasksByTab(
    tasks: List<CareTask>,
    tab: MaintenanceTab
  ): List<CareTask> {
    val todayStartMillis = getTodayStartMillis()
    val tomorrowStartMillis = getTomorrowStartMillis()

    return when (tab) {
      MaintenanceTab.ALL -> {
        tasks
          .filter { task ->
            task.status == CareTaskStatus.PENDING
          }
          .sortedBy { task ->
            task.dueAtMillis
          }
      }

      MaintenanceTab.TODAY -> {
        tasks
          .filter { task ->
            task.status == CareTaskStatus.PENDING &&
              task.dueAtMillis < tomorrowStartMillis
          }
          .sortedBy { task ->
            task.dueAtMillis
          }
      }

      MaintenanceTab.UPCOMING -> {
        tasks
          .filter { task ->
            task.status == CareTaskStatus.PENDING &&
              task.dueAtMillis >= tomorrowStartMillis
          }
          .sortedBy { task ->
            task.dueAtMillis
          }
      }

      MaintenanceTab.HISTORY -> {
        tasks
          .filter { task ->
            task.status == CareTaskStatus.COMPLETED
          }
          .sortedByDescending { task ->
            task.completedAtMillis ?: 0L
          }
      }
    }
  }

  private fun CareTask.toCareTaskUi(
    tankName: String
  ): CareTaskUi {
    val typeUi = CareTaskTypeCatalog.get(type)
    val now = System.currentTimeMillis()

    return CareTaskUi(
      id = id,
      tankId = tankId,
      tankName = tankName,
      title = getTaskTitle(
        task = this,
        typeTitle = typeUi.title
      ),
      description = description.ifBlank {
        typeUi.defaultDescription
      },
      type = type,
      typeTitle = typeUi.title,
      source = source,
      sourceLabel = getSourceLabel(source),
      status = status,
      dueAtMillis = dueAtMillis,
      completedAtMillis = completedAtMillis,
      repeatEnabled = repeatEnabled,
      repeatIntervalDays = repeatIntervalDays,
      reminderEnabled = reminderEnabled,
      missedReminderEnabled = missedReminderEnabled,
      missedReminderDays = missedReminderDays,
      waterChangePercent = waterChangePercent,
      note = note,
      iconRes = typeUi.iconRes,
      accentColor = typeUi.accentColor,
      isOverdue = status == CareTaskStatus.PENDING &&
        dueAtMillis < getTodayStartMillis(),
      primaryTimeText = getPrimaryTimeText(this),
      secondaryText = getSecondaryText(this),
    )
  }

  private fun getTaskTitle(
    task: CareTask,
    typeTitle: String
  ): String {
    if (task.type == CareTaskType.WATER_CHANGE) {
      val percent = task.waterChangePercent

      if (percent != null && percent > 0) {
        return "$typeTitle ($percent%)"
      }
    }

    return task.title.ifBlank {
      typeTitle
    }
  }

  private fun getSourceLabel(
    source: CareTaskSource
  ): String {
    return when (source) {
      CareTaskSource.MANUAL -> "Manual"
      CareTaskSource.AUTOMATIC -> "Auto"
    }
  }

  private fun getPrimaryTimeText(
    task: CareTask
  ): String {
    if (task.status == CareTaskStatus.COMPLETED) {
      val completedAt = task.completedAtMillis

      return if (completedAt == null || completedAt <= 0L) {
        "Completed"
      } else {
        "Completed ${formatTime(completedAt)}"
      }
    }

    val timeText = formatTime(task.dueAtMillis)

    return if (task.repeatEnabled) {
      "$timeText • Every ${task.repeatIntervalDays.coerceAtLeast(1)} days"
    } else {
      timeText
    }
  }

  private fun getSecondaryText(
    task: CareTask
  ): String {
    return when {
      task.note.isNotBlank() -> {
        task.note
      }

      task.reminderEnabled && task.missedReminderEnabled -> {
        "Reminder active • repeats ${task.missedReminderDays.coerceAtLeast(1)} days if missed"
      }

      task.reminderEnabled -> {
        "Reminder active"
      }

      else -> {
        task.description
      }
    }
  }

  private fun getTankName(
    tankId: Long,
    tanks: List<SavedAquariumTank>
  ): String {
    return tanks.firstOrNull { tank ->
      tank.id == tankId
    }?.name ?: "Unknown aquarium"
  }

  private fun getTodayStartMillis(): Long {
    return Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
  }

  private fun getTomorrowStartMillis(): Long {
    return Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
  }

  private fun formatTime(
    millis: Long
  ): String {
    return SimpleDateFormat(
      "HH:mm",
      Locale.getDefault()
    ).format(Date(millis))
  }
}
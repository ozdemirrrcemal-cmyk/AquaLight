package com.aqua.aqualight.ui.tabs.maintenance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import com.aqua.aqualight.data.care.smartcare.SmartCareTaskGenerator
import kotlinx.coroutines.flow.Flow
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job

data class TankActivityUiState(
  val lastTrimText: String = "--",
  val lastWaterChangeText: String = "--",
  val lastFilterMaintenanceText: String = "--",
  val nextCareText: String = "--",
  val nextCareTask: CareTaskUi? = null,
  val completedTasks: List<CareTaskUi> = emptyList()
)

data class TankCareSummaryUi(
  val lastTrimText: String = "--",
  val lastWaterChangeText: String = "--"
)

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

  val tankCareSummaryItems: StateFlow<Map<Long, TankCareSummaryUi>> =
  combine(
    careTaskDataStoreManager.tasksFlow,
    tanksFlow
  ) {
    tasks, tanks ->

    tanks.associate {
      tank ->
      tank.id to buildTankCareSummary(
        tankId = tank.id,
        tasks = tasks
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = emptyMap()
  )

  val taskItems: StateFlow<List<CareTaskUi>> =
  combine(
    careTaskDataStoreManager.tasksFlow,
    tanksFlow,
    selectedTabFlow
  ) {
    tasks, tanks, selectedTab ->
    val filteredTasks = filterTasksByTab(
      tasks = tasks,
      tab = selectedTab
    )

    filteredTasks.map {
      task ->
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

  fun taskByIdFlow(
    taskId: Long
  ): Flow<CareTaskUi?> {
    return combine(
      careTaskDataStoreManager.taskFlow(taskId),
      tanksFlow
    ) {
      task, tanks ->
      task?.toCareTaskUi(
        tankName = getTankName(
          tankId = task.tankId,
          tanks = tanks
        )
      )
    }
  }

  fun tankActivityStateFlow(
    tankId: Long
  ): Flow<TankActivityUiState> {
    return combine(
      careTaskDataStoreManager.tasksFlow,
      tanksFlow
    ) {
      tasks, tanks ->
      val tankName = getTankName(
        tankId = tankId,
        tanks = tanks
      )

      val tankTasks = tasks.filter {
        task ->
        task.tankId == tankId
      }

      val completedTasks = tankTasks
      .filter {
        task ->
        task.status == CareTaskStatus.COMPLETED
      }
      .sortedByDescending {
        task ->
        task.completedAtMillis ?: task.dueAtMillis
      }

      val pendingTasks = tankTasks
      .filter {
        task ->
        task.status == CareTaskStatus.PENDING
      }
      .sortedBy {
        task ->
        task.dueAtMillis
      }

      val completedTaskItems = completedTasks.map {
        task ->
        task.toCareTaskUi(
          tankName = tankName
        )
      }

      val nextCareTask = selectNextCareTask(
        tasks = pendingTasks
      )

      TankActivityUiState(
        lastTrimText = getLastCompletedTaskText(
          tasks = completedTasks,
          types = setOf(
            CareTaskType.PLANT_TRIM
          )
        ),
        lastWaterChangeText = getLastCompletedTaskText(
          tasks = completedTasks,
          types = setOf(
            CareTaskType.WATER_CHANGE
          )
        ),
        lastFilterMaintenanceText = getLastCompletedTaskText(
          tasks = completedTasks,
          types = getFilterMaintenanceTypes()
        ),
        nextCareText = nextCareTask?.let {
          task ->
          getNextCareSummaryText(task)
        } ?: "--",
        nextCareTask = nextCareTask?.toCareTaskUi(
          tankName = tankName
        ),
        completedTasks = completedTaskItems
      )
    }
  }

  fun setTanks(
    tanks: List<SavedAquariumTank>
  ) {
    tanksFlow.value = tanks
    syncSmartCareTasks(tanks)
  }

  private fun syncSmartCareTasks(
    tanks: List<SavedAquariumTank>
  ) {
    if (tanks.isEmpty()) {
      return
    }

    viewModelScope.launch {
      val generatedTasks = SmartCareTaskGenerator.generateForTanks(
        tanks = tanks
      )

      careTaskDataStoreManager.syncAutomaticTasks(
        generatedTasks = generatedTasks
      )
    }
  }

  fun selectTab(
    tab: MaintenanceTab
  ) {
    selectedTabFlow.value = tab
  }

  fun completeTask(
    taskId: Long
  ): Job {
    return viewModelScope.launch {
      careTaskDataStoreManager.completeTask(
        taskId = taskId
      )
    }
  }

  fun deleteTask(
    taskId: Long
  ): Job {
    return viewModelScope.launch {
      careTaskDataStoreManager.deleteTask(
        taskId = taskId
      )
    }
  }

  fun updateCompletedTaskDate(
    taskId: Long,
    completedAtMillis: Long
  ): Job {
    return viewModelScope.launch {
      careTaskDataStoreManager.updateCompletedTaskDate(
        taskId = taskId,
        completedAtMillis = completedAtMillis
      )
    }
  }

  fun addCompletedActivity(
    tankId: Long,
    type: CareTaskType,
    completedAtMillis: Long = System.currentTimeMillis(),
    waterChangePercent: Int? = null,
    note: String = ""
  ): Job {
    return viewModelScope.launch {
      val typeUi = CareTaskTypeCatalog.get(type)

      careTaskDataStoreManager.addCompletedActivity(
        tankId = tankId,
        title = typeUi.title,
        description = typeUi.defaultDescription,
        type = type,
        completedAtMillis = completedAtMillis,
        waterChangePercent = waterChangePercent,
        note = note
      )
    }
  }

  suspend fun deleteManualTask(
    taskId: Long
  ) {
    careTaskDataStoreManager.deleteManualTask(
      taskId = taskId
    )
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
    careTaskDataStoreManager.updateManualTask(
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

  private fun filterTasksByTab(
    tasks: List<CareTask>,
    tab: MaintenanceTab
  ): List<CareTask> {
    val tomorrowStartMillis = getTomorrowStartMillis()

    return when (tab) {
      MaintenanceTab.ALL -> {
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

      MaintenanceTab.TODAY -> {
        tasks
        .filter {
          task ->
          task.status == CareTaskStatus.PENDING &&
          task.dueAtMillis < tomorrowStartMillis
        }
        .sortedBy {
          task ->
          task.dueAtMillis
        }
      }

      MaintenanceTab.UPCOMING -> {
        tasks
        .filter {
          task ->
          task.status == CareTaskStatus.PENDING &&
          task.dueAtMillis >= tomorrowStartMillis
        }
        .sortedBy {
          task ->
          task.dueAtMillis
        }
      }

      MaintenanceTab.HISTORY -> {
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
    }
  }

  private fun buildTankCareSummary(
    tankId: Long,
    tasks: List<CareTask>
  ): TankCareSummaryUi {
    val completedTasks = tasks.filter {
      task ->
      task.tankId == tankId &&
      task.status == CareTaskStatus.COMPLETED
    }

    return TankCareSummaryUi(
      lastTrimText = getLastCompletedTaskText(
        tasks = completedTasks,
        types = setOf(
          CareTaskType.PLANT_TRIM
        )
      ),
      lastWaterChangeText = getLastCompletedTaskText(
        tasks = completedTasks,
        types = setOf(
          CareTaskType.WATER_CHANGE
        )
      )
    )
  }

  private fun CareTask.toCareTaskUi(
    tankName: String
  ): CareTaskUi {
    val typeUi = CareTaskTypeCatalog.get(type)

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
      createdAtMillis = createdAtMillis,
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
      secondaryText = getSecondaryText(this)
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
      CareTaskSource.AUTOMATIC -> "Smart"
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
    if (
      task.source == CareTaskSource.AUTOMATIC &&
      task.description.isNotBlank()
    ) {
      return task.description
    }

    return when {
      task.note.isNotBlank() -> {
        task.note
      }

      task.reminderEnabled && task.missedReminderEnabled -> {
        "Reminder active • repeats ${task.missedReminderDays.coerceAtLeast(1)} days if missed"
      }

      task.reminderEnabled -> {
        "Reminder active"
      } else -> {
        task.description
      }
    }
  }

  private fun getLastCompletedTaskText(
    tasks: List<CareTask>,
    types: Set<CareTaskType>
  ): String {
    val lastTask = tasks
    .filter {
      task ->
      task.type in types
    }
    .maxByOrNull {
      task ->
      task.completedAtMillis ?: task.dueAtMillis
    }

    val completedAt = lastTask?.completedAtMillis ?: lastTask?.dueAtMillis

    return if (completedAt == null || completedAt <= 0L) {
      "--"
    } else {
      getDaysAgoText(completedAt)
    }
  }

  private fun getFilterMaintenanceTypes(): Set<CareTaskType> {
    return setOf(
      CareTaskType.FILTER_MAINTENANCE,
      CareTaskType.FILTER_CHANGE,
      CareTaskType.PRE_FILTER_CLEANING,
      CareTaskType.PIPE_CLEANING,
      CareTaskType.DIFFUSER_CLEANING,
      CareTaskType.HOSE_CLEANING
    )
  }

  private fun selectNextCareTask(
    tasks: List<CareTask>
  ): CareTask? {
    if (tasks.isEmpty()) {
      return null
    }

    val now = System.currentTimeMillis()
    val tomorrowStartMillis = getTomorrowStartMillis()

    val overdueTask = tasks
    .filter {
      task ->
      task.dueAtMillis < now
    }
    .minByOrNull {
      task ->
      task.dueAtMillis
    }

    if (overdueTask != null) {
      return overdueTask
    }

    val todayTask = tasks
    .filter {
      task ->
      task.dueAtMillis < tomorrowStartMillis
    }
    .minByOrNull {
      task ->
      task.dueAtMillis
    }

    if (todayTask != null) {
      return todayTask
    }

    val smartTask = tasks
    .filter {
      task ->
      task.source == CareTaskSource.AUTOMATIC
    }
    .minByOrNull {
      task ->
      task.dueAtMillis
    }

    if (smartTask != null) {
      return smartTask
    }

    return tasks.minByOrNull {
      task ->
      task.dueAtMillis
    }
  }

  private fun getNextCareSummaryText(
    task: CareTask
  ): String {
    val todayStartMillis = getTodayStartMillis()
    val dueDayStartMillis = getStartOfDayMillis(task.dueAtMillis)

    val daysUntil = TimeUnit.MILLISECONDS.toDays(
      dueDayStartMillis - todayStartMillis
    )

    return when {
      daysUntil < 0L -> {
        "Overdue"
      }

      daysUntil == 0L -> {
        "Today"
      }

      daysUntil == 1L -> {
        "Tomorrow"
      } else -> {
        "$daysUntil days later"
      }
    }
  }

  private fun getDaysAgoText(
    millis: Long
  ): String {
    val todayStartMillis = getTodayStartMillis()
    val targetStartMillis = getStartOfDayMillis(millis)

    val daysAgo = TimeUnit.MILLISECONDS
    .toDays(todayStartMillis - targetStartMillis)
    .coerceAtLeast(0L)

    return when (daysAgo) {
      0L -> {
        "Today"
      }

      1L -> {
        "1 day ago"
      } else -> {
        "$daysAgo days ago"
      }
    }
  }

  private fun getTankName(
    tankId: Long,
    tanks: List<SavedAquariumTank>
  ): String {
    return tanks.firstOrNull {
      tank ->
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

  private fun getStartOfDayMillis(
    millis: Long
  ): Long {
    return Calendar.getInstance().apply {
      timeInMillis = millis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
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
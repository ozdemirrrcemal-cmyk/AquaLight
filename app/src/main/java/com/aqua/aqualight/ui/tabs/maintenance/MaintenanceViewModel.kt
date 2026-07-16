package com.aqua.aqualight.ui.tabs.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.application.care.CompletedCareActivityInput
import com.aqua.aqualight.application.care.MaintenanceOperations
import com.aqua.aqualight.application.care.ManualCareTaskInput
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TankNextCareStatus {
    NONE,
    OVERDUE,
    TODAY,
    TOMORROW,
    FUTURE
}

data class TankActivityUiState(
    val lastTrimText: String = "--",
    val lastWaterChangeText: String = "--",
    val lastFilterMaintenanceText: String = "--",
    val nextCareText: String = "--",
    val nextCareStatus: TankNextCareStatus = TankNextCareStatus.NONE,
    val nextCareTask: CareTaskUi? = null,
    val completedTasks: List<CareTaskUi> = emptyList()
)

data class TankCareSummaryUi(
    val lastTrimText: String = "--",
    val lastWaterChangeText: String = "--"
)

class MaintenanceViewModel(
    private val operations: MaintenanceOperations,
    private val textResolver: MaintenanceTextResolver
) : ViewModel() {

    private val selectedTabFlow = MutableStateFlow(MaintenanceTab.ALL)
    private val tanksFlow = MutableStateFlow<List<AquariumTankSnapshot>>(emptyList())

    val tanks: StateFlow<List<AquariumTankSnapshot>> = tanksFlow
    val selectedTab: StateFlow<MaintenanceTab> = selectedTabFlow

    val tankCareSummaryItems: StateFlow<Map<Long, TankCareSummaryUi>> =
        combine(operations.tasks, tanksFlow) { tasks, tanks ->
            tanks.associate { tank ->
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
            operations.tasks,
            tanksFlow,
            selectedTabFlow
        ) { tasks, tanks, selectedTab ->
            filterTasksByTab(tasks, selectedTab).map { task ->
                task.toCareTaskUi(
                    tankName = getTankName(task.tankId, tanks)
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun taskByIdFlow(taskId: Long): Flow<CareTaskUi?> {
        return combine(
            operations.task(taskId),
            tanksFlow
        ) { task, tanks ->
            task?.toCareTaskUi(
                tankName = getTankName(task.tankId, tanks)
            )
        }
    }

    fun tankActivityStateFlow(tankId: Long): Flow<TankActivityUiState> {
        return combine(operations.tasks, tanksFlow) { tasks, tanks ->
            val tankName = getTankName(tankId, tanks)
            val tankTasks = tasks.filter { task -> task.tankId == tankId }
            val completedTasks = tankTasks
                .filter { task -> task.status == CareTaskStatus.COMPLETED }
                .sortedByDescending { task ->
                    task.completedAtMillis ?: task.dueAtMillis
                }
            val pendingTasks = tankTasks
                .filter { task -> task.status == CareTaskStatus.PENDING }
                .sortedBy(CareTaskSnapshot::dueAtMillis)
            val nextCareTask = selectNextCareTask(pendingTasks)

            TankActivityUiState(
                lastTrimText = getLastCompletedTaskText(
                    tasks = completedTasks,
                    types = setOf(CareTaskType.PLANT_TRIM)
                ),
                lastWaterChangeText = getLastCompletedTaskText(
                    tasks = completedTasks,
                    types = setOf(CareTaskType.WATER_CHANGE)
                ),
                lastFilterMaintenanceText = getLastCompletedTaskText(
                    tasks = completedTasks,
                    types = getFilterMaintenanceTypes()
                ),
                nextCareText = nextCareTask?.let(::getNextCareSummaryText) ?: "--",
                nextCareStatus = nextCareTask?.let(::getNextCareStatus)
                    ?: TankNextCareStatus.NONE,
                nextCareTask = nextCareTask?.toCareTaskUi(tankName),
                completedTasks = completedTasks.map { task ->
                    task.toCareTaskUi(tankName)
                }
            )
        }
    }

    fun setTanks(tanks: List<AquariumTankSnapshot>) {
        tanksFlow.value = tanks
        if (tanks.isNotEmpty()) {
            viewModelScope.launch {
                operations.syncSmartCareTasks(tanks)
            }
        }
    }

    fun selectTab(tab: MaintenanceTab) {
        selectedTabFlow.value = tab
    }

    fun completeTask(taskId: Long): Job = viewModelScope.launch {
        operations.completeTask(taskId)
    }

    fun deleteTask(taskId: Long): Job = viewModelScope.launch {
        operations.deleteTask(taskId)
    }

    fun updateCompletedTaskDate(
        taskId: Long,
        completedAtMillis: Long
    ): Job = viewModelScope.launch {
        operations.updateCompletedTaskDate(taskId, completedAtMillis)
    }

    fun addCompletedActivity(
        tankId: Long,
        type: CareTaskType,
        completedAtMillis: Long = System.currentTimeMillis(),
        waterChangePercent: Int? = null,
        note: String = ""
    ): Job = viewModelScope.launch {
        operations.addCompletedActivity(
            CompletedCareActivityInput(
                tankId = tankId,
                type = type,
                completedAtMillis = completedAtMillis,
                waterChangePercent = waterChangePercent,
                note = note
            )
        )
    }

    suspend fun deleteManualTask(taskId: Long) {
        operations.deleteManualTask(taskId)
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
        operations.addManualTask(
            ManualCareTaskInput(
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
        operations.updateManualTask(
            taskId = taskId,
            input = ManualCareTaskInput(
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
        )
    }

    private fun filterTasksByTab(
        tasks: List<CareTaskSnapshot>,
        tab: MaintenanceTab
    ): List<CareTaskSnapshot> {
        val tomorrowStartMillis = getTomorrowStartMillis()
        return when (tab) {
            MaintenanceTab.ALL -> tasks
                .filter { task -> task.status == CareTaskStatus.PENDING }
                .sortedBy(CareTaskSnapshot::dueAtMillis)

            MaintenanceTab.TODAY -> tasks
                .filter { task ->
                    task.status == CareTaskStatus.PENDING &&
                        task.dueAtMillis < tomorrowStartMillis
                }
                .sortedBy(CareTaskSnapshot::dueAtMillis)

            MaintenanceTab.UPCOMING -> tasks
                .filter { task ->
                    task.status == CareTaskStatus.PENDING &&
                        task.dueAtMillis >= tomorrowStartMillis
                }
                .sortedBy(CareTaskSnapshot::dueAtMillis)

            MaintenanceTab.HISTORY -> tasks
                .filter { task -> task.status == CareTaskStatus.COMPLETED }
                .sortedByDescending { task -> task.completedAtMillis ?: 0L }
        }
    }

    private fun buildTankCareSummary(
        tankId: Long,
        tasks: List<CareTaskSnapshot>
    ): TankCareSummaryUi {
        val completedTasks = tasks.filter { task ->
            task.tankId == tankId && task.status == CareTaskStatus.COMPLETED
        }
        return TankCareSummaryUi(
            lastTrimText = getLastCompletedTaskText(
                completedTasks,
                setOf(CareTaskType.PLANT_TRIM)
            ),
            lastWaterChangeText = getLastCompletedTaskText(
                completedTasks,
                setOf(CareTaskType.WATER_CHANGE)
            )
        )
    }

    private fun CareTaskSnapshot.toCareTaskUi(tankName: String): CareTaskUi {
        val presentation = textResolver.typePresentation(type)
        return CareTaskUi(
            id = id,
            tankId = tankId,
            tankName = tankName,
            title = getTaskTitle(this, presentation.title),
            description = description.ifBlank { presentation.defaultDescription },
            type = type,
            typeTitle = presentation.title,
            source = source,
            sourceLabel = textResolver.sourceLabel(source),
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
            iconRes = presentation.iconRes,
            accentColor = presentation.accentColor,
            isOverdue = status == CareTaskStatus.PENDING &&
                dueAtMillis < getTodayStartMillis(),
            primaryTimeText = getPrimaryTimeText(this),
            secondaryText = getSecondaryText(this)
        )
    }

    private fun getTaskTitle(
        task: CareTaskSnapshot,
        typeTitle: String
    ): String {
        val percent = task.waterChangePercent
        if (
            task.type == CareTaskType.WATER_CHANGE &&
            percent != null &&
            percent > 0
        ) {
            return textResolver.waterChangeTitle(typeTitle, percent)
        }
        return task.title.ifBlank { typeTitle }
    }

    private fun getPrimaryTimeText(task: CareTaskSnapshot): String {
        if (task.status == CareTaskStatus.COMPLETED) {
            val completedAt = task.completedAtMillis
            return if (completedAt == null || completedAt <= 0L) {
                textResolver.completedStatus()
            } else {
                textResolver.completedTime(formatTime(completedAt))
            }
        }

        val timeText = formatTime(task.dueAtMillis)
        return if (task.repeatEnabled) {
            textResolver.repeatTime(
                timeText = timeText,
                repeatDays = task.repeatIntervalDays.coerceAtLeast(1)
            )
        } else {
            timeText
        }
    }

    private fun getSecondaryText(task: CareTaskSnapshot): String {
        if (
            task.source == CareTaskSource.AUTOMATIC &&
            task.description.isNotBlank()
        ) {
            return task.description
        }

        return when {
            task.note.isNotBlank() -> task.note
            task.reminderEnabled && task.missedReminderEnabled ->
                textResolver.reminderWithMissedDays(
                    task.missedReminderDays.coerceAtLeast(1)
                )
            task.reminderEnabled -> textResolver.reminderActive()
            else -> task.description
        }
    }

    private fun getLastCompletedTaskText(
        tasks: List<CareTaskSnapshot>,
        types: Set<CareTaskType>
    ): String {
        val lastTask = tasks
            .filter { task -> task.type in types }
            .maxByOrNull { task -> task.completedAtMillis ?: task.dueAtMillis }
        val completedAt = lastTask?.completedAtMillis ?: lastTask?.dueAtMillis
        return if (completedAt == null || completedAt <= 0L) {
            "--"
        } else {
            getDaysAgoText(completedAt)
        }
    }

    private fun getFilterMaintenanceTypes(): Set<CareTaskType> = setOf(
        CareTaskType.FILTER_MAINTENANCE,
        CareTaskType.FILTER_CHANGE,
        CareTaskType.PRE_FILTER_CLEANING,
        CareTaskType.PIPE_CLEANING,
        CareTaskType.DIFFUSER_CLEANING,
        CareTaskType.HOSE_CLEANING
    )

    private fun selectNextCareTask(
        tasks: List<CareTaskSnapshot>
    ): CareTaskSnapshot? {
        if (tasks.isEmpty()) return null

        val now = System.currentTimeMillis()
        val tomorrowStartMillis = getTomorrowStartMillis()
        return tasks
            .filter { task -> task.dueAtMillis < now }
            .minByOrNull(CareTaskSnapshot::dueAtMillis)
            ?: tasks
                .filter { task -> task.dueAtMillis < tomorrowStartMillis }
                .minByOrNull(CareTaskSnapshot::dueAtMillis)
            ?: tasks
                .filter { task -> task.source == CareTaskSource.AUTOMATIC }
                .minByOrNull(CareTaskSnapshot::dueAtMillis)
            ?: tasks.minByOrNull(CareTaskSnapshot::dueAtMillis)
    }

    private fun getNextCareStatus(
        task: CareTaskSnapshot
    ): TankNextCareStatus {
        val daysUntil = TimeUnit.MILLISECONDS.toDays(
            getStartOfDayMillis(task.dueAtMillis) - getTodayStartMillis()
        )
        return when {
            daysUntil < 0L -> TankNextCareStatus.OVERDUE
            daysUntil == 0L -> TankNextCareStatus.TODAY
            daysUntil == 1L -> TankNextCareStatus.TOMORROW
            else -> TankNextCareStatus.FUTURE
        }
    }

    private fun getNextCareSummaryText(task: CareTaskSnapshot): String {
        val daysUntil = TimeUnit.MILLISECONDS.toDays(
            getStartOfDayMillis(task.dueAtMillis) - getTodayStartMillis()
        )
        return when {
            daysUntil < 0L -> textResolver.overdue()
            daysUntil == 0L -> textResolver.today()
            daysUntil == 1L -> textResolver.tomorrow()
            else -> textResolver.daysLater(daysUntil)
        }
    }

    private fun getDaysAgoText(millis: Long): String {
        val daysAgo = TimeUnit.MILLISECONDS
            .toDays(getTodayStartMillis() - getStartOfDayMillis(millis))
            .coerceAtLeast(0L)
        return when (daysAgo) {
            0L -> textResolver.today()
            1L -> textResolver.oneDayAgo()
            else -> textResolver.daysAgo(daysAgo)
        }
    }

    private fun getTankName(
        tankId: Long,
        tanks: List<AquariumTankSnapshot>
    ): String = tanks.firstOrNull { tank -> tank.id == tankId }?.name
        ?: textResolver.unknownAquarium()

    private fun getTodayStartMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun getTomorrowStartMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    private fun getStartOfDayMillis(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun formatTime(millis: Long): String = SimpleDateFormat(
        "HH:mm",
        Locale.getDefault()
    ).format(Date(millis))
}

package com.aqua.aqualight.ui.tabs.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.application.care.CompletedCareActivityInput
import com.aqua.aqualight.application.care.MaintenanceOperations
import com.aqua.aqualight.application.care.ManualCareTaskInput
import com.aqua.aqualight.ui.common.text.AquaUiText
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val lastTrimText: AquaUiText = AquaUiText.Resource(
        R.string.common_not_available_double_symbol
    ),
    val lastWaterChangeText: AquaUiText = AquaUiText.Resource(
        R.string.common_not_available_double_symbol
    ),
    val lastFilterMaintenanceText: AquaUiText = AquaUiText.Resource(
        R.string.common_not_available_double_symbol
    ),
    val nextCareText: AquaUiText = AquaUiText.Resource(
        R.string.common_not_available_double_symbol
    ),
    val nextCareStatus: TankNextCareStatus = TankNextCareStatus.NONE,
    val nextCareTask: CareTaskUi? = null,
    val completedTasks: List<CareTaskUi> = emptyList()
)

data class TankCareSummaryUi(
    val lastTrimText: AquaUiText = AquaUiText.Resource(
        R.string.common_not_available_double_symbol
    ),
    val lastWaterChangeText: AquaUiText = AquaUiText.Resource(
        R.string.common_not_available_double_symbol
    )
)

class MaintenanceViewModel(
    private val operations: MaintenanceOperations,
    private val textResolver: MaintenanceTextResolver
) : ViewModel() {

    private val selectedTabFlow = MutableStateFlow(MaintenanceTab.ALL)
    private val tanksFlow = MutableStateFlow<List<AquariumTankSnapshot>>(emptyList())

    val tanks: StateFlow<List<AquariumTankSnapshot>> = tanksFlow
    val selectedTab: StateFlow<MaintenanceTab> = selectedTabFlow

    init {
        viewModelScope.launch {
            tanksFlow.collectLatest { tanks ->
                if (tanks.isNotEmpty()) {
                    operations.syncSmartCareTasks(tanks)
                }
            }
        }
    }

    val tankCareSummaryItems: StateFlow<Map<Long, TankCareSummaryUi>> =
        combine(
            operations.tasks,
            tanksFlow,
            textResolver.localeChanges
        ) { tasks, tanks, _ ->
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
            selectedTabFlow,
            textResolver.localeChanges
        ) { tasks, tanks, selectedTab, _ ->
            filterTasksByTab(tasks, selectedTab).map { task ->
                task.toCareTaskUi(
                    tank = getTank(task.tankId, tanks)
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
            tanksFlow,
            textResolver.localeChanges
        ) { task, tanks, _ ->
            task?.toCareTaskUi(
                tank = getTank(task.tankId, tanks)
            )
        }
    }

    fun tankActivityStateFlow(tankId: Long): Flow<TankActivityUiState> {
        return combine(
            operations.tasks,
            tanksFlow,
            textResolver.localeChanges
        ) { tasks, tanks, _ ->
            val tank = getTank(tankId, tanks)
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
                nextCareText = nextCareTask
                    ?.let(::getNextCareSummaryText)
                    ?.let(AquaUiText::Dynamic)
                    ?: AquaUiText.Resource(R.string.common_not_available_double_symbol),
                nextCareStatus = nextCareTask?.let(::getNextCareStatus)
                    ?: TankNextCareStatus.NONE,
                nextCareTask = nextCareTask?.toCareTaskUi(tank),
                completedTasks = completedTasks.map { task ->
                    task.toCareTaskUi(tank)
                }
            )
        }
    }

    fun setTanks(tanks: List<AquariumTankSnapshot>) {
        tanksFlow.value = tanks
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

    private fun CareTaskSnapshot.toCareTaskUi(
        tank: AquariumTankSnapshot?
    ): CareTaskUi {
        val typePresentation = textResolver.typePresentation(type)
        val automaticPresentation = if (source == CareTaskSource.AUTOMATIC) {
            textResolver.automaticTaskPresentation(this, tank)
        } else {
            null
        }
        val resolvedTitle = getTaskTitle(
            task = this,
            typeTitle = typePresentation.title,
            automaticTitle = automaticPresentation?.title
        )
        val resolvedDescription = getTaskDescription(
            task = this,
            defaultDescription = typePresentation.defaultDescription,
            automaticDescription = automaticPresentation?.description
        )

        return CareTaskUi(
            id = id,
            tankId = tankId,
            tankName = tank?.name ?: textResolver.unknownAquarium(),
            title = resolvedTitle,
            description = resolvedDescription,
            type = type,
            typeTitle = typePresentation.title,
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
            iconRes = typePresentation.iconRes,
            accentColor = typePresentation.accentColor,
            isOverdue = status == CareTaskStatus.PENDING &&
                dueAtMillis < getTodayStartMillis(),
            primaryTimeText = getPrimaryTimeText(this),
            secondaryText = getSecondaryText(this, resolvedDescription)
        )
    }

    private fun getTaskTitle(
        task: CareTaskSnapshot,
        typeTitle: String,
        automaticTitle: String?
    ): String {
        if (task.source == CareTaskSource.AUTOMATIC) {
            return automaticTitle?.takeIf(String::isNotBlank)
                ?: task.title.ifBlank { typeTitle }
        }

        if (task.type == CareTaskType.CUSTOM) {
            return task.title.ifBlank { typeTitle }
        }

        val percent = task.waterChangePercent
        return if (
            task.type == CareTaskType.WATER_CHANGE &&
            percent != null &&
            percent > 0
        ) {
            textResolver.waterChangeTitle(typeTitle, percent)
        } else {
            typeTitle
        }
    }

    private fun getTaskDescription(
        task: CareTaskSnapshot,
        defaultDescription: String,
        automaticDescription: String?
    ): String {
        return if (task.source == CareTaskSource.AUTOMATIC) {
            automaticDescription?.takeIf(String::isNotBlank)
                ?: task.description.ifBlank { defaultDescription }
        } else {
            defaultDescription
        }
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

    private fun getSecondaryText(
        task: CareTaskSnapshot,
        resolvedDescription: String
    ): String {
        if (
            task.source == CareTaskSource.AUTOMATIC &&
            resolvedDescription.isNotBlank()
        ) {
            return resolvedDescription
        }

        return when {
            task.note.isNotBlank() -> task.note
            task.reminderEnabled && task.missedReminderEnabled ->
                textResolver.reminderWithMissedDays(
                    task.missedReminderDays.coerceAtLeast(1)
                )
            task.reminderEnabled -> textResolver.reminderActive()
            else -> resolvedDescription
        }
    }

    private fun getLastCompletedTaskText(
        tasks: List<CareTaskSnapshot>,
        types: Set<CareTaskType>
    ): AquaUiText {
        val lastTask = tasks
            .filter { task -> task.type in types }
            .maxByOrNull { task -> task.completedAtMillis ?: task.dueAtMillis }
        val completedAt = lastTask?.completedAtMillis ?: lastTask?.dueAtMillis
        return if (completedAt == null || completedAt <= 0L) {
            AquaUiText.Resource(R.string.common_not_available_double_symbol)
        } else {
            AquaUiText.Dynamic(getDaysAgoText(completedAt))
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

    private fun getTank(
        tankId: Long,
        tanks: List<AquariumTankSnapshot>
    ): AquariumTankSnapshot? = tanks.firstOrNull { tank -> tank.id == tankId }

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

    private fun formatTime(millis: Long): String = textResolver.formatTime(millis)
}

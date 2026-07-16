package com.aqua.aqualight.ui.tabs.maintenance

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.data.care.CareTaskTypePresentation
import com.aqua.aqualight.data.care.MaintenanceRepository
import com.aqua.aqualight.data.care.MaintenanceTextResolver
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `empty tank input does not start Smart Care synchronization`() {
        val repository = FakeMaintenanceRepository()
        val viewModel = MaintenanceViewModel(
            repository = repository,
            textResolver = FakeMaintenanceTextResolver
        )

        viewModel.setTanks(emptyList())
        viewModel.selectTab(MaintenanceTab.TODAY)

        assertEquals(0, repository.syncSmartCareCalls)
        assertEquals(MaintenanceTab.TODAY, viewModel.selectedTab.value)
    }

    @Test
    fun `manual mutations delegate through the injected repository`() = runTest {
        val repository = FakeMaintenanceRepository()
        val viewModel = MaintenanceViewModel(
            repository = repository,
            textResolver = FakeMaintenanceTextResolver
        )

        viewModel.deleteManualTask(taskId = 7L)
        viewModel.addManualTask(
            tankId = 11L,
            title = "Water change",
            description = "Replace water",
            type = CareTaskType.WATER_CHANGE,
            dueAtMillis = 1000L,
            repeatEnabled = true,
            repeatIntervalDays = 7,
            reminderEnabled = true,
            missedReminderEnabled = true,
            missedReminderDays = 2,
            waterChangePercent = 25,
            note = "Test note"
        )
        viewModel.updateManualTask(
            taskId = 9L,
            tankId = 12L,
            title = "Updated task",
            description = "Updated description",
            type = CareTaskType.FILTER_MAINTENANCE,
            dueAtMillis = 2000L,
            repeatEnabled = false,
            repeatIntervalDays = 1,
            reminderEnabled = false,
            missedReminderEnabled = false,
            missedReminderDays = 1,
            waterChangePercent = null,
            note = "Updated note"
        )

        assertEquals(7L, repository.deletedManualTaskId)
        assertEquals(11L, repository.addedManualTankId)
        assertEquals(CareTaskType.WATER_CHANGE, repository.addedManualType)
        assertEquals(25, repository.addedWaterChangePercent)
        assertEquals(9L, repository.updatedManualTaskId)
        assertEquals(12L, repository.updatedManualTankId)
        assertEquals(CareTaskType.FILTER_MAINTENANCE, repository.updatedManualType)
        assertNull(repository.updatedWaterChangePercent)
    }

    private class FakeMaintenanceRepository : MaintenanceRepository {
        override val tasksFlow = MutableStateFlow<List<CareTask>>(emptyList())

        var syncSmartCareCalls = 0
        var deletedManualTaskId: Long? = null
        var addedManualTankId: Long? = null
        var addedManualType: CareTaskType? = null
        var addedWaterChangePercent: Int? = null
        var updatedManualTaskId: Long? = null
        var updatedManualTankId: Long? = null
        var updatedManualType: CareTaskType? = null
        var updatedWaterChangePercent: Int? = null

        override fun taskFlow(taskId: Long): Flow<CareTask?> = flowOf(null)

        override suspend fun syncSmartCareTasks(tanks: List<AquariumTankSnapshot>) {
            syncSmartCareCalls += 1
        }

        override suspend fun completeTask(taskId: Long) = Unit

        override suspend fun deleteTask(taskId: Long) = Unit

        override suspend fun updateCompletedTaskDate(
            taskId: Long,
            completedAtMillis: Long
        ) = Unit

        override suspend fun addCompletedActivity(
            tankId: Long,
            type: CareTaskType,
            completedAtMillis: Long,
            waterChangePercent: Int?,
            note: String
        ) = Unit

        override suspend fun deleteManualTask(taskId: Long) {
            deletedManualTaskId = taskId
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
            addedManualTankId = tankId
            addedManualType = type
            addedWaterChangePercent = waterChangePercent
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
            updatedManualTaskId = taskId
            updatedManualTankId = tankId
            updatedManualType = type
            updatedWaterChangePercent = waterChangePercent
        }
    }

    private object FakeMaintenanceTextResolver : MaintenanceTextResolver {
        override fun typePresentation(type: CareTaskType) = CareTaskTypePresentation(
            title = type.name,
            defaultDescription = type.name,
            iconRes = 0,
            accentColor = "#000000"
        )

        override fun waterChangeTitle(typeTitle: String, percent: Int) =
            "$typeTitle $percent"

        override fun sourceLabel(source: CareTaskSource) = source.name
        override fun completedStatus() = "completed"
        override fun completedTime(timeText: String) = timeText
        override fun repeatTime(timeText: String, repeatDays: Int) = timeText
        override fun reminderWithMissedDays(days: Int) = days.toString()
        override fun reminderActive() = "active"
        override fun overdue() = "overdue"
        override fun today() = "today"
        override fun tomorrow() = "tomorrow"
        override fun daysLater(days: Long) = days.toString()
        override fun oneDayAgo() = "one day ago"
        override fun daysAgo(days: Long) = days.toString()
        override fun unknownAquarium() = "unknown"
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}

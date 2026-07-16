package com.aqua.aqualight.ui.tabs.maintenance

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.application.care.CompletedCareActivityInput
import com.aqua.aqualight.application.care.MaintenanceOperations
import com.aqua.aqualight.application.care.ManualCareTaskInput
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypePresentation
import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver
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
        val operations = FakeMaintenanceOperations()
        val viewModel = MaintenanceViewModel(
            operations = operations,
            textResolver = FakeMaintenanceTextResolver
        )

        viewModel.setTanks(emptyList())
        viewModel.selectTab(MaintenanceTab.TODAY)

        assertEquals(0, operations.syncSmartCareCalls)
        assertEquals(MaintenanceTab.TODAY, viewModel.selectedTab.value)
    }

    @Test
    fun `non-empty tank input delegates Smart Care synchronization`() = runTest {
        val operations = FakeMaintenanceOperations()
        val viewModel = MaintenanceViewModel(
            operations = operations,
            textResolver = FakeMaintenanceTextResolver
        )

        viewModel.setTanks(listOf(tank(id = 11L, name = "Tank 1")))

        assertEquals(1, operations.syncSmartCareCalls)
        assertEquals(listOf(11L), operations.lastSyncedTankIds)
    }

    @Test
    fun `manual mutations delegate typed application inputs`() = runTest {
        val operations = FakeMaintenanceOperations()
        val viewModel = MaintenanceViewModel(
            operations = operations,
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

        assertEquals(7L, operations.deletedManualTaskId)
        assertEquals(11L, operations.addedManualInput?.tankId)
        assertEquals(CareTaskType.WATER_CHANGE, operations.addedManualInput?.type)
        assertEquals(25, operations.addedManualInput?.waterChangePercent)
        assertEquals(9L, operations.updatedManualTaskId)
        assertEquals(12L, operations.updatedManualInput?.tankId)
        assertEquals(CareTaskType.FILTER_MAINTENANCE, operations.updatedManualInput?.type)
        assertNull(operations.updatedManualInput?.waterChangePercent)
    }

    @Test
    fun `completed activity delegates typed application input`() = runTest {
        val operations = FakeMaintenanceOperations()
        val viewModel = MaintenanceViewModel(
            operations = operations,
            textResolver = FakeMaintenanceTextResolver
        )

        viewModel.addCompletedActivity(
            tankId = 21L,
            type = CareTaskType.WATER_CHANGE,
            completedAtMillis = 5000L,
            waterChangePercent = 30,
            note = "Done"
        ).join()

        assertEquals(
            CompletedCareActivityInput(
                tankId = 21L,
                type = CareTaskType.WATER_CHANGE,
                completedAtMillis = 5000L,
                waterChangePercent = 30,
                note = "Done"
            ),
            operations.completedActivityInput
        )
    }

    private class FakeMaintenanceOperations : MaintenanceOperations {
        override val tasks = MutableStateFlow<List<CareTaskSnapshot>>(emptyList())

        var syncSmartCareCalls = 0
        var lastSyncedTankIds: List<Long> = emptyList()
        var deletedManualTaskId: Long? = null
        var addedManualInput: ManualCareTaskInput? = null
        var updatedManualTaskId: Long? = null
        var updatedManualInput: ManualCareTaskInput? = null
        var completedActivityInput: CompletedCareActivityInput? = null

        override fun task(taskId: Long): Flow<CareTaskSnapshot?> = flowOf(null)

        override suspend fun syncSmartCareTasks(tanks: List<AquariumTankSnapshot>) {
            syncSmartCareCalls += 1
            lastSyncedTankIds = tanks.map(AquariumTankSnapshot::id)
        }

        override suspend fun completeTask(taskId: Long) = Unit

        override suspend fun deleteTask(taskId: Long) = Unit

        override suspend fun updateCompletedTaskDate(
            taskId: Long,
            completedAtMillis: Long
        ) = Unit

        override suspend fun addCompletedActivity(input: CompletedCareActivityInput) {
            completedActivityInput = input
        }

        override suspend fun deleteManualTask(taskId: Long) {
            deletedManualTaskId = taskId
        }

        override suspend fun addManualTask(input: ManualCareTaskInput) {
            addedManualInput = input
        }

        override suspend fun updateManualTask(
            taskId: Long,
            input: ManualCareTaskInput
        ) {
            updatedManualTaskId = taskId
            updatedManualInput = input
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

    private fun tank(id: Long, name: String): AquariumTankSnapshot = AquariumTankSnapshot(
        id = id,
        name = name,
        description = "",
        photoUri = null,
        setupDateMillis = null,
        widthCm = 10,
        lengthCm = 10,
        heightCm = 10,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "",
        tankStyle = "",
        createdAtMillis = 1L,
        smartCareEnabled = true,
        careRemindersEnabled = true,
        plants = emptyList(),
        materials = emptyList(),
        livestock = emptyList()
    )

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

package com.aqua.aqualight.ui.tabs.maintenance

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.application.care.CompletedCareActivityInput
import com.aqua.aqualight.application.care.MaintenanceOperations
import com.aqua.aqualight.application.care.ManualCareTaskInput
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTextPresentation
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypePresentation
import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceLiveLocaleTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `manual application copy relocalizes while user text remains unchanged`() = runTest {
        val operations = FakeOperations()
        val resolver = FakeResolver()
        val viewModel = MaintenanceViewModel(operations, resolver)
        viewModel.setTanks(listOf(tank()))
        operations.tasks.value = listOf(
            task(
                id = 1L,
                type = CareTaskType.GLASS_CLEANING,
                title = "Persisted Turkish title",
                description = "Persisted Turkish description",
                note = "User note"
            ),
            task(
                id = 2L,
                type = CareTaskType.CUSTOM,
                title = "User custom title",
                description = "Persisted custom description",
                note = "Custom user note"
            )
        )

        val english = viewModel.taskItems.first { it.size == 2 }
        assertEquals("en-GLASS_CLEANING", english[0].title)
        assertEquals("en-description-GLASS_CLEANING", english[0].description)
        assertEquals("User note", english[0].note)
        assertEquals("User custom title", english[1].title)
        assertEquals("en-description-CUSTOM", english[1].description)
        assertEquals("Custom user note", english[1].note)

        resolver.select("tr")

        val turkish = viewModel.taskItems.first {
            it.firstOrNull()?.title == "tr-GLASS_CLEANING"
        }
        assertEquals("tr-description-GLASS_CLEANING", turkish[0].description)
        assertEquals("User note", turkish[0].note)
        assertEquals("User custom title", turkish[1].title)
        assertEquals("tr-description-CUSTOM", turkish[1].description)
        assertEquals("Custom user note", turkish[1].note)
    }

    @Test
    fun `automatic title and description relocalize from semantic rule identity`() = runTest {
        val operations = FakeOperations()
        val resolver = FakeResolver()
        val viewModel = MaintenanceViewModel(operations, resolver)
        viewModel.setTanks(listOf(tank()))
        operations.tasks.value = listOf(
            task(
                id = 3L,
                type = CareTaskType.CUSTOM,
                source = CareTaskSource.AUTOMATIC,
                title = "Persisted title",
                description = "Persisted description",
                generatedRuleKey = "smart_11_startup_day_1_general_check_1"
            )
        )

        val english = viewModel.taskItems.first { it.isNotEmpty() }
        assertEquals("en-auto-title", english.single().title)
        assertEquals("en-auto-description", english.single().description)

        resolver.select("tr")

        val turkish = viewModel.taskItems.first {
            it.singleOrNull()?.title == "tr-auto-title"
        }
        assertEquals("tr-auto-description", turkish.single().description)
    }

    private class FakeOperations : MaintenanceOperations {
        override val tasks = MutableStateFlow<List<CareTaskSnapshot>>(emptyList())

        override fun task(taskId: Long): Flow<CareTaskSnapshot?> =
            tasks.map { list -> list.firstOrNull { it.id == taskId } }

        override suspend fun syncSmartCareTasks(tanks: List<AquariumTankSnapshot>) = Unit
        override suspend fun completeTask(taskId: Long) = Unit
        override suspend fun deleteTask(taskId: Long) = Unit
        override suspend fun updateCompletedTaskDate(taskId: Long, completedAtMillis: Long) = Unit
        override suspend fun addCompletedActivity(input: CompletedCareActivityInput) = Unit
        override suspend fun deleteManualTask(taskId: Long) = Unit
        override suspend fun addManualTask(input: ManualCareTaskInput) = Unit
        override suspend fun updateManualTask(taskId: Long, input: ManualCareTaskInput) = Unit
    }

    private class FakeResolver : MaintenanceTextResolver {
        private val language = MutableStateFlow("en")
        override val localeChanges: Flow<String> = language

        fun select(code: String) {
            language.value = code
        }

        override fun typePresentation(type: CareTaskType) = CareTaskTypePresentation(
            title = "${language.value}-${type.name}",
            defaultDescription = "${language.value}-description-${type.name}",
            iconRes = 0,
            accentColor = 0
        )

        override fun automaticTaskPresentation(
            task: CareTaskSnapshot,
            tank: AquariumTankSnapshot?
        ) = CareTaskTextPresentation(
            title = "${language.value}-auto-title",
            description = "${language.value}-auto-description"
        )

        override fun waterChangeTitle(typeTitle: String, percent: Int) = "$typeTitle $percent"
        override fun sourceLabel(source: CareTaskSource) = source.name
        override fun formatTime(timeMillis: Long) = timeMillis.toString()
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

    private fun task(
        id: Long,
        type: CareTaskType,
        source: CareTaskSource = CareTaskSource.MANUAL,
        title: String,
        description: String,
        note: String = "",
        generatedRuleKey: String = ""
    ) = CareTaskSnapshot(
        id = id,
        tankId = 11L,
        title = title,
        description = description,
        type = type,
        source = source,
        status = CareTaskStatus.PENDING,
        dueAtMillis = 1_000L,
        completedAtMillis = null,
        repeatEnabled = false,
        repeatIntervalDays = 1,
        reminderEnabled = true,
        missedReminderEnabled = false,
        missedReminderDays = 1,
        waterChangePercent = null,
        note = note,
        createdAtMillis = 1L,
        generatedRuleKey = generatedRuleKey
    )

    private fun tank() = AquariumTankSnapshot(
        id = 11L,
        name = "User tank",
        description = "",
        photoUri = null,
        setupDateEpochDay = null,
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
}

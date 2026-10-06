package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationTrigger
import com.aqua.aqualight.application.aquarium.health.LivestockHealthOperations
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class LivestockHealthViewModelBoundaryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observationReadDelegatesToApplicationBoundary() = runTest {
        val operations = FakeLivestockHealthOperations()
        val viewModel = LivestockHealthViewModel(operations, flowOf(emptyList()))
        val observer = Observer<List<LivestockObservationSnapshot>> { }

        val liveData = viewModel.observationsForTank(42L)
        try {
            liveData.observeForever(observer)
            advanceUntilIdle()

            assertEquals(42L, operations.observedTankId)
        } finally {
            liveData.removeObserver(observer)
        }
    }

    @Test
    fun lastWaterChangeUsesLatestCompletedTaskForSelectedTank() = runTest {
        val operations = FakeLivestockHealthOperations()
        val viewModel = LivestockHealthViewModel(
            operations = operations,
            careTasks = flowOf(
                listOf(
                    waterChangeTask(id = 1L, tankId = 42L, completedAtMillis = 1_700L),
                    waterChangeTask(id = 2L, tankId = 42L, completedAtMillis = 1_900L),
                    waterChangeTask(id = 3L, tankId = 7L, completedAtMillis = 2_100L)
                )
            )
        )
        val observer = Observer<Long?> { }

        val liveData = viewModel.lastCompletedWaterChangeForTank(42L)
        try {
            liveData.observeForever(observer)
            advanceUntilIdle()

            assertEquals(1_900L, liveData.value)
        } finally {
            liveData.removeObserver(observer)
        }
    }

    @Test
    fun createCheckAndCloseDelegateTypedApplicationInputs() = runTest {
        val operations = FakeLivestockHealthOperations()
        val viewModel = LivestockHealthViewModel(operations, flowOf(emptyList()))
        val observation = LivestockObservationInput(
            requestId = "observation-request",
            tankId = 42L,
            livestockId = 7L,
            symptomKeys = listOf("surface"),
            onsetKey = "today",
            otherObservation = "",
            note = "note",
            photoUris = listOf("content://photo"),
            affectedCount = 1
        )
        val evaluation = LivestockEvaluationInput(
            requestId = "evaluation-request",
            trigger = LivestockEvaluationTrigger.INITIAL_OBSERVATION,
            waterAnalysis = null
        )
        val refresh = evaluation.copy(
            requestId = "evaluation-refresh",
            trigger = LivestockEvaluationTrigger.USER_REFRESH
        )
        val check = LivestockCheckInput(
            requestId = "check-request",
            status = "same",
            affectedCount = 1,
            checkedAtMillis = 1_700_000_000_000L,
            note = "follow-up",
            photoUris = emptyList()
        )

        assertEquals(91L, viewModel.create(observation, evaluation))
        viewModel.addEvaluation(42L, 91L, refresh)
        viewModel.addCheck(42L, 91L, check)
        viewModel.close(42L, 91L, "manual")

        assertEquals(observation to evaluation, operations.created)
        assertEquals(Triple(42L, 91L, refresh), operations.evaluated)
        assertEquals(Triple(42L, 91L, check), operations.checked)
        assertEquals(Triple(42L, 91L, "manual"), operations.closed)
    }

    private fun waterChangeTask(
        id: Long,
        tankId: Long,
        completedAtMillis: Long
    ) = CareTaskSnapshot(
        id = id,
        tankId = tankId,
        title = "water change",
        description = "",
        type = CareTaskType.WATER_CHANGE,
        source = CareTaskSource.MANUAL,
        status = CareTaskStatus.COMPLETED,
        dueAtMillis = completedAtMillis,
        completedAtMillis = completedAtMillis,
        repeatEnabled = false,
        repeatIntervalDays = 1,
        reminderEnabled = false,
        missedReminderEnabled = false,
        missedReminderDays = 1,
        waterChangePercent = null,
        note = "",
        createdAtMillis = completedAtMillis
    )

    private class FakeLivestockHealthOperations : LivestockHealthOperations {
        var observedTankId: Long? = null
        var created: Pair<LivestockObservationInput, LivestockEvaluationInput>? = null
        var evaluated: Triple<Long, Long, LivestockEvaluationInput>? = null
        var checked: Triple<Long, Long, LivestockCheckInput>? = null
        var closed: Triple<Long, Long, String>? = null

        override fun observationsForTank(
            tankId: Long
        ): Flow<List<LivestockObservationSnapshot>> {
            observedTankId = tankId
            return flowOf(emptyList())
        }

        override suspend fun createObservation(
            input: LivestockObservationInput,
            evaluation: LivestockEvaluationInput
        ): Long {
            created = input to evaluation
            return 91L
        }

        override suspend fun addEvaluation(
            tankId: Long,
            observationId: Long,
            input: LivestockEvaluationInput
        ) {
            evaluated = Triple(tankId, observationId, input)
        }

        override suspend fun addCheck(
            tankId: Long,
            observationId: Long,
            input: LivestockCheckInput
        ) {
            checked = Triple(tankId, observationId, input)
        }

        override suspend fun closeObservation(
            tankId: Long,
            observationId: Long,
            reason: String
        ) {
            closed = Triple(tankId, observationId, reason)
        }
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

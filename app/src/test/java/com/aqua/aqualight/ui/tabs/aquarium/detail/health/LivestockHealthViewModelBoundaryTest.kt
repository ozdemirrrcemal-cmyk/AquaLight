package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthOperations
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
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
        val viewModel = LivestockHealthViewModel(operations)
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
    fun createCheckAndCloseDelegateTypedApplicationInputs() = runTest {
        val operations = FakeLivestockHealthOperations()
        val viewModel = LivestockHealthViewModel(operations)
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
        val check = LivestockCheckInput(
            requestId = "check-request",
            status = "same",
            affectedCount = 1,
            checkedAtMillis = 1_700_000_000_000L,
            note = "follow-up",
            photoUri = null
        )

        assertEquals(91L, viewModel.create(observation))
        viewModel.addCheck(42L, 91L, check)
        viewModel.close(42L, 91L, "manual")

        assertEquals(observation, operations.created)
        assertEquals(Triple(42L, 91L, check), operations.checked)
        assertEquals(Triple(42L, 91L, "manual"), operations.closed)
    }

    private class FakeLivestockHealthOperations : LivestockHealthOperations {
        var observedTankId: Long? = null
        var created: LivestockObservationInput? = null
        var checked: Triple<Long, Long, LivestockCheckInput>? = null
        var closed: Triple<Long, Long, String>? = null

        override fun observationsForTank(
            tankId: Long
        ): Flow<List<LivestockObservationSnapshot>> {
            observedTankId = tankId
            return flowOf(emptyList())
        }

        override suspend fun createObservation(input: LivestockObservationInput): Long {
            created = input
            return 91L
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

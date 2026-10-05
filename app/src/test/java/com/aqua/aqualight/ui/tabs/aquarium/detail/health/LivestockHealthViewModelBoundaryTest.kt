package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthOperations
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LivestockHealthViewModelBoundaryTest {

    @Test
    fun observationReadDelegatesToApplicationBoundary() {
        val operations = FakeLivestockHealthOperations()
        val viewModel = LivestockHealthViewModel(operations)

        viewModel.observationsForTank(42L)

        assertEquals(42L, operations.observedTankId)
    }

    @Test
    fun createCheckAndCloseDelegateTypedApplicationInputs() = runBlocking {
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
}

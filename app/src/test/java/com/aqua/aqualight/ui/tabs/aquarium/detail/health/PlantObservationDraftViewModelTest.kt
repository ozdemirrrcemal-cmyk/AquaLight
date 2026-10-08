package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.aquarium.health.PlantHealthOperations
import com.aqua.aqualight.application.aquarium.health.PlantObservationInput
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class PlantObservationDraftViewModelTest {
    @get:Rule val main = PlantDraftMainDispatcherRule()

    @Test fun repeatedPressWhileSavingCreatesOneRequest() = runTest {
        val operations = DraftOperations().apply { gate = CompletableDeferred() }
        val draft = PlantObservationDraftViewModel(operations, SavedStateHandle())
        draft.toggle("healthy")
        draft.submit(1L, 2L)
        draft.submit(1L, 2L)
        assertEquals(1, operations.inputs.size)
        operations.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(PlantSaveState.Saved(71L), draft.save.value)
        draft.submit(1L, 2L)
        assertEquals(1, operations.inputs.size)
    }

    @Test fun uncertainSaveSurvivesProcessRecreationWithAnImmutablePayload() = runTest {
        val operations = DraftOperations().apply { fail = true; requestExists = true }
        val state = SavedStateHandle()
        val initial = PlantObservationDraftViewModel(operations, state)
        initial.toggle("algae")
        initial.updateNote("Observed on older leaves")
        initial.replacePhoto(0, "photo-a")
        initial.submit(1L, 2L)
        advanceUntilIdle()
        val restored = PlantObservationDraftViewModel(operations, restoredHandle(state))
        assertEquals(PlantSaveState.Failed, restored.save.value)
        assertTrue(restored.locked)
        restored.updateNote("Changed")
        restored.toggle("healthy")
        operations.fail = false
        restored.submit(1L, 2L)
        advanceUntilIdle()
        assertEquals(operations.inputs.first(), operations.inputs.last())
        assertEquals(PlantSaveState.Saved(71L), restored.save.value)
    }

    @Test fun confirmedRejectionAllowsEditingButAnUnreadableStoreKeepsTheRetryFixed() = runTest {
        val operations = DraftOperations().apply { fail = true; requestExists = false }
        val draft = PlantObservationDraftViewModel(operations, SavedStateHandle())
        draft.toggle("healthy")
        draft.submit(1L, 2L)
        advanceUntilIdle()
        assertFalse(draft.locked)
        draft.updateNote("Edited after rejection")
        operations.requestExists = null
        draft.submit(1L, 2L)
        advanceUntilIdle()
        assertTrue(draft.locked)
        draft.updateNote("Must not change")
        assertEquals("Edited after rejection", draft.note)
    }

    @Test fun savedRequestRestorationDoesNotCreateAnotherObservation() = runTest {
        val operations = DraftOperations()
        val state = SavedStateHandle()
        val initial = PlantObservationDraftViewModel(operations, state)
        initial.toggle("healthy")
        initial.submit(1L, 2L)
        advanceUntilIdle()
        val restored = PlantObservationDraftViewModel(operations, restoredHandle(state))
        assertEquals(PlantSaveState.Saved(71L), restored.save.value)
        restored.submit(1L, 2L)
        assertEquals(1, operations.inputs.size)
    }

    @Test fun photoReplacementAndRemovalDiscardOnlySupersededCandidates() = runTest {
        val operations = DraftOperations()
        val draft = PlantObservationDraftViewModel(operations, SavedStateHandle())
        draft.replacePhoto(0, "photo-a")
        draft.replacePhoto(1, "photo-b")
        draft.replacePhoto(0, "photo-c")
        draft.replacePhoto(1, null)
        assertEquals(listOf("photo-a", "photo-b"), operations.discarded)
        assertEquals(listOf("photo-c", "", ""), draft.photos)
    }

    private fun restoredHandle(state: SavedStateHandle) =
        SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })

    private class DraftOperations : PlantHealthOperations {
        val inputs = mutableListOf<PlantObservationInput>()
        val discarded = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        var requestExists: Boolean? = false
        override fun observationsForPlant(tankId: Long, plantId: Long): Flow<List<PlantObservationSnapshot>> =
            flowOf(emptyList())
        override suspend fun createObservation(input: PlantObservationInput): Long {
            inputs.add(input)
            gate?.await()
            if (fail) throw IOException("Simulated write failure")
            return 71L
        }
        override suspend fun containsRequest(requestId: String) =
            requestExists ?: throw IOException("Store unavailable")
        override suspend fun deleteObservation(tankId: Long, plantId: Long, observationId: Long) = Unit
        override suspend fun discardDraftPhotos(photoUris: List<String>) { discarded.addAll(photoUris) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlantDraftMainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

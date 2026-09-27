package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisOperations
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterHistoryCursor
import com.aqua.aqualight.application.aquarium.health.WaterHistoryPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaterAnalysisMutationControllerTest {
    @Test
    fun `double save and delete cannot pass a pending commit and completion awaits consumption`() = runTest {
        val fake = MutationOperations()
        val controller = WaterAnalysisMutationController(fake, backgroundScope)
        val input = input()
        controller.save(input)
        controller.save(input)
        controller.delete(7, 2)
        runCurrent()
        assertEquals(1, fake.saves)
        assertEquals(0, fake.deletes)
        assertEquals(WaterAnalysisMutationState.Running, controller.state.value)
        fake.commit.complete(Unit)
        runCurrent()
        val completed = controller.state.value
        assertEquals(WaterAnalysisMutationState.Saved(12), completed)
        controller.save(input)
        runCurrent()
        assertEquals(1, fake.saves)
        controller.consume(completed)
        assertEquals(WaterAnalysisMutationState.Idle, controller.state.value)
    }

    @Test
    fun `retry retains the same request and failure is not success`() = runTest {
        val fake = MutationOperations().apply { failure = IllegalStateException("disk full") }
        val controller = WaterAnalysisMutationController(fake, backgroundScope)
        val input = input()
        controller.save(input)
        fake.commit.complete(Unit)
        runCurrent()
        val failure = WaterAnalysisMutationState.Failed(false)
        assertEquals(failure, controller.state.value)
        fake.failure = null
        controller.consume(failure)
        controller.save(input)
        runCurrent()
        assertEquals(listOf(input.requestId, input.requestId), fake.requests)
        assertEquals(WaterAnalysisMutationState.Saved(12), controller.state.value)
    }

    @Test
    fun `cancelled session is not converted into a mutation failure or success`() = runTest {
        val fake = MutationOperations().apply { failure = CancellationException("owner changed") }
        val controller = WaterAnalysisMutationController(fake, backgroundScope)
        controller.delete(7, 12)
        fake.commit.complete(Unit)
        runCurrent()
        assertEquals(WaterAnalysisMutationState.Idle, controller.state.value)
        assertFalse(controller.state.value is WaterAnalysisMutationState.Failed)
    }

    private fun input() = WaterAnalysisInput(7, 1_800_000_000_000, null, null, emptyList())

    @Test
    fun `delete retains both route identities until commit acknowledgement`() = runTest {
        val fake = MutationOperations()
        val controller = WaterAnalysisMutationController(fake, backgroundScope)
        controller.delete(7, 12)
        runCurrent()
        assertEquals(listOf(7L to 12L), fake.deletedKeys)
        assertEquals(WaterAnalysisMutationState.Running, controller.state.value)
        fake.commit.complete(Unit)
        runCurrent()
        assertEquals(WaterAnalysisMutationState.Deleted, controller.state.value)
    }
}

private class MutationOperations : WaterAnalysisOperations {
    val commit = CompletableDeferred<Unit>()
    var saves = 0
    var deletes = 0
    var failure: Exception? = null
    val requests = mutableListOf<String>()
    val deletedKeys = mutableListOf<Pair<Long, Long>>()
    override fun historyPage(tankId: Long, cursor: WaterHistoryCursor?, newer: Boolean) =
        flowOf(WaterHistoryPage(emptyList(), 0, null))
    override fun latestAnalysis(tankId: Long) = flowOf<WaterAnalysisSnapshot?>(null)
    override fun analysis(tankId: Long, analysisId: Long) = flowOf<WaterAnalysisSnapshot?>(null)
    override suspend fun saveAnalysis(input: WaterAnalysisInput): Long {
        saves++
        requests += input.requestId
        commit.await()
        failure?.let { throw it }
        return 12
    }
    override suspend fun deleteAnalysis(tankId: Long, analysisId: Long) {
        deletes++
        deletedKeys += tankId to analysisId
        commit.await()
        failure?.let { throw it }
    }
}

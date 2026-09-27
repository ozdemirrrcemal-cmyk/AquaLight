package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.observation.HealthObservation
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationOperations
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPage
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationQuery
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.observation.ObservationFollowUp
import com.aqua.aqualight.application.aquarium.health.observation.ObservationIdentity
import com.aqua.aqualight.application.aquarium.health.observation.ObservationNotes
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.application.aquarium.health.observation.PlantFinding
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthObservationMutationsTest {
    @Test
    fun `pending save freezes findings blocks repeats and remains until consumed`() = runTest {
        val operations = ObservationMutationFake()
        val mutations = HealthObservationMutations(operations, backgroundScope)
        val findings = mutableSetOf(PlantFinding.LEAF_DAMAGE)
        val input = input().copy(observation = HealthObservation.Plant(3, findings))
        mutations.save(input)
        findings.clear()
        mutations.save(input())
        mutations.delete(2, 7)
        runCurrent()
        assertEquals(1, operations.inputs.size)
        assertEquals(setOf(PlantFinding.LEAF_DAMAGE),
            (operations.inputs.single().observation as HealthObservation.Plant).findings)
        assertEquals(HealthMutationState.Running, mutations.state.value)
        assertEquals(emptyList<Pair<Long, Long>>(), operations.deletes)
        operations.acknowledged.complete(Unit)
        runCurrent()
        assertEquals(HealthMutationState.Saved(7), mutations.state.value)
        mutations.delete(2, 7)
        runCurrent()
        assertEquals(emptyList<Pair<Long, Long>>(), operations.deletes)
        mutations.consume(mutations.state.value)
        assertEquals(HealthMutationState.Idle, mutations.state.value)
    }

    @Test
    fun `failure retains original request identity on retry and never reports success`() = runTest {
        val operations = ObservationMutationFake().apply { failure = IllegalStateException("disk full") }
        val mutations = HealthObservationMutations(operations, backgroundScope)
        val input = input()
        operations.acknowledged.complete(Unit)
        mutations.save(input)
        runCurrent()
        assertEquals(HealthMutationState.Failed, mutations.state.value)
        mutations.consume(HealthMutationState.Failed)
        operations.failure = null
        mutations.save(input)
        runCurrent()
        assertEquals(listOf(input.identity.requestId, input.identity.requestId),
            operations.inputs.map { it.identity.requestId })
        assertEquals(HealthMutationState.Saved(7), mutations.state.value)
    }

    @Test
    fun `session cancellation is propagated with exact tank and record identity`() = runTest {
        val operations = ObservationMutationFake().apply { failure = CancellationException("stale owner generation") }
        val mutations = HealthObservationMutations(operations, backgroundScope)
        operations.acknowledged.complete(Unit)
        mutations.delete(2, 7)
        runCurrent()
        assertEquals(listOf(2L to 7L), operations.deletes)
        assertEquals(HealthMutationState.Idle, mutations.state.value)
    }

    private fun input() = HealthObservationInput(
        ObservationIdentity(2, 1_780_000_000_000, UUID.randomUUID().toString()),
        HealthObservation.Plant(3, setOf(PlantFinding.LEAF_DAMAGE)),
        ObservationNotes("", null, ObservationFollowUp(ObservationPhase.OBSERVATION, null)))
}

private class ObservationMutationFake : HealthObservationOperations {
    val acknowledged = CompletableDeferred<Unit>()
    val inputs = mutableListOf<HealthObservationInput>()
    val deletes = mutableListOf<Pair<Long, Long>>()
    var failure: Exception? = null
    override suspend fun prepare(tankId: Long, observedAtMillis: Long) = error("Unused in mutation test")
    override fun history(query: HealthObservationQuery) = flowOf(HealthObservationPage(emptyList(), 0, null))
    override fun observation(tankId: Long, observationId: Long) = flowOf<HealthObservationSnapshot?>(null)
    override suspend fun save(input: HealthObservationInput): Long {
        inputs += input
        acknowledged.await()
        failure?.let { throw it }
        return 7
    }
    override suspend fun delete(tankId: Long, observationId: Long) {
        deletes += tankId to observationId
        acknowledged.await()
        failure?.let { throw it }
    }
}

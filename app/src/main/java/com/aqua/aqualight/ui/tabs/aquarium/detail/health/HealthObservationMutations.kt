package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationOperations
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface HealthMutationState {
    data object Idle : HealthMutationState
    data object Running : HealthMutationState
    data class Saved(val id: Long) : HealthMutationState
    data object Deleted : HealthMutationState
    data object Failed : HealthMutationState
}

internal class HealthObservationMutations(
    private val operations: HealthObservationOperations,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow<HealthMutationState>(HealthMutationState.Idle)
    val state = mutable.asStateFlow()

    fun save(input: HealthObservationInput) {
        val frozen = input.copy(observation = HealthObservationPolicy.freeze(input.observation))
        start { HealthMutationState.Saved(operations.save(frozen)) }
    }

    fun delete(tank: Long, id: Long) = start {
        operations.delete(tank, id)
        HealthMutationState.Deleted
    }

    fun consume(value: HealthMutationState) {
        if (value != HealthMutationState.Running) mutable.compareAndSet(value, HealthMutationState.Idle)
    }

    private fun start(operation: suspend () -> HealthMutationState) {
        if (!mutable.compareAndSet(HealthMutationState.Idle, HealthMutationState.Running)) return
        scope.launch {
            try {
                mutable.value = operation()
            } catch (cancelled: CancellationException) {
                mutable.value = HealthMutationState.Idle
                throw cancelled
            } catch (_: Exception) {
                mutable.value = HealthMutationState.Failed
            }
        }
    }
}

package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisOperations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface WaterAnalysisMutationState {
    data object Idle : WaterAnalysisMutationState
    data object Running : WaterAnalysisMutationState
    data class Saved(val analysisId: Long) : WaterAnalysisMutationState
    data object Deleted : WaterAnalysisMutationState
    data class Failed(val deleting: Boolean) : WaterAnalysisMutationState
}

/** Survives view recreation; completion remains available until the route consumes it. */
internal class WaterAnalysisMutationController(
    private val operations: WaterAnalysisOperations,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<WaterAnalysisMutationState>(WaterAnalysisMutationState.Idle)
    val state = mutableState.asStateFlow()

    fun save(input: WaterAnalysisInput) {
        val frozen = input.copy(measurements = input.measurements.toList())
        start(deleting = false) { WaterAnalysisMutationState.Saved(operations.saveAnalysis(frozen)) }
    }

    fun delete(tankId: Long, analysisId: Long) = start(deleting = true) {
        operations.deleteAnalysis(tankId, analysisId)
        WaterAnalysisMutationState.Deleted
    }

    fun consume(outcome: WaterAnalysisMutationState) {
        if (outcome != WaterAnalysisMutationState.Running) {
            mutableState.compareAndSet(outcome, WaterAnalysisMutationState.Idle)
        }
    }

    private fun start(deleting: Boolean, operation: suspend () -> WaterAnalysisMutationState) {
        if (!mutableState.compareAndSet(WaterAnalysisMutationState.Idle, WaterAnalysisMutationState.Running)) return
        scope.launch {
            try {
                mutableState.value = operation()
            } catch (cancelled: CancellationException) {
                mutableState.value = WaterAnalysisMutationState.Idle
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = WaterAnalysisMutationState.Failed(deleting)
            }
        }
    }
}

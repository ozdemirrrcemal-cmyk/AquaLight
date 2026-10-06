package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthOperations
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LivestockHealthViewModel(
    private val operations: LivestockHealthOperations,
    private val careTasks: Flow<List<CareTaskSnapshot>>
) : ViewModel() {
    fun observationsForTank(tankId: Long) = operations.observationsForTank(tankId).asLiveData()

    fun lastCompletedWaterChangeForTank(tankId: Long) = careTasks.map { tasks ->
        tasks.asSequence()
            .filter { task ->
                task.tankId == tankId &&
                    task.status == CareTaskStatus.COMPLETED &&
                    task.type == CareTaskType.WATER_CHANGE
            }
            .maxByOrNull { task -> task.completedAtMillis ?: task.dueAtMillis }
            ?.let { task -> task.completedAtMillis ?: task.dueAtMillis }
    }.asLiveData()
    suspend fun create(
        input: LivestockObservationInput,
        evaluation: LivestockEvaluationInput
    ) = operations.createObservation(input, evaluation)

    suspend fun addEvaluation(
        tankId: Long,
        observationId: Long,
        input: LivestockEvaluationInput
    ) = operations.addEvaluation(tankId, observationId, input)

    suspend fun addCheck(tankId: Long, observationId: Long, input: LivestockCheckInput) =
        operations.addCheck(tankId, observationId, input)
    suspend fun close(tankId: Long, observationId: Long, reason: String) =
        operations.closeObservation(tankId, observationId, reason)
}

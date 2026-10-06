package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthContextOperations
import com.aqua.aqualight.application.aquarium.health.LivestockHealthOperations
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput

class LivestockHealthViewModel(
    private val operations: LivestockHealthOperations,
    private val contextOperations: LivestockHealthContextOperations
) : ViewModel() {
    fun observationsForTank(tankId: Long) = operations.observationsForTank(tankId).asLiveData()

    fun contextForTank(tankId: Long) = contextOperations.contextForTank(tankId).asLiveData()

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

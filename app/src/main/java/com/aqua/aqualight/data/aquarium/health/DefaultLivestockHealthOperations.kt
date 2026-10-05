package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthOperations
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.data.user.withCurrentOwnerScope

internal class DefaultLivestockHealthOperations(
    private val store: LivestockHealthDataStoreManager
) : LivestockHealthOperations {
    override fun observationsForTank(tankId: Long) = store.observationsForTank(tankId)
    override suspend fun createObservation(
        input: LivestockObservationInput,
        evaluation: LivestockEvaluationInput
    ) = withCurrentOwnerScope {
        store.create(input, evaluation)
    }

    override suspend fun addEvaluation(
        tankId: Long,
        observationId: Long,
        input: LivestockEvaluationInput
    ) = withCurrentOwnerScope {
        store.addEvaluation(tankId, observationId, input)
    }
    override suspend fun addCheck(tankId: Long, observationId: Long, input: LivestockCheckInput) =
        withCurrentOwnerScope { store.addCheck(tankId, observationId, input) }
    override suspend fun closeObservation(tankId: Long, observationId: Long, reason: String) =
        withCurrentOwnerScope { store.close(tankId, observationId, reason) }
}

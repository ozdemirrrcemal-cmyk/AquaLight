package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthOperations
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.data.user.withCurrentOwnerScope

internal class DefaultLivestockHealthOperations(
    private val store: LivestockHealthDataStoreManager
) : LivestockHealthOperations {
    override fun observationsForTank(tankId: Long) = store.observationsForTank(tankId)
    override suspend fun createObservation(input: LivestockObservationInput) =
        withCurrentOwnerScope { store.create(input) }
    override suspend fun addCheck(tankId: Long, observationId: Long, input: LivestockCheckInput) =
        withCurrentOwnerScope { store.addCheck(tankId, observationId, input) }
    override suspend fun closeObservation(tankId: Long, observationId: Long, reason: String) =
        withCurrentOwnerScope { store.close(tankId, observationId, reason) }
}

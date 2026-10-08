package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.PlantHealthOperations
import com.aqua.aqualight.application.aquarium.health.PlantObservationInput
import com.aqua.aqualight.data.user.UserDataScope

internal class DefaultPlantHealthOperations(
    private val store: PlantHealthDataStoreManager,
    private val ownerUid: String
) : PlantHealthOperations {
    override fun observationsForPlant(tankId: Long, plantId: Long) =
        store.observationsForPlant(tankId, plantId)

    override suspend fun createObservation(input: PlantObservationInput) =
        withActiveOwner { store.create(input) }

    override suspend fun containsRequest(requestId: String) =
        withActiveOwner { store.containsRequest(requestId) }

    override suspend fun deleteObservation(tankId: Long, plantId: Long, observationId: Long) =
        withActiveOwner { store.deleteObservation(tankId, plantId, observationId) }

    override suspend fun discardDraftPhotos(photoUris: List<String>) =
        UserDataScope.withOwnerUid(ownerUid) { store.discardDraftPhotos(photoUris) }

    private suspend fun <T> withActiveOwner(block: suspend () -> T): T {
        check(UserDataScope.requireCurrentUid() == ownerUid) { "Observation owner is no longer active." }
        return UserDataScope.withOwnerUid(ownerUid, block)
    }
}

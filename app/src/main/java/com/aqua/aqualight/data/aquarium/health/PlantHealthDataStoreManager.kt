package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.DataStore
import com.aqua.aqualight.application.aquarium.health.PlantObservationInput
import com.aqua.aqualight.application.aquarium.health.PlantObservationRules
import com.aqua.aqualight.application.aquarium.health.PlantObservationSnapshot
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class PlantHealthDataStoreManager(
    private val store: DataStore<PlantHealthStore>,
    private val plantsForOwner: suspend (String) -> Map<Long, Set<Long>>,
    private val media: PlantObservationMedia,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context, tanks: AquariumTankDataStoreManager) : this(
        context.applicationContext.plantHealthDataStore,
        { owner -> tanks.tanksSnapshotForOwner(owner).associate { tank ->
            tank.id to tank.plants.map { plant -> plant.id }.toSet()
        } },
        AppPlantObservationMedia(context)
    )

    fun observationsForPlant(tankId: Long, plantId: Long) = run {
        require(tankId > 0 && plantId > 0)
        val owner = UserDataScope.requireCurrentUid()
        store.data.map { current ->
            requirePlantObservationOwner(owner)
            validatePlantHealthStore(current).observationsList
                .filter { it.ownerUid == owner && it.tankId == tankId && it.plantId == plantId }
                .map { it.toSnapshot() }
                .sortedWith(compareByDescending<PlantObservationSnapshot> { it.createdAtMillis }
                    .thenByDescending { it.id })
        }
    }

    suspend fun create(input: PlantObservationInput): Long = withContext(NonCancellable + Dispatchers.IO) {
        PlantObservationRules.validate(input)
        val normalized = input.copy(note = input.note.trim())
        val owner = UserDataScope.requireCurrentUid()
        val present = plantsForOwner(owner)[input.tankId]?.contains(input.plantId) == true
        var id = 0L
        store.updateData { current ->
            validatePlantHealthStore(current)
            requirePlantObservationOwner(owner)
            val existing = current.observationsList.firstOrNull {
                it.ownerUid == owner && it.requestId == input.requestId
            }
            if (existing != null) {
                require(existing.toInput() == normalized) { "Observation retry changed its payload." }
                id = existing.id
                return@updateData current
            }
            check(present) { "The selected tank plant no longer exists." }
            input.photoUris.forEach { media.requirePendingOwner(it, owner) }
            val maxId = current.observationsList.maxOfOrNull { it.id } ?: 0L
            check(maxId < Long.MAX_VALUE)
            val timestamp = now().also { check(it > 0) }
            id = maxOf(timestamp, maxId + 1)
            val record = StoredPlantObservation.newBuilder()
                .setId(id).setRequestId(input.requestId).setOwnerUid(owner)
                .setTankId(input.tankId).setPlantId(input.plantId)
                .addAllSymptomKeys(input.symptomKeys).setNote(normalized.note)
                .addAllPhotoUris(input.photoUris).setCreatedAtMillis(timestamp).build()
            validatePlantHealthStore(current.toBuilder().addObservations(record).build())
        }
        input.photoUris.forEach { media.commit(it) }
        if (plantsForOwner(owner)[input.tankId]?.contains(input.plantId) != true) {
            deleteObservation(input.tankId, input.plantId, id)
            error("The selected tank plant was removed while saving.")
        }
        id
    }

    suspend fun deleteObservation(tankId: Long, plantId: Long, observationId: Long) {
        require(tankId > 0 && plantId > 0 && observationId > 0)
        remove(UserDataScope.requireCurrentUid()) {
            it.tankId == tankId && it.plantId == plantId && it.id == observationId
        }
    }

    suspend fun deleteForTank(tankId: Long) {
        require(tankId > 0)
        remove(UserDataScope.requireCurrentUid()) { it.tankId == tankId }
    }

    suspend fun reconcileTankPlants(tankId: Long) {
        require(tankId > 0)
        val owner = UserDataScope.requireCurrentUid()
        val assigned = plantsForOwner(owner)[tankId].orEmpty()
        remove(owner) { it.tankId == tankId && it.plantId !in assigned }
    }

    suspend fun clearAllForOwner(ownerUid: String) = remove(ownerUid) { true }

    suspend fun containsRequest(requestId: String): Boolean {
        require(requestId.isNotBlank())
        val owner = UserDataScope.requireCurrentUid()
        val current = validatePlantHealthStore(store.data.first())
        requirePlantObservationOwner(owner)
        return current.observationsList.any { it.ownerUid == owner && it.requestId == requestId }
    }

    suspend fun discardDraftPhotos(photoUris: List<String>) = withContext(NonCancellable + Dispatchers.IO) {
        val owner = UserDataScope.requireCurrentUid()
        val referenced = store.data.first().observationsList.flatMap { it.photoUrisList }.toSet()
        photoUris.distinct().filterNot { it in referenced }.forEach { media.rollbackDraft(it, owner) }
    }

    suspend fun reconcileAndGetMediaUrisForOwner(ownerUid: String): Set<String> {
        val assigned = plantsForOwner(ownerUid)
        remove(ownerUid) { assigned[it.tankId]?.contains(it.plantId) != true }
        return validatePlantHealthStore(store.data.first()).observationsList
            .filter { it.ownerUid == ownerUid }.flatMap { it.photoUrisList }.toSet()
    }

    private suspend fun remove(
        ownerUid: String,
        select: (StoredPlantObservation) -> Boolean
    ) = withContext(NonCancellable + Dispatchers.IO) {
        require(ownerUid.isNotBlank())
        val deleted = linkedSetOf<String>()
        store.updateData { current ->
            validatePlantHealthStore(current)
            val removed = current.observationsList.filter { it.ownerUid == ownerUid && select(it) }
            val retained = current.observationsList - removed.toSet()
            val referenced = retained.flatMap { it.photoUrisList }.toSet()
            val photos = removed.flatMap { it.photoUrisList }.filterNot { it in referenced }
            photos.forEach { media.prepareDeletion(it, ownerUid) }
            deleted.addAll(photos)
            validatePlantHealthStore(current.toBuilder().clearObservations()
                .addAllObservations(retained).build())
        }
        val referenced = store.data.first().observationsList.flatMap { it.photoUrisList }.toSet()
        deleted.filterNot { it in referenced }.forEach { media.delete(it, ownerUid) }
    }

}

private fun requirePlantObservationOwner(expected: String) {
    check(UserDataScope.requireCurrentUid() == expected) { "Observation owner changed." }
}

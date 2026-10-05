package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.aqua.aqualight.platform.media.CommittedMediaDeletionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class LivestockHealthDataStoreManager(
    context: Context,
    private val tanks: AquariumTankDataStoreManager
) {
    private val appContext = context.applicationContext

    suspend fun reconcileAndGetMediaUrisForOwner(ownerUid: String): Set<String> {
        require(ownerUid.isNotBlank())
        removeObservations(appContext, ownerUid, false) { records ->
            val livestockByTank = tanks.tanksSnapshotForOwner(ownerUid).associate { tank ->
                tank.id to tank.livestock.map { it.id }.toSet()
            }
            records.filter { record ->
                livestockByTank[record.tankId]?.contains(record.livestockId) != true
            }
        }
        return appContext.livestockHealthDataStore.data.map { store ->
            validateLivestockHealthStore(store).observationsList
                .filter { it.ownerUid == ownerUid }
                .flatMap(StoredLivestockObservation::mediaUris)
                .toSet()
        }.first()
    }

    fun observationsForTank(tankId: Long): Flow<List<LivestockObservationSnapshot>> {
        require(tankId > 0)
        val ownerUid = UserDataScope.requireCurrentUid()
        return appContext.livestockHealthDataStore.data.map { store ->
            checkOwner(ownerUid)
            validateLivestockHealthStore(store).observationsList
                .filter { it.ownerUid == ownerUid && it.tankId == tankId }
                .map(StoredLivestockObservation::toSnapshot)
                .sortedWith(compareByDescending<LivestockObservationSnapshot> { it.createdAtMillis }
                    .thenByDescending { it.id })
        }
    }

    suspend fun create(
        input: LivestockObservationInput,
        evaluation: LivestockEvaluationInput
    ): Long = withContext(NonCancellable + Dispatchers.IO) {
        validateInput(input)
        val ownerUid = UserDataScope.requireCurrentUid()
        val selectedQuantity = tanks.tanksSnapshotForOwner(ownerUid)
            .firstOrNull { tank -> tank.id == input.tankId }
            ?.livestock
            ?.firstOrNull { livestock -> livestock.id == input.livestockId }
            ?.quantity
        val id = createOrReuseLivestockObservation(
            appContext = appContext,
            ownerUid = ownerUid,
            selectedQuantity = selectedQuantity,
            input = input,
            evaluation = evaluation,
            requireOwner = { checkOwner(ownerUid) }
        )
        input.photoUris.forEach { uri ->
            AppMediaStorage.commitPendingMedia(appContext, uri)
        }
        ensureCreatedLivestockPresent(ownerUid, input, id)
        id
    }

    suspend fun addCheck(tankId: Long, observationId: Long, input: LivestockCheckInput) =
        withContext(NonCancellable + Dispatchers.IO) {
            require(tankId > 0 && observationId > 0)
            validateCheck(input)
            val ownerUid = UserDataScope.requireCurrentUid()
            mutateRecord(tankId, observationId) { record ->
                val existing = record.checksList.firstOrNull { it.requestId == input.requestId }
                if (existing != null) {
                    require(existing.status == input.status &&
                        existing.affectedCount == input.affectedCount &&
                        existing.checkedAtMillis == input.checkedAtMillis &&
                        existing.note == input.note.trim() &&
                        existing.photoUrisList == input.photoUris
                    ) { "Check request was already used for different data." }
                    return@mutateRecord record
                }
                require(record.closedAtMillis == 0L) { "Follow-up already closed." }
                require(input.photoUris.all { uri ->
                    AppMediaStorage.pendingMediaOwner(
                        appContext,
                        uri,
                        AppMediaScope.LIVESTOCK
                    ) == ownerUid
                }) { "Check photos must belong to the active owner." }
                require(input.checkedAtMillis >= record.createdAtMillis) {
                    "Check cannot predate the observation."
                }
                require(input.affectedCount <= record.totalCount)
                val updated = record.toBuilder().addChecks(
                    StoredLivestockCheck.newBuilder()
                        .setRequestId(input.requestId)
                        .setStatus(input.status)
                        .setAffectedCount(input.affectedCount)
                        .setCheckedAtMillis(input.checkedAtMillis)
                        .setNote(input.note.trim())
                        .addAllPhotoUris(input.photoUris)
                        .build()
                )
                if (input.status == "recovered") {
                    updated.setClosedAtMillis(maxOf(System.currentTimeMillis(), input.checkedAtMillis))
                        .setCloseReason("recovered")
                }
                updated.build()
            }
            input.photoUris.forEach { uri ->
                AppMediaStorage.commitPendingMedia(appContext, uri)
            }
        }

    suspend fun close(tankId: Long, observationId: Long, reason: String) {
        require(reason == "manual" || reason == "recovered")
        mutateRecord(tankId, observationId) { record ->
            if (record.closedAtMillis != 0L) return@mutateRecord record
            record.toBuilder()
                .setClosedAtMillis(maxOf(
                    System.currentTimeMillis(),
                    record.checksList.maxOfOrNull { it.checkedAtMillis }
                        ?: record.createdAtMillis
                ))
                .setCloseReason(reason)
                .build()
        }
    }

    private suspend fun ensureCreatedLivestockPresent(
        ownerUid: String,
        input: LivestockObservationInput,
        id: Long
    ) {
        val stillPresent = tanks.tanksSnapshotForOwner(ownerUid).any { tank ->
            tank.id == input.tankId && tank.livestock.any { it.id == input.livestockId }
        }
        if (!stillPresent) {
            removeObservations(appContext, ownerUid, true) { records ->
                records.filter { it.id == id && it.tankId == input.tankId }
            }
            throw StoreInvariantViolation("Selected livestock no longer exists in this tank.")
        }
    }

    suspend fun deleteForTank(tankId: Long) {
        require(tankId > 0)
        val ownerUid = UserDataScope.requireCurrentUid()
        removeObservations(appContext, ownerUid, true) { records ->
            records.filter { it.tankId == tankId }
        }
    }

    suspend fun deleteForLivestock(tankId: Long, livestockId: Long) {
        require(tankId > 0 && livestockId > 0)
        val ownerUid = UserDataScope.requireCurrentUid()
        removeObservations(appContext, ownerUid, true) { records ->
            records.filter { it.tankId == tankId && it.livestockId == livestockId }
        }
    }

    suspend fun clearAllForOwner(ownerUid: String) {
        require(ownerUid.isNotBlank())
        removeObservations(appContext, ownerUid, false) { records -> records }
    }

    internal suspend fun mutateRecord(
        tankId: Long,
        observationId: Long,
        update: (StoredLivestockObservation) -> StoredLivestockObservation
    ) {
        val ownerUid = UserDataScope.requireCurrentUid()
        appContext.livestockHealthDataStore.updateData { current ->
            checkOwner(ownerUid)
            val index = current.observationsList.indexOfFirst {
                it.id == observationId && it.tankId == tankId && it.ownerUid == ownerUid
            }
            if (index < 0) throw StoreInvariantViolation("Observation no longer exists.")
            validateLivestockHealthStore(current.toBuilder()
                .setObservations(index, update(current.getObservations(index))).build())
        }
    }

}

private suspend fun removeObservations(
    appContext: Context,
    ownerUid: String,
    requireActiveOwner: Boolean,
    select: suspend (List<StoredLivestockObservation>) -> List<StoredLivestockObservation>
) = withContext(NonCancellable + Dispatchers.IO) {
    val media = linkedSetOf<String>()
    appContext.livestockHealthDataStore.updateData { current ->
        if (requireActiveOwner) checkOwner(ownerUid)
        val selected = select(current.observationsList.filter { it.ownerUid == ownerUid })
        if (selected.isEmpty()) return@updateData current

        val removed = selected.toSet()
        val retained = current.observationsList.filterNot { it in removed }
        val retainedMedia = retained.flatMap(StoredLivestockObservation::mediaUris).toSet()
        val deletedMedia = selected.flatMap(StoredLivestockObservation::mediaUris)
            .filterNot { it in retainedMedia }.toSet()
        deletedMedia.forEach { uri ->
            AppMediaStorage.deleteAfterCommit(
                appContext, ownerUid, uri, CommittedMediaDeletionMode.PREPARE
            )
        }
        media.addAll(deletedMedia)
        validateLivestockHealthStore(current.toBuilder().clearObservations()
            .addAllObservations(retained).build())
    }
    val referenced = appContext.livestockHealthDataStore.data.first()
        .observationsList.flatMap(StoredLivestockObservation::mediaUris).toSet()
    media.filterNot { it in referenced }.forEach { uri ->
        AppMediaStorage.deleteAfterCommit(appContext, ownerUid, uri)
    }
}


private fun checkOwner(expected: String) {
    if (UserDataScope.requireCurrentUid() != expected) {
        throw StoreInvariantViolation("Active owner changed during livestock health operation.")
    }
}

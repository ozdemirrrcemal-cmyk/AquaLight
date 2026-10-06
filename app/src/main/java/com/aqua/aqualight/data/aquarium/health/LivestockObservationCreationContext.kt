package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationTrigger
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage

internal data class LivestockObservationCreationContext(
    val appContext: Context,
    val ownerUid: String,
    val selectedQuantity: Int?,
    val requireOwner: () -> Unit
)

internal suspend fun createOrReuseLivestockObservation(
    context: LivestockObservationCreationContext,
    input: LivestockObservationInput,
    evaluation: LivestockEvaluationInput
): Long {
    require(evaluation.trigger == LivestockEvaluationTrigger.INITIAL_OBSERVATION)
    var id = 0L
    context.appContext.livestockHealthDataStore.updateData { current ->
        context.requireOwner()
        val existing = current.observationsList.firstOrNull { record ->
            record.ownerUid == context.ownerUid && record.requestId == input.requestId
        }
        if (existing != null) {
            requireObservationRetryMatches(existing, input, evaluation)
            id = existing.id
            return@updateData current
        }

        val quantity = context.selectedQuantity
            ?: throw StoreInvariantViolation(
                "Selected livestock no longer exists in this tank."
            )
        require(input.affectedCount <= quantity)
        requireObservationPhotoOwnership(context, input.photoUris)

        val now = System.currentTimeMillis()
        val maxId = current.observationsList.maxOfOrNull { record -> record.id } ?: 0L
        check(maxId < Long.MAX_VALUE)
        id = maxOf(now, maxId + 1L)
        val observation = buildStoredObservation(
            id = id,
            ownerUid = context.ownerUid,
            totalCount = quantity,
            input = input,
            createdAtMillis = now
        )
        val storedEvaluation = buildStoredEvaluation(
            id = now,
            record = observation,
            input = evaluation,
            evaluatedAtMillis = now
        )
        validateLivestockHealthStore(
            current.toBuilder()
                .addObservations(
                    observation.toBuilder()
                        .addEvaluations(storedEvaluation)
                        .build()
                )
                .build()
        )
    }
    return id
}

private fun requireObservationRetryMatches(
    existing: StoredLivestockObservation,
    input: LivestockObservationInput,
    evaluation: LivestockEvaluationInput
) {
    require(
        existing.tankId == input.tankId &&
            existing.livestockId == input.livestockId &&
            existing.symptomKeysList == input.symptomKeys &&
            existing.onsetKey == input.onsetKey &&
            existing.affectedCount == input.affectedCount &&
            existing.otherObservation == input.otherObservation.trim() &&
            existing.note == input.note.trim() &&
            existing.photoUrisList == input.photoUris
    ) {
        "Observation request was already used for different data."
    }
    val storedEvaluation = existing.evaluationsList.firstOrNull { stored ->
        stored.requestId == evaluation.requestId
    }
    require(
        storedEvaluation != null &&
            storedEvaluation.matchesInput(existing, evaluation)
    ) {
        "Initial evaluation request was already used for different data."
    }
}

private fun requireObservationPhotoOwnership(
    context: LivestockObservationCreationContext,
    photoUris: List<String>
) {
    require(photoUris.all { uri ->
        AppMediaStorage.pendingMediaOwner(
            context.appContext,
            uri,
            AppMediaScope.LIVESTOCK
        ) == context.ownerUid
    }) {
        "Observation photos must belong to the active owner."
    }
}

private fun buildStoredObservation(
    id: Long,
    ownerUid: String,
    totalCount: Int,
    input: LivestockObservationInput,
    createdAtMillis: Long
): StoredLivestockObservation =
    StoredLivestockObservation.newBuilder()
        .setId(id)
        .setRequestId(input.requestId)
        .setOwnerUid(ownerUid)
        .setTankId(input.tankId)
        .setLivestockId(input.livestockId)
        .addAllSymptomKeys(input.symptomKeys)
        .setOnsetKey(input.onsetKey)
        .setOtherObservation(input.otherObservation.trim())
        .setNote(input.note.trim())
        .addAllPhotoUris(input.photoUris)
        .setAffectedCount(input.affectedCount)
        .setTotalCount(totalCount)
        .setCreatedAtMillis(createdAtMillis)
        .build()

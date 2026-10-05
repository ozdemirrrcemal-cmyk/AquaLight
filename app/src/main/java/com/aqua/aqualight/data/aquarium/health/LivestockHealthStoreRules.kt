package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.application.aquarium.health.LivestockCheckSnapshot
import com.aqua.aqualight.application.aquarium.health.LIVESTOCK_HEALTH_MAX_PHOTOS
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal fun StoredLivestockObservation.toSnapshot(): LivestockObservationSnapshot =
    LivestockObservationSnapshot(
        id = id, tankId = tankId, livestockId = livestockId,
        symptomKeys = symptomKeysList, onsetKey = onsetKey,
        otherObservation = otherObservation, note = note, photoUris = photoUrisList,
        affectedCount = affectedCount, totalCount = totalCount,
        createdAtMillis = createdAtMillis,
        closedAtMillis = closedAtMillis.takeIf { it > 0 },
        closeReason = closeReason.takeIf(String::isNotBlank),
        checks = checksList.map {
            LivestockCheckSnapshot(
                status = it.status, affectedCount = it.affectedCount,
                checkedAtMillis = it.checkedAtMillis, note = it.note,
                photoUris = it.photoUrisList
            )
        },
        evaluations = evaluationsList.map { evaluation ->
            evaluation.toSnapshot(tankId)
        }
    )

internal fun StoredLivestockObservation.mediaUris(): List<String> =
    photoUrisList + checksList.flatMap { check -> check.photoUrisList }

private const val MAX_REQUEST_ID_LENGTH = 64
private const val MAX_OWNER_UID_LENGTH = 128
private const val MAX_SYMPTOMS = 16
private const val MAX_KEY_LENGTH = 64
private const val MAX_OTHER_LENGTH = 2000
private const val MAX_NOTE_LENGTH = 4000
private const val MAX_URI_LENGTH = 2048
private const val MIN_RECORD_TIME_MILLIS = 946_684_800_000L
private const val MAX_RECORD_TIME_MILLIS = 4_102_444_800_000L
private const val CHECK_CLOCK_SKEW_MILLIS = 60_000L

internal fun validateInput(input: LivestockObservationInput) {
    require(input.requestId.length in 1..MAX_REQUEST_ID_LENGTH)
    require(input.tankId > 0 && input.livestockId > 0 && input.affectedCount > 0)
    require(input.symptomKeys.isNotEmpty() && input.symptomKeys.size <= MAX_SYMPTOMS)
    require(input.symptomKeys.distinct().size == input.symptomKeys.size)
    require(input.symptomKeys.all { it.isNotBlank() && it.length <= MAX_KEY_LENGTH })
    require(input.onsetKey.isNotBlank() && input.onsetKey.length <= MAX_KEY_LENGTH)
    require(input.otherObservation.length <= MAX_OTHER_LENGTH && input.note.length <= MAX_NOTE_LENGTH)
    require(input.photoUris.size <= LIVESTOCK_HEALTH_MAX_PHOTOS)
    require(input.photoUris.distinct().size == input.photoUris.size)
    require(input.photoUris.all { it.isNotBlank() && it.length <= MAX_URI_LENGTH })
    require("other" !in input.symptomKeys || input.otherObservation.isNotBlank())
}

internal fun validateCheck(input: LivestockCheckInput) {
    require(input.requestId.length in 1..MAX_REQUEST_ID_LENGTH)
    require(input.status in setOf("increased", "same", "decreased", "recovered"))
    require(input.affectedCount > 0)
    require(input.checkedAtMillis in MIN_RECORD_TIME_MILLIS..
        (System.currentTimeMillis() + CHECK_CLOCK_SKEW_MILLIS))
    require(input.note.length <= MAX_NOTE_LENGTH)
    require(input.photoUris.size <= LIVESTOCK_HEALTH_MAX_PHOTOS)
    require(input.photoUris.distinct().size == input.photoUris.size)
    require(input.photoUris.all { it.isNotBlank() && it.length <= MAX_URI_LENGTH })
}

internal fun validateLivestockHealthStore(store: LivestockHealthStore): LivestockHealthStore {
    CommercialStoreSchema.requireCurrent(
        "LivestockHealthStore", store.schemaVersion,
        CommercialStoreSchema.LIVESTOCK_HEALTH_VERSION
    )
    val ids = mutableSetOf<Pair<String, Long>>()
    val requestIds = mutableSetOf<Pair<String, String>>()
    store.observationsList.forEach { record ->
        validateStoredObservation(record)
        if (!ids.add(record.ownerUid to record.id)) invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
        if (!requestIds.add(record.ownerUid to record.requestId)) invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
        val checkRequestIds = mutableSetOf<String>()
        record.checksList.forEach { check ->
            validateStoredCheck(check, record)
            if (!checkRequestIds.add(check.requestId)) {
                invalidStoredHealthRecord(INVALID_CHECK_MESSAGE)
            }
        }
        val evaluationIds = mutableSetOf<Long>()
        val evaluationRequestIds = mutableSetOf<String>()
        record.evaluationsList.forEach { evaluation ->
            validateStoredEvaluation(record, evaluation)
            if (!evaluationIds.add(evaluation.id)) {
                invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
            }
            if (!evaluationRequestIds.add(evaluation.requestId)) {
                invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
            }
        }
    }
    return store
}

private fun validateStoredObservation(record: StoredLivestockObservation) {
    if (record.id <= 0 || record.tankId <= 0 || record.livestockId <= 0) {
        invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    }
    if (record.ownerUid.isBlank() || record.ownerUid.length > MAX_OWNER_UID_LENGTH ||
        record.ownerUid != record.ownerUid.trim()
    ) invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    if (record.createdAtMillis !in MIN_RECORD_TIME_MILLIS..MAX_RECORD_TIME_MILLIS) {
        invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    }
    if (record.totalCount <= 0 || record.affectedCount !in 1..record.totalCount) {
        invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    }
    if (record.evaluationsCount == 0) {
        invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    }
    val initialEvaluation = record.evaluationsList.minByOrNull {
        evaluation -> evaluation.evaluatedAtMillis
    } ?: invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    if (
        initialEvaluation.trigger != "INITIAL_OBSERVATION" ||
        initialEvaluation.basedOnCheckCount != 0
    ) {
        invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    }
    if ((record.closedAtMillis == 0L) != record.closeReason.isBlank()) {
        invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    }
    if (record.closedAtMillis != 0L) {
        if (record.closedAtMillis < record.createdAtMillis) invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
        if (record.closeReason !in setOf("manual", "recovered")) invalidStoredHealthRecord(INVALID_OBSERVATION_MESSAGE)
    }
    validateInput(LivestockObservationInput(
        record.requestId, record.tankId, record.livestockId, record.symptomKeysList,
        record.onsetKey, record.otherObservation, record.note,
        record.photoUrisList, record.affectedCount
    ))
}

private fun validateStoredCheck(check: StoredLivestockCheck, record: StoredLivestockObservation) {
    if (check.status !in setOf("increased", "same", "decreased", "recovered")) {
        invalidStoredHealthRecord(INVALID_CHECK_MESSAGE)
    }
    if (check.requestId.length !in 1..MAX_REQUEST_ID_LENGTH) invalidStoredHealthRecord(INVALID_CHECK_MESSAGE)
    if (check.affectedCount !in 1..record.totalCount) invalidStoredHealthRecord(INVALID_CHECK_MESSAGE)
    if (check.checkedAtMillis < record.createdAtMillis) invalidStoredHealthRecord(INVALID_CHECK_MESSAGE)
    if (check.note.length > MAX_NOTE_LENGTH ||
        check.photoUrisCount > LIVESTOCK_HEALTH_MAX_PHOTOS ||
        check.photoUrisList.distinct().size != check.photoUrisCount ||
        check.photoUrisList.any { it.isBlank() || it.length > MAX_URI_LENGTH }
    ) {
        invalidStoredHealthRecord(INVALID_CHECK_MESSAGE)
    }
}

private const val INVALID_OBSERVATION_MESSAGE = "Invalid livestock observation."
private const val INVALID_CHECK_MESSAGE = "Invalid livestock check."

private fun invalidStoredHealthRecord(message: String): Nothing =
    throw StoreInvariantViolation(message)

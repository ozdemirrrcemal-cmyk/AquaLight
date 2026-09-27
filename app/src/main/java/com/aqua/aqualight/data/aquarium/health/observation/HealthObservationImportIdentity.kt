package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import java.util.UUID

internal object HealthObservationImportIdentity {
    fun original(record: StoredHealthObservation): StoredHealthObservation {
        if (!record.hasOrigin()) return record
        val origin = record.origin
        val input = record.input.toBuilder().setTankId(origin.sourceTankId).setSubjectId(origin.sourceSubjectId)
            .setRequestId(origin.sourceRequestId).setPhotoUri(origin.sourcePhotoUri).clearPreviousObservationId()
        if (origin.hasSourcePreviousObservationId()) input.previousObservationId = origin.sourcePreviousObservationId
        return record.toBuilder().clearOrigin().setOwnerUid(origin.sourceOwnerUid).setId(origin.sourceObservationId)
            .setInput(input).build()
    }

    fun validate(record: StoredHealthObservation) {
        if (!record.hasOrigin()) return
        val origin = record.origin
        require(origin.sourceOwnerUid.isNotBlank() && origin.sourceOwnerUid == origin.sourceOwnerUid.trim())
        require(WaterAnalysisPolicy.isValidRequestId(origin.sourceRequestId))
        require(origin.sourceSubjectId == if (record.hasSubject()) record.subject.originalLocalId else 0L)
        require(record.input.subjectId == origin.sourceSubjectId)
        require(!origin.hasSourcePreviousObservationId() || origin.sourcePreviousObservationId > 0L)
        require(origin.sourceRecordSha256 == healthObservationSha256(original(record).toByteArray())) {
            "Imported health evidence has changed."
        }
    }

    fun key(record: StoredHealthObservation): Pair<String, Long> =
        if (record.hasOrigin()) record.origin.sourceOwnerUid to record.origin.sourceObservationId
        else record.ownerUid to record.id

    fun remap(record: StoredHealthObservation, target: HealthObservationImportTarget): StoredHealthObservation {
        HealthObservationRecordRules.validate(record)
        val original = original(record)
        val origin = StoredHealthObservationOrigin.newBuilder().setSourceOwnerUid(original.ownerUid)
            .setSourceObservationId(original.id).setSourceTankId(original.input.tankId)
            .setSourceSubjectId(original.input.subjectId).setSourceRequestId(original.input.requestId)
            .setSourcePhotoUri(original.input.photoUri)
            .setSourceRecordSha256(healthObservationSha256(original.toByteArray()))
            .setRestoreTransactionId(target.transactionId)
        if (original.input.hasPreviousObservationId()) {
            origin.sourcePreviousObservationId = original.input.previousObservationId
        }
        require(original.input.photoUri.isNotEmpty() == (target.photoUri != null))
        val input = record.input.toBuilder().setTankId(target.tankId).setRequestId(UUID.randomUUID().toString())
            .setPhotoUri(target.photoUri.orEmpty()).clearPreviousObservationId()
        target.previousId?.let { input.previousObservationId = it }
        return record.toBuilder().setOwnerUid(target.ownerUid).setId(target.id).setInput(input).setOrigin(origin)
            .build().also(HealthObservationRecordRules::validate)
    }

    fun sameEvidence(first: StoredHealthObservation, second: StoredHealthObservation) =
        original(first) == original(second)
}

/** Registered entity IDs are preserved by tank restore; original IDs remain in immutable provenance. */
internal data class HealthObservationImportTarget(
    val ownerUid: String,
    val tankId: Long,
    val id: Long,
    val transactionId: String,
    val photoUri: String?,
    val previousId: Long?
)

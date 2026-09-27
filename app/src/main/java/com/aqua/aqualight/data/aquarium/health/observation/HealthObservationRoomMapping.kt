package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationEntity
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import java.security.MessageDigest

internal fun healthObservationSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

internal fun StoredHealthObservation.toEntity(): HealthObservationEntity {
    HealthObservationRecordRules.validate(this)
    val bytes = toByteArray()
    return HealthObservationEntity(ownerUid, id, input.tankId, input.kind, input.subjectId,
        input.observedAtMillis, createdAtMillis, input.requestId, bytes, healthObservationSha256(bytes),
        input.photoUri.ifEmpty { null },
        if (hasOrigin()) origin.sourceOwnerUid else ownerUid,
        if (hasOrigin()) origin.sourceObservationId else id,
        if (hasOrigin()) origin.sourceRecordSha256 else healthObservationSha256(bytes),
        if (hasOrigin()) origin.restoreTransactionId else "", "", 0)
}

internal fun HealthObservationEntity.toStored(): StoredHealthObservation {
    require(payload.size in 1..MAX_OBSERVATION_BYTES)
    require(payloadSha256 == healthObservationSha256(payload))
    val stored = StoredHealthObservation.parseFrom(payload)
    HealthObservationRecordRules.validate(stored)
    val expected = stored.toEntity()
    require(ownerUid == expected.ownerUid && observationId == expected.observationId && tankId == expected.tankId)
    require(kind == expected.kind && subjectId == expected.subjectId && observedAtMillis == expected.observedAtMillis)
    require(createdAtMillis == expected.createdAtMillis && requestId == expected.requestId &&
        photoUri == expected.photoUri)
    require(sourceOwnerUid == expected.sourceOwnerUid && sourceObservationId == expected.sourceObservationId)
    require(sourceSha256 == expected.sourceSha256 && restoreTransactionId == expected.restoreTransactionId)
    require(deleteState in 0..2 && (deleteState == 0) == deleteTransactionId.isEmpty())
    require(deleteState == 0 || WaterAnalysisPolicy.isValidRequestId(deleteTransactionId))
    return stored
}

private const val MAX_OBSERVATION_BYTES = 2 * 1024 * 1024

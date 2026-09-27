package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationInput
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationRequestException
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationRequestFailure
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationRequest
import java.util.concurrent.Callable

/** Caller retains the session/tank gates until the transaction settles, including cancellation. */
internal class HealthObservationRoomCommit(private val database: HealthObservationDatabase) {
    private val dao = database.observations()

    fun replay(owner: String, input: HealthObservationInput): Long? {
        require(owner.isNotBlank() && owner == owner.trim())
        val request = dao.request(owner, input.identity.requestId) ?: return null
        if (request.payloadSha256 != fingerprint(input)) {
            throw HealthObservationRequestException(HealthObservationRequestFailure.PAYLOAD_CHANGED)
        }
        val existing = dao.record(owner, input.identity.tankId, request.observationId)
            ?: throw HealthObservationRequestException(HealthObservationRequestFailure.RECORD_REMOVED)
        existing.toStored()
        return request.observationId
    }

    fun create(owner: String, prepared: PreparedHealthObservation, nowMillis: Long,
        requireAuthority: () -> Unit): Long = database.runInTransaction(Callable {
        requireAuthority()
        replay(owner, prepared.input) ?: insert(owner, prepared, nowMillis)
    })

    fun delete(owner: String, tank: Long, id: Long, requireAuthority: () -> Unit) {
        require(owner.isNotBlank() && tank > 0 && id > 0)
        database.runInTransaction {
            requireAuthority()
            dao.record(owner, tank, id)?.toStored()
            dao.delete(owner, tank, id)
        }
    }

    private fun insert(owner: String, prepared: PreparedHealthObservation, nowMillis: Long): Long {
        val previous = dao.lastAllocatedId(owner)
        check(previous < Long.MAX_VALUE) { "Health observation identities are exhausted." }
        val id = maxOf(nowMillis, previous + 1L)
        val input = prepared.input
        input.notes.followUp.previousObservationId?.let { parentId ->
            val parent = checkNotNull(dao.record(owner, input.identity.tankId, parentId)).toStored()
            require(parent.input.kind == input.observation.kind.name)
            require(parent.input.subjectId == (input.observation.subjectId ?: 0L))
            require(parent.input.observedAtMillis <= input.identity.observedAtMillis)
        }
        val stored = HealthObservationRecordCodec.encode(owner, id, nowMillis, prepared)
        dao.insert(stored.toEntity())
        dao.insertRequest(HealthObservationRequest(owner, input.identity.requestId, id, fingerprint(input)))
        return id
    }

    private fun fingerprint(input: HealthObservationInput): String =
        healthObservationSha256(HealthObservationInputCodec.encode(input).toByteArray())
}

package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationRequest
import com.aqua.aqualight.data.user.archive.HealthHistoryArchive
import java.util.concurrent.Callable

/** Caller owns the shared archive/tank gates. Every imported event and retry identity commits together. */
internal class HealthObservationArchiveCommit(private val database: HealthObservationDatabase) {
    private val dao = database.observations()

    fun previous(owner: String, source: StoredHealthObservation): StoredHealthObservation? {
        val key = HealthObservationImportIdentity.key(source)
        return dao.origin(owner, key.first, key.second)?.let { row ->
            check(row.deleteState == 0)
            row.toStored().also { require(HealthObservationImportIdentity.sameEvidence(it, source)) }
        }
    }

    fun restore(request: HealthHistoryRestoreRequest, photos: Map<Long, String>,
        requireAuthority: () -> Unit): Int = database.runInTransaction(Callable {
        requireAuthority()
        var lastId = dao.lastAllocatedId(request.ownerUid)
        var added = 0
        val restoredIds = mutableMapOf<Long, Long>()
        HealthHistoryArchive.visit(request.file, request.reference.recordCount) { source ->
            val tankId = requireNotNull(request.tankIdMap[source.input.tankId])
            val existing = previous(request.ownerUid, source)
            if (existing == null) {
                lastId = Math.addExact(lastId, 1L)
                val parent = previousObservation(request.ownerUid, tankId, source, restoredIds)
                val row = HealthObservationImportIdentity.remap(source, HealthObservationImportTarget(
                    request.ownerUid, tankId, lastId, request.transactionId, photos[source.id], parent))
                dao.insert(row.toEntity())
                dao.insertRequest(HealthObservationRequest(request.ownerUid, row.input.requestId, row.id,
                    healthObservationSha256(row.input.toByteArray())))
                restoredIds[source.id] = row.id
                added += 1
            } else {
                require(existing.input.tankId == tankId) { "Imported observation targets a different aquarium." }
                restoredIds[source.id] = existing.id
            }
        }
        requireAuthority()
        added
    })

    fun rollback(owner: String, transaction: String): List<String> = database.runInTransaction(Callable {
        val photos = mutableListOf<String>()
        var after = 0L
        var page = dao.restorePage(owner, transaction, after)
        while (page.isNotEmpty()) {
            page.forEach { row ->
                val stored = row.toStored()
                require(stored.hasOrigin() && stored.origin.restoreTransactionId == transaction)
                stored.input.photoUri.takeIf(String::isNotEmpty)?.let(photos::add)
            }
            after = page.last().observationId
            page = dao.restorePage(owner, transaction, after)
        }
        database.maintenance().rollbackRestore(owner, transaction)
        photos
    })

    fun transactionTanks(owner: String, transaction: String): Set<Long> {
        val tanks = mutableSetOf<Long>()
        var after = 0L
        var page = dao.restorePage(owner, transaction, after)
        while (page.isNotEmpty()) {
            page.forEach { tanks += it.toStored().input.tankId }
            after = page.last().observationId
            page = dao.restorePage(owner, transaction, after)
        }
        return tanks
    }

    private fun previousObservation(owner: String, tank: Long, source: StoredHealthObservation,
        restoredIds: Map<Long, Long>): Long? {
        val original = HealthObservationImportIdentity.original(source)
        if (!original.input.hasPreviousObservationId()) return null
        val restoredParent = restoredIds[source.input.previousObservationId]?.let { dao.record(owner, tank, it) }
        val parentRow = restoredParent ?: dao.origin(owner, original.ownerUid, original.input.previousObservationId)
        val parent = parentRow?.let {
            check(it.deleteState == 0)
            it.toStored()
        }
        parent?.let {
            require(it.input.tankId == tank && it.input.kind == source.input.kind &&
                it.input.subjectId == source.input.subjectId &&
                it.input.observedAtMillis <= source.input.observedAtMillis)
        }
        return parent?.id
    }
}

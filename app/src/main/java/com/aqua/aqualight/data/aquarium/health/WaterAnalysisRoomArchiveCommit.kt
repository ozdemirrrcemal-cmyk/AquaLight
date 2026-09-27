package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterRequestEntity
import com.aqua.aqualight.data.user.archive.WaterHistoryArchive
import com.aqua.aqualight.data.user.archive.WaterHistoryArchiveReference
import java.io.File
import java.util.concurrent.Callable

/** Blocking primitives. The archive coordinator owns session/owner/tank gates through acknowledgement. */
internal class WaterAnalysisRoomArchiveCommit(private val database: WaterAnalysisDatabase) {
    private val analyses = database.analyses()
    private val imports = database.imports()
    private val index = WaterAnalysisRoomImportIndex(database)

    fun snapshot(owner: String, tankIds: Set<Long>, file: File): WaterHistoryArchiveReference =
        database.runInTransaction(Callable {
            WaterAnalysisRoomCommit(database).requireActive(owner)
            val count = analyses.countForOwner(owner)
            require(count in 0L..WaterHistoryArchive.MAX_RECORDS.toLong())
            WaterHistoryArchive.write(records(owner).onEach { record ->
                require(record.tankId in tankIds) { "Analysis snapshot contains an unarchived aquarium." }
            }, count.toInt(), file)
        })

    fun restore(request: WaterHistoryRestoreRequest, requireAuthority: () -> Unit): Int =
        database.runInTransaction(Callable {
            requireAuthority()
            WaterAnalysisRoomCommit(database).requireActive(request.ownerUid)
            var lastId = analyses.lastAllocatedId(request.ownerUid)
            var added = 0
            WaterHistoryArchive.visit(request.file, request.reference.recordCount) { source ->
                val tankId = requireNotNull(request.tankIdMap[source.tankId])
                val previous = index.previous(request.ownerUid, source)
                if (previous == null) {
                    lastId = Math.addExact(lastId, 1L)
                    insert(WaterAnalysisImportIdentity.remap(source, WaterAnalysisImportTarget(
                        request.ownerUid, tankId, lastId, request.transactionId)))
                    added += 1
                } else {
                    require(previous.tankId == tankId && WaterAnalysisImportIdentity.sameEvidence(previous, source)) {
                        "An archived analysis identity conflicts with existing history."
                    }
                }
            }
            requireAuthority()
            added
        })

    fun rollback(owner: String, transactionId: String, requireAuthority: () -> Unit) {
        database.runInTransaction {
            requireAuthority()
            WaterAnalysisRoomCommit(database).requireActive(owner)
            var expectedCount = 0
            visitTransaction(owner, transactionId) { record ->
                check(record.hasImportOrigin() && record.importOrigin.restoreTransactionId == transactionId)
                val origin = record.importOrigin
                index.requireMapping(record, checkNotNull(imports.original(owner,
                    origin.sourceOwnerUid, origin.sourceAnalysisId)))
                expectedCount += 1
            }
            check(imports.rollback(owner, transactionId) == expectedCount)
            // Event FK cascades its import mapping; request tombstones still prevent ID/retry reuse.
        }
    }

    fun transactionTanks(owner: String, transactionId: String): Set<Long> = buildSet {
        visitTransaction(owner, transactionId) { add(it.tankId) }
    }

    private fun insert(record: StoredWaterAnalysis) {
        analyses.insert(listOf(record.toMigrationEntity()))
        analyses.insertRequests(listOf(WaterRequestEntity(record.ownerUid, record.requestId,
            record.id, WaterAnalysisRequestFingerprint.of(record))))
        index.insert(record)
    }

    private fun records(owner: String): Sequence<StoredWaterAnalysis> = sequence {
        var afterId = 0L
        var page = analyses.migrationPage(owner, afterId)
        while (page.isNotEmpty()) {
            page.forEach { yield(it.toMigrationRecord()) }
            afterId = page.last().analysisId
            page = analyses.migrationPage(owner, afterId)
        }
    }

    private fun visitTransaction(owner: String, transactionId: String, consume: (StoredWaterAnalysis) -> Unit) {
        var afterId = 0L
        var page = imports.transactionPage(owner, transactionId, afterId)
        while (page.isNotEmpty()) {
            page.forEach { consume(it.toMigrationRecord()) }
            afterId = page.last().analysisId
            page = imports.transactionPage(owner, transactionId, afterId)
        }
    }
}

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterDeletionManifestEntity
import com.aqua.aqualight.data.aquarium.health.room.WaterDeletionStageEntity
import java.util.UUID
import java.util.concurrent.Callable

/** Blocking primitives; caller holds the tank gate from capture through deletion or compensation. */
internal class WaterAnalysisRoomDeletionStage(private val database: WaterAnalysisDatabase) {
    private val stages = database.deletions()
    private val analyses = database.analyses()

    fun capture(owner: String, tankId: Long): WaterDeletionManifestEntity = database.runInTransaction(Callable {
        WaterAnalysisRoomCommit(database).requireActive(owner)
        require(tankId > 0L)
        val previous = stages.manifest(owner, tankId)
        if (previous != null) {
            verify(requireManifest(owner, tankId, previous.transactionId))
            previous
        } else {
            check(stages.count(owner, tankId) == 0L) { "Unjournaled water deletion stage exists." }
            stages.capture(owner, tankId)
            val transaction = UUID.randomUUID().toString()
            val digest = digest(owner, tankId, transaction, checkLive = true)
            val manifest = WaterDeletionManifestEntity(owner, tankId, transaction, digest.count,
                digest.finish(), WaterDeletionManifestEntity.PREPARED)
            check(manifest.recordCount == analyses.countForTank(owner, tankId))
            stages.insertManifest(manifest)
            manifest
        }
    })

    fun remove(owner: String, tankId: Long, transactionId: String) {
        database.runInTransaction {
            val manifest = requireManifest(owner, tankId, transactionId)
            verify(manifest)
            if (manifest.state == WaterDeletionManifestEntity.PREPARED) {
                check(digest(owner, tankId, transactionId, checkLive = true).finish() == manifest.sha256)
                check(analyses.countForTank(owner, tankId) == manifest.recordCount)
                stages.removeEvents(owner, tankId)
                stages.markRemoved(owner, tankId)
            } else {
                check(analyses.countForTank(owner, tankId) == 0L) { "Deleted tank acquired new water history." }
            }
        }
    }

    fun restore(owner: String, tankId: Long, transactionId: String) {
        database.runInTransaction {
            val manifest = requireManifest(owner, tankId, transactionId)
            verify(manifest)
            forEachPage(owner, tankId) { page -> restorePage(page) }
            check(analyses.countForTank(owner, tankId) == manifest.recordCount)
        }
    }

    /** Called only after the external tank journal durably resolves its deletion/rollback. */
    fun complete(owner: String, tankId: Long, transactionId: String) {
        database.runInTransaction {
            if (stages.manifest(owner, tankId) != null) {
                requireManifest(owner, tankId, transactionId)
                stages.clearRows(owner, tankId)
                stages.clearManifest(owner, tankId)
            } else {
                check(stages.count(owner, tankId) == 0L)
            }
        }
    }

    private fun requireManifest(owner: String, tankId: Long, transactionId: String): WaterDeletionManifestEntity {
        val manifest = checkNotNull(stages.manifest(owner, tankId)) { "Water deletion stage is missing." }
        check(manifest.transactionId == transactionId) { "Water deletion transaction changed." }
        check(manifest.state in setOf(WaterDeletionManifestEntity.PREPARED, WaterDeletionManifestEntity.REMOVED))
        return manifest
    }

    private fun verify(manifest: WaterDeletionManifestEntity) {
        val digest = digest(manifest.ownerUid, manifest.tankId, manifest.transactionId, checkLive = false)
        check(digest.count == manifest.recordCount && digest.finish() == manifest.sha256) {
            "Water deletion stage count or checksum does not match."
        }
    }

    private fun digest(owner: String, tankId: Long, transactionId: String,
        checkLive: Boolean): WaterDeletionStageDigest {
        val digest = WaterDeletionStageDigest(owner, tankId, transactionId)
        forEachPage(owner, tankId) { page ->
            page.forEach { row ->
                row.validatedRecord()
                if (checkLive) requireLiveMatch(row)
                digest.add(row)
            }
        }
        return digest
    }

    private fun requireLiveMatch(row: WaterDeletionStageEntity) {
        val existing = checkNotNull(analyses.record(row.ownerUid, row.tankId, row.analysisId))
        existing.toMigrationRecord()
        check(existing.rawProto.contentEquals(row.rawProto)) { "Water history changed after stage capture." }
    }

    private fun restorePage(page: List<WaterDeletionStageEntity>) {
        val missing = page.mapNotNull { row ->
            val record = row.validatedRecord()
            val existing = analyses.recordForOwner(row.ownerUid, row.analysisId)
            if (existing == null) {
                if (record.requestId.isNotEmpty()) {
                    val request = checkNotNull(analyses.request(row.ownerUid, record.requestId))
                    check(request.analysisId == row.analysisId &&
                        request.payloadSha256 == WaterAnalysisRequestFingerprint.of(record))
                }
                record.toMigrationEntity()
            } else {
                requireLiveMatch(row)
                null
            }
        }
        analyses.insert(missing)
    }

    private fun forEachPage(owner: String, tankId: Long, action: (List<WaterDeletionStageEntity>) -> Unit) {
        var lastId = 0L
        var page = stages.page(owner, tankId, lastId)
        while (page.isNotEmpty()) {
            action(page)
            lastId = page.last().analysisId
            page = stages.page(owner, tankId, lastId)
        }
    }
}

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisEntity
import com.aqua.aqualight.data.aquarium.health.room.WaterMigrationEntity
import java.util.concurrent.Callable

/** Stages migration only. VERIFIED is not a live-store cutover and never authorizes deleting Proto. */
internal class WaterAnalysisRoomMigration(private val database: WaterAnalysisDatabase) {
    private val dao = database.analyses()

    /** A crash commits both this batch and its checkpoint, or neither. No replacement of event rows. */
    fun copyNextBatch(source: WaterAnalysisMigrationSource): Boolean = database.runInTransaction(Callable {
        val manifest = source.manifest
        val previous = matchingCheckpoint(manifest)
        val batch = source.batchAfter(previous.lastAnalysisId)
        if (previous.state == WaterMigrationEntity.VERIFIED || batch.isEmpty()) {
            requireComplete(previous)
            false
        } else {
            val copiedCount = previous.copiedCount + batch.size
            if (copiedCount > manifest.recordCount) throw WaterAnalysisMigrationMismatch()
            dao.insert(batch.map(StoredWaterAnalysis::toMigrationEntity))
            dao.saveMigration(checkpoint(manifest, copiedCount, batch.last().id, WaterMigrationEntity.COPYING))
            true
        }
    })

    /** Checks exact payloads and indexed columns under the same transaction as the verified marker. */
    fun verify(source: WaterAnalysisMigrationSource) {
        database.runInTransaction {
            val previous = matchingCheckpoint(source.manifest)
            requireComplete(previous)
            source.verify(destinationRows(source.manifest.ownerUid))
            dao.saveMigration(checkpoint(
                source.manifest, previous.copiedCount, previous.lastAnalysisId, WaterMigrationEntity.VERIFIED
            ))
        }
    }

    private fun matchingCheckpoint(manifest: WaterAnalysisMigrationManifest): WaterMigrationEntity {
        val previous = dao.migration(manifest.ownerUid) ?: checkpoint(manifest, 0, 0L, WaterMigrationEntity.COPYING)
        val identityMatches = previous.sourceSha256 == manifest.sourceSha256 &&
            previous.recordsSha256 == manifest.recordsSha256 && previous.expectedCount == manifest.recordCount
        val progressMatches = previous.copiedCount in 0..manifest.recordCount &&
            dao.countForOwner(manifest.ownerUid) == previous.copiedCount.toLong() &&
            previous.lastAnalysisId >= 0L && (previous.copiedCount == 0) == (previous.lastAnalysisId == 0L)
        val stateKnown = previous.state in setOf(WaterMigrationEntity.COPYING, WaterMigrationEntity.VERIFIED)
        if (!identityMatches || !progressMatches || !stateKnown) throw WaterAnalysisMigrationMismatch()
        return previous
    }

    private fun requireComplete(checkpoint: WaterMigrationEntity) {
        if (checkpoint.copiedCount != checkpoint.expectedCount) throw WaterAnalysisMigrationMismatch()
    }

    private fun destinationRows(ownerUid: String): Sequence<StoredWaterAnalysis> = sequence {
        var lastId = 0L
        var page = dao.migrationPage(ownerUid, lastId)
        while (page.isNotEmpty()) {
            page.forEach { yield(it.toMigrationRecord()) }
            lastId = page.last().analysisId
            page = dao.migrationPage(ownerUid, lastId)
        }
    }

    private fun checkpoint(
        manifest: WaterAnalysisMigrationManifest,
        copiedCount: Int,
        lastId: Long,
        state: Int
    ): WaterMigrationEntity = WaterMigrationEntity(
        manifest.ownerUid, manifest.sourceSha256, manifest.recordsSha256,
        manifest.recordCount, copiedCount, lastId, state
    )
}

internal fun StoredWaterAnalysis.toMigrationEntity(): WaterAnalysisEntity = WaterAnalysisEntity(
    ownerUid, id, tankId, measuredAtMillis, createdAtMillis, requestId.ifEmpty { null }, toByteArray()
)

internal fun WaterAnalysisEntity.toMigrationRecord(): StoredWaterAnalysis {
    val record = StoredWaterAnalysis.parseFrom(rawProto)
    val identityMatches = record.ownerUid == ownerUid && record.id == analysisId && record.tankId == tankId
    val timesMatch = record.measuredAtMillis == observedAtMillis && record.createdAtMillis == createdAtMillis
    if (!identityMatches || !timesMatch || record.requestId.ifEmpty { null } != requestId) {
        throw WaterAnalysisMigrationMismatch()
    }
    return record
}

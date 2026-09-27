package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterMigrationEntity
import com.aqua.aqualight.data.aquarium.health.room.WaterRequestEntity

/**
 * Explicit cutover, callable only after legacy writes are stopped under the owning store gate.
 * Production composition must not invoke this until archive and deletion recovery gates pass.
 * Source bytes are retained. ACTIVE can never authorize fallback to the older Proto history.
 */
internal class WaterAnalysisRoomActivation(private val database: WaterAnalysisDatabase) {
    fun activate(source: WaterAnalysisMigrationSource) {
        val dao = database.analyses()
        val previous = dao.migration(source.manifest.ownerUid)
        if (previous?.state == WaterMigrationEntity.ACTIVE) {
            require(previous.recordsSha256 == source.manifest.recordsSha256 &&
                previous.sourceSha256 == source.manifest.sourceSha256) { "Active migration source changed." }
            return
        }
        val migration = WaterAnalysisRoomMigration(database)
        while (migration.copyNextBatch(source)) { /* Each bounded batch has its own durable checkpoint. */ }
        migration.verify(source)
        database.runInTransaction {
            val verified = checkNotNull(dao.migration(source.manifest.ownerUid))
            check(verified.state == WaterMigrationEntity.VERIFIED)
            seedRequests(source)
            dao.saveMigration(WaterMigrationEntity(verified.ownerUid, verified.sourceSha256,
                verified.recordsSha256, verified.expectedCount, verified.copiedCount,
                verified.lastAnalysisId, WaterMigrationEntity.ACTIVE))
        }
    }

    private fun seedRequests(source: WaterAnalysisMigrationSource) {
        var lastId = 0L
        var batch = source.batchAfter(lastId)
        while (batch.isNotEmpty()) {
            database.analyses().insertRequests(batch.filter { it.requestId.isNotEmpty() }.map { row ->
                WaterRequestEntity(row.ownerUid, row.requestId, row.id, WaterAnalysisRequestFingerprint.of(row))
            })
            lastId = batch.last().id
            batch = source.batchAfter(lastId)
        }
    }
}

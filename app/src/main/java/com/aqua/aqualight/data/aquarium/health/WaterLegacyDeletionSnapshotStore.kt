package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.health.room.WaterDeletionManifestEntity
import com.aqua.aqualight.data.aquarium.health.room.WaterDeletionStageEntity
import java.util.UUID
import java.util.concurrent.Callable

/** The migration bridge snapshots live Proto history into the same bounded Room staging tables. */
internal class WaterLegacyDeletionSnapshotStore(private val database: WaterAnalysisDatabase) {
    private val dao = database.deletions()

    fun pendingPage(owner: String, afterTankId: Long): List<WaterDeletionManifestEntity> =
        dao.manifestPage(owner, afterTankId)

    fun capture(owner: String, tankId: Long, records: List<StoredWaterAnalysis>): String =
        database.runInTransaction(Callable {
            check(dao.manifest(owner, tankId) == null && dao.count(owner, tankId) == 0L) {
                "An unresolved water deletion stage already exists."
            }
            val transaction = UUID.randomUUID().toString()
            records.sortedBy { it.id }.chunked(WaterAnalysisMigrationSource.BATCH_SIZE).forEach { page ->
                dao.insertRows(page.map { record ->
                    require(record.ownerUid == owner && record.tankId == tankId)
                    WaterDeletionStageEntity(owner, tankId, record.id, record.toByteArray())
                })
            }
            val digest = digest(owner, tankId, transaction)
            check(digest.count == records.size.toLong())
            dao.insertManifest(WaterDeletionManifestEntity(owner, tankId, transaction, digest.count,
                digest.finish(), WaterDeletionManifestEntity.PREPARED))
            transaction
        })

    fun visitVerified(owner: String, tankId: Long, transactionId: String, consume: (StoredWaterAnalysis) -> Unit) {
        database.runInTransaction {
            val manifest = checkNotNull(dao.manifest(owner, tankId)) { "Water rollback snapshot is missing." }
            check(manifest.transactionId == transactionId && manifest.state == WaterDeletionManifestEntity.PREPARED)
            val digest = digest(owner, tankId, transactionId)
            check(digest.count == manifest.recordCount && digest.finish() == manifest.sha256) {
                "Water rollback snapshot failed verification."
            }
            visit(owner, tankId) { consume(it.validatedRecord()) }
        }
    }

    fun finish(owner: String, tankId: Long, expectedTransactionId: String?) {
        database.runInTransaction {
            val manifest = dao.manifest(owner, tankId)
            if (manifest == null) {
                check(dao.count(owner, tankId) == 0L)
            } else {
                check(expectedTransactionId == null || manifest.transactionId == expectedTransactionId)
                dao.clearRows(owner, tankId)
                dao.clearManifest(owner, tankId)
            }
        }
    }

    private fun digest(owner: String, tankId: Long, transaction: String): WaterDeletionStageDigest {
        val digest = WaterDeletionStageDigest(owner, tankId, transaction)
        visit(owner, tankId) { row ->
            row.validatedRecord()
            digest.add(row)
        }
        return digest
    }

    private fun visit(owner: String, tankId: Long, consume: (WaterDeletionStageEntity) -> Unit) {
        var lastId = 0L
        var page = dao.page(owner, tankId, lastId)
        while (page.isNotEmpty()) {
            page.forEach(consume)
            lastId = page.last().analysisId
            page = dao.page(owner, tankId, lastId)
        }
    }
}

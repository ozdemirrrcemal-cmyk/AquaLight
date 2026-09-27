package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import java.util.concurrent.Callable

/** Bounded row traversal avoids both a full history list and an unbounded SQL NOT IN argument list. */
internal class WaterAnalysisRoomOrphanRepair(private val database: WaterAnalysisDatabase) {
    fun repair(owner: String, tankIds: Set<Long>): Int = database.runInTransaction(Callable {
        WaterAnalysisRoomCommit(database).requireActive(owner)
        val dao = database.analyses()
        var lastId = 0L
        var removed = 0
        var page = dao.migrationPage(owner, lastId)
        while (page.isNotEmpty()) {
            page.forEach { row ->
                val record = row.toMigrationRecord()
                if (record.tankId !in tankIds) {
                    removed = Math.addExact(removed, dao.delete(owner, record.tankId, record.id))
                }
            }
            lastId = page.last().analysisId
            page = dao.migrationPage(owner, lastId)
        }
        removed
    })
}

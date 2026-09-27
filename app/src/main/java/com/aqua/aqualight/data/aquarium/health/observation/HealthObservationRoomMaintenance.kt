package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationEntity

/** Joins the existing tank deletion saga; no second tank deletion flow or journal is introduced. */
internal class HealthObservationRoomMaintenance(private val database: HealthObservationDatabase) {
    private val maintenance = database.maintenance()

    fun prepare(owner: String, tank: Long, transaction: String) = database.runInTransaction {
        requireIdentity(owner, tank, transaction)
        requireStage(owner, tank, transaction)
        forEachTankPage(owner, tank) { page -> page.forEach { it.toStored() } }
        maintenance.prepare(owner, tank, transaction)
    }

    fun remove(owner: String, tank: Long, allowUnstaged: Boolean) = database.runInTransaction {
        val stages = maintenance.stages(owner, tank)
        require(stages.size <= 1)
        val transaction = stages.singleOrNull()
        if (transaction == null) {
            check(allowUnstaged || database.observations().tankPage(owner, tank, 0).isEmpty()) {
                "Health history deletion must be staged before removing a live tank."
            }
            maintenance.removeMissingTank(owner, tank)
        } else {
            forEachTankPage(owner, tank) { page ->
                page.forEach { row ->
                    row.toStored()
                    check(row.deleteTransactionId == transaction && row.deleteState != 0)
                }
            }
            maintenance.remove(owner, tank, transaction)
        }
    }

    fun restore(owner: String, tank: Long, transaction: String) = database.runInTransaction {
        requireIdentity(owner, tank, transaction)
        requireStage(owner, tank, transaction)
        forEachTankPage(owner, tank) { page -> page.forEach { it.toStored() } }
        maintenance.restore(owner, tank, transaction)
    }

    fun finish(owner: String, tank: Long, transaction: String?) = database.runInTransaction {
        val staged = maintenance.stages(owner, tank)
        require(staged.size <= 1)
        staged.singleOrNull()?.let { existing ->
            require(transaction == null || transaction == existing)
            forEachTankPage(owner, tank) { page -> page.forEach { it.toStored() } }
            maintenance.complete(owner, tank, existing)
            // A prepare that never reached remove is rolled back, not deleted.
            maintenance.restore(owner, tank, existing)
        }
    }

    fun clearOwner(owner: String) = database.runInTransaction {
        require(owner.isNotBlank() && owner == owner.trim())
        maintenance.clearEvents(owner)
        maintenance.clearRequests(owner)
    }

    private fun requireIdentity(owner: String, tank: Long, transaction: String) {
        require(owner.isNotBlank() && owner == owner.trim() && tank > 0L)
        require(WaterAnalysisPolicy.isValidRequestId(transaction))
    }

    private fun requireStage(owner: String, tank: Long, transaction: String) {
        require(maintenance.stages(owner, tank).all { it == transaction })
    }

    private fun forEachTankPage(owner: String, tank: Long, action: (List<HealthObservationEntity>) -> Unit) {
        var lastId = 0L
        var page = database.observations().tankPage(owner, tank, lastId)
        while (page.isNotEmpty()) {
            action(page)
            lastId = page.last().observationId
            page = database.observations().tankPage(owner, tank, lastId)
        }
    }
}

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Uses the existing tank coordinator and durable journal; it introduces no second deletion flow. */
internal class RoomWaterAnalysisDeletionIntegrity(private val database: WaterAnalysisDatabase) :
    WaterAnalysisDeletionIntegrity {
    private val stages = WaterAnalysisRoomDeletionStage(database)

    override suspend fun prepare(tankId: Long): String {
        val owner = UserDataScope.requireCurrentUid()
        return awaitCommit { stages.capture(owner, tankId).transactionId }
    }

    override suspend fun restore(tankId: Long, transactionId: String) {
        val owner = UserDataScope.requireCurrentUid()
        awaitCommit { stages.restore(owner, tankId, transactionId) }
    }

    override suspend fun finish(tankId: Long, transactionId: String?) {
        val owner = UserDataScope.requireCurrentUid()
        awaitCommit {
            val manifest = database.deletions().manifest(owner, tankId)
            if (manifest == null) {
                check(database.deletions().count(owner, tankId) == 0L)
            } else {
                stages.complete(owner, tankId, transactionId ?: manifest.transactionId)
            }
        }
    }

    suspend fun remove(tankId: Long) {
        val owner = UserDataScope.requireCurrentUid()
        awaitCommit {
            database.runInTransaction {
                WaterAnalysisRoomCommit(database).requireActive(owner)
                val manifest = database.deletions().manifest(owner, tankId)
                if (manifest == null) database.deletions().removeEvents(owner, tankId)
                else stages.remove(owner, tankId, manifest.transactionId)
            }
        }
    }

    suspend fun reconcileResolvedStages(owner: String) {
        check(UserDataScope.requireCurrentUid() == owner)
        var lastTank = 0L
        var page = withContext(Dispatchers.IO) { database.deletions().manifestPage(owner, lastTank) }
        while (page.isNotEmpty()) {
            page.forEach { stage ->
                OwnerTankMutationGate.shared.withTanks(owner, listOf(stage.tankId)) {
                    if (TankCareIntegrityJournal.pendingForOwner(owner).none { it.tankId == stage.tankId }) {
                        finish(stage.tankId, stage.transactionId)
                    }
                }
            }
            lastTank = page.last().tankId
            page = withContext(Dispatchers.IO) { database.deletions().manifestPage(owner, lastTank) }
        }
    }

    private suspend fun <T> awaitCommit(block: () -> T): T {
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable + Dispatchers.IO) { block() }
        currentCoroutineContext().ensureActive()
        return result
    }
}

package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.DataStore
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.store.updateDataAwaitingCommit
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Temporary live-Proto adapter. Rollback rows are paged; the legacy destination still rewrites its full Proto. */
internal class ProtoWaterAnalysisDeletionIntegrity(
    private val store: DataStore<WaterAnalysesStore>,
    database: WaterAnalysisDatabase
) : WaterAnalysisDeletionIntegrity {
    private val snapshots = WaterLegacyDeletionSnapshotStore(database)

    override suspend fun prepare(tankId: Long): String {
        require(tankId > 0L)
        val owner = UserDataScope.requireCurrentUid()
        val source = withContext(Dispatchers.IO) {
            WaterAnalysisStoreRules.validateStore(store.data.first()).analysesList
                .filter { it.ownerUid == owner && it.tankId == tankId }
        }
        return awaitStagingCommit { snapshots.capture(owner, tankId, source) }
    }

    override suspend fun restore(tankId: Long, transactionId: String) {
        val owner = UserDataScope.requireCurrentUid()
        store.updateDataAwaitingCommit { current ->
            withContext(Dispatchers.IO) {
                check(UserDataScope.requireCurrentUid() == owner)
                val builder = WaterAnalysisStoreRules.validateStore(current).toBuilder()
                val existing = current.analysesList.filter { it.ownerUid == owner }.associateBy { it.id }
                snapshots.visitVerified(owner, tankId, transactionId) { record ->
                    val previous = existing[record.id]
                    check(previous == null || previous == record) {
                        "Water rollback identity conflicts with a live record."
                    }
                    if (previous == null) builder.addAnalyses(record)
                }
                WaterAnalysisStoreRules.validateStore(builder.build())
            }
        }
    }

    override suspend fun finish(tankId: Long, transactionId: String?) {
        val owner = UserDataScope.requireCurrentUid()
        awaitStagingCommit { snapshots.finish(owner, tankId, transactionId) }
    }

    suspend fun reconcileResolvedStages(owner: String) {
        check(owner == UserDataScope.requireCurrentUid())
        var lastTankId = 0L
        var page = withContext(Dispatchers.IO) { snapshots.pendingPage(owner, lastTankId) }
        while (page.isNotEmpty()) {
            page.forEach { stage ->
                OwnerTankMutationGate.shared.withTanks(owner, listOf(stage.tankId)) {
                    if (TankCareIntegrityJournal.pendingForOwner(owner).none { it.tankId == stage.tankId }) {
                        finish(stage.tankId, null)
                    }
                }
            }
            lastTankId = page.last().tankId
            page = withContext(Dispatchers.IO) { snapshots.pendingPage(owner, lastTankId) }
        }
    }

    private suspend fun <T> awaitStagingCommit(block: () -> T): T {
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable + Dispatchers.IO) { block() }
        currentCoroutineContext().ensureActive()
        return result
    }
}

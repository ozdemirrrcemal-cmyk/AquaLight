package com.aqua.aqualight.data.aquarium.health.observation

import android.content.Context
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Adapter used only while the existing deletion/owner recovery coordinator owns its gates. */
internal class HealthObservationDeletionIntegrity(context: Context) {
    private val database = HealthObservationDatabase.getInstance(context.applicationContext)
    private val tanks = AquariumTankDataStoreManager(context.applicationContext)
    private val maintenance = HealthObservationRoomMaintenance(database)

    suspend fun prepare(tank: Long, transaction: String) = awaitCommit {
        maintenance.prepare(UserDataScope.requireCurrentUid(), tank, transaction)
    }

    suspend fun restore(tank: Long, transaction: String) = awaitCommit {
        maintenance.restore(UserDataScope.requireCurrentUid(), tank, transaction)
    }

    suspend fun finish(tank: Long, transaction: String?) = awaitCommit {
        maintenance.finish(UserDataScope.requireCurrentUid(), tank, transaction)
    }

    suspend fun remove(tank: Long) {
        val owner = UserDataScope.requireCurrentUid()
        val tankMissing = tanks.tanksSnapshotForOwner(owner).none { it.id == tank }
        awaitCommit { maintenance.remove(owner, tank, allowUnstaged = tankMissing) }
    }

    suspend fun reconcileResolvedStages(owner: String) {
        check(UserDataScope.requireCurrentUid() == owner)
        var after = 0L
        var page = withContext(Dispatchers.IO) { database.maintenance().stagedTanks(owner, after) }
        while (page.isNotEmpty()) {
            page.forEach { tank ->
                OwnerTankMutationGate.shared.withTanks(owner, listOf(tank)) {
                    if (TankCareIntegrityJournal.pendingForOwner(owner).none { it.tankId == tank }) {
                        finish(tank, null)
                    }
                }
            }
            after = page.last()
            page = withContext(Dispatchers.IO) { database.maintenance().stagedTanks(owner, after) }
        }
    }

    suspend fun clearOwner(owner: String) = awaitCommit { maintenance.clearOwner(owner) }

    suspend fun repairOrphans(owner: String, validTanks: Set<Long>) = awaitCommit {
        database.runInTransaction {
            var after = 0L
            var page = database.observations().ownerPage(owner, after)
            while (page.isNotEmpty()) {
                page.forEach { row ->
                    row.toStored()
                    if (row.tankId !in validTanks) database.maintenance().removeMissingTank(owner, row.tankId)
                }
                after = page.last().observationId
                page = database.observations().ownerPage(owner, after)
            }
        }
    }

    private suspend fun <T> awaitCommit(block: () -> T): T {
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable + Dispatchers.IO) { block() }
        currentCoroutineContext().ensureActive()
        return result
    }
}

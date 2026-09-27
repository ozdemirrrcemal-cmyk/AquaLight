package com.aqua.aqualight.data.aquarium.health.observation

import android.content.Context
import com.aqua.aqualight.data.aquarium.OwnerArchiveMutationGate
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.OwnerSessionWriteLease
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.UserDataRestoreJournal
import com.aqua.aqualight.data.user.archive.requireNoActiveRestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class HealthObservationWriteAccess(
    context: Context,
    val session: OwnerSessionWriteLease,
    private val tanks: AquariumTankDataStoreManager
) {
    val appContext = context.applicationContext
    val database = HealthObservationDatabase.getInstance(appContext)
    val commits = HealthObservationRoomCommit(database)
    val queries = HealthObservationRoomQueries(database)
    private val journal = UserDataRestoreJournal(appContext)
    val owner: String get() = session.ownerUid

    init { TankCareIntegrityJournal.initialize(appContext) }

    suspend fun <T> withTankWrite(tankId: Long, block: suspend () -> T): T = session.withWrite {
        UserDataScope.withOwnerUid(owner) {
            OwnerArchiveMutationGate.shared.withOwner(owner) {
                OwnerTankMutationGate.shared.withTanks(owner, listOf(tankId)) {
                    journal.requireNoActiveRestore(owner)
                    requireAuthority(tankId)
                    check(tanks.tanksSnapshotForOwner(owner).any { it.id == tankId }) {
                        "The selected aquarium is no longer available."
                    }
                    block()
                }
            }
        }
    }

    fun requireAuthority(tankId: Long) {
        session.requireCurrent()
        check(!TankCareIntegrityJournal.isWriteBlocked(owner, tankId)) {
            "The aquarium has an active deletion transaction."
        }
    }

    suspend fun <T> awaitCommit(block: () -> T): T {
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable + Dispatchers.IO) { block() }
        currentCoroutineContext().ensureActive()
        return result
    }
}

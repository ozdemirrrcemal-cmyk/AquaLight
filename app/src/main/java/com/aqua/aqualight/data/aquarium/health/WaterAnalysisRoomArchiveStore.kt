package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.WaterHistoryArchive
import com.aqua.aqualight.data.user.archive.WaterHistoryArchiveReference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Staged Room backend; production remains on Proto until its owner cutover is explicitly enabled. */
internal class WaterAnalysisRoomArchiveStore(
    database: WaterAnalysisDatabase,
    private val existingTankIds: suspend (String) -> Set<Long>
) : WaterAnalysisHistoryArchiveStore {
    private val commits = WaterAnalysisRoomArchiveCommit(database)

    override suspend fun snapshot(ownerUid: String, tankIds: Set<Long>, file: File): WaterHistoryArchiveReference =
        withContext(Dispatchers.IO) {
            requireOwner(ownerUid)
            commits.snapshot(ownerUid, tankIds, file).also { requireOwner(ownerUid) }
        }

    override suspend fun restore(request: WaterHistoryRestoreRequest): Int = withContext(Dispatchers.IO) {
        requireOwner(request.ownerUid)
        WaterHistoryArchive.validate(request.reference, request.file, request.tankIdMap.keys)
        OwnerTankMutationGate.shared.withTanks(request.ownerUid, request.tankIdMap.values) {
            val currentTanks = existingTankIds(request.ownerUid)
            require(request.tankIdMap.values.all { it in currentTanks }) { "Restore target aquarium is missing." }
            awaitCommit {
                commits.restore(request) {
                    requireOwner(request.ownerUid)
                    request.tankIdMap.values.forEach { tankId ->
                        check(!TankCareIntegrityJournal.isWriteBlocked(request.ownerUid, tankId))
                    }
                }
            }
        }
    }

    override suspend fun rollback(ownerUid: String, transactionId: String) = withContext(Dispatchers.IO) {
        requireOwner(ownerUid)
        val tanks = commits.transactionTanks(ownerUid, transactionId)
        OwnerTankMutationGate.shared.withTanks(ownerUid, tanks) {
            awaitCommit { commits.rollback(ownerUid, transactionId) { requireOwner(ownerUid) } }
        }
    }

    private suspend fun <T> awaitCommit(block: () -> T): T {
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable + Dispatchers.IO) { block() }
        currentCoroutineContext().ensureActive()
        return result
    }

    private fun requireOwner(ownerUid: String) {
        check(UserDataScope.requireCurrentUid() == ownerUid) { "Archive owner session changed." }
    }
}

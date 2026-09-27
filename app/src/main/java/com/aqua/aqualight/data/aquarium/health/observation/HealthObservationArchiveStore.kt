package com.aqua.aqualight.data.aquarium.health.observation

import android.content.Context
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.health.observation.room.HealthObservationDatabase
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.HealthHistoryArchive
import com.aqua.aqualight.data.user.archive.HealthHistoryArchiveReference
import com.aqua.aqualight.data.user.archive.UserDataRestoreTransactions
import com.aqua.aqualight.data.user.archive.UserDataRestoreTransactionState
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class HealthHistoryRestoreRequest(
    val ownerUid: String,
    val transactionId: String,
    val tankIdMap: Map<Long, Long>,
    val reference: HealthHistoryArchiveReference,
    val file: File,
    val mediaByEntryName: Map<String, File>
)

/** Joins the existing owner archive transaction. Never opens a competing restore journal. */
internal class HealthObservationArchiveStore(
    context: Context,
    private val existingTankIds: suspend (String) -> Set<Long>,
    private val transactions: UserDataRestoreTransactions
) {
    private val database = HealthObservationDatabase.getInstance(context.applicationContext)
    private val media = UserDataArchiveMediaGateway(context.applicationContext)
    private val ownership = HealthObservationMedia(context.applicationContext)
    private val commits = HealthObservationArchiveCommit(database)
    private val snapshots = HealthObservationArchiveSnapshot(database, media)

    suspend fun snapshot(owner: String, tanks: Set<Long>, file: File, mediaDirectory: File?): HealthHistorySnapshot =
        withContext(Dispatchers.IO) {
            requireOwner(owner)
            snapshots.snapshot(owner, tanks, file, mediaDirectory).also { requireOwner(owner) }
        }

    suspend fun restore(request: HealthHistoryRestoreRequest): Int = withContext(Dispatchers.IO) {
        requireOwner(request.ownerUid)
        HealthHistoryArchive.validate(request.reference, request.file, request.tankIdMap.keys)
        OwnerTankMutationGate.shared.withTanks(request.ownerUid, request.tankIdMap.values) {
            val tanks = existingTankIds(request.ownerUid)
            require(request.tankIdMap.values.all { it in tanks }) { "Restore target aquarium is missing." }
            requireAuthority(request)
            restoreWithPhotos(request)
        }
    }

    suspend fun rollback(owner: String, transaction: String) = withContext(Dispatchers.IO) {
        requireOwner(owner)
        val tanks = commits.transactionTanks(owner, transaction)
        OwnerTankMutationGate.shared.withTanks(owner, tanks) {
            withContext(NonCancellable) {
                requireOwner(owner)
                commits.rollback(owner, transaction).forEach { ownership.deleted(owner, it) }
            }
        }
    }

    private suspend fun restoreWithPhotos(request: HealthHistoryRestoreRequest): Int {
        val candidates = linkedMapOf<Long, String>()
        var committed = false
        try {
            val newIds = mutableSetOf<Long>()
            HealthHistoryArchive.visit(request.file, request.reference.recordCount) { source ->
                if (commits.previous(request.ownerUid, source) == null) newIds += source.id
            }
            request.reference.photos.filter { it.observationId in newIds }.forEach { photo ->
                currentCoroutineContext().ensureActive()
                val source = requireNotNull(request.mediaByEntryName[photo.media.entryName])
                candidates[photo.observationId] = media.prepareRestoredPhoto(request.ownerUid,
                    "${request.transactionId}_${photo.observationId}", source, AppMediaScope.HEALTH)
            }
            currentCoroutineContext().ensureActive()
            val count = withContext(NonCancellable + Dispatchers.IO) {
                candidates.values.forEach { ownership.requireCandidate(request.ownerUid, it) }
                val added = commits.restore(request, candidates) { requireAuthority(request) }
                committed = true
                candidates.values.forEach(media::commit)
                added
            }
            currentCoroutineContext().ensureActive()
            return count
        } finally {
            if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                candidates.values.forEach(media::rollback)
            }
        }
    }

    private fun requireAuthority(request: HealthHistoryRestoreRequest) {
        requireOwner(request.ownerUid)
        val pending = transactions.pending(request.ownerUid)
        check(pending?.state == UserDataRestoreTransactionState.ACTIVE &&
            pending.waterTransactionId == request.transactionId) { "Restore transaction is no longer active." }
        request.tankIdMap.values.forEach {
            check(!TankCareIntegrityJournal.isWriteBlocked(request.ownerUid, it))
        }
    }

    private fun requireOwner(owner: String) {
        check(UserDataScope.requireCurrentUid() == owner) { "Archive owner session changed." }
    }
}

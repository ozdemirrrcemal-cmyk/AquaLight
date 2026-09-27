package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.DataStore
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.store.updateDataAwaitingCommit
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.WaterHistoryArchive
import com.aqua.aqualight.data.user.archive.WaterHistoryArchiveReference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class WaterHistoryRestoreRequest(
    val ownerUid: String,
    val transactionId: String,
    val tankIdMap: Map<Long, Long>,
    val reference: WaterHistoryArchiveReference,
    val file: File
)

internal interface WaterAnalysisHistoryArchiveStore {
    suspend fun snapshot(ownerUid: String, tankIds: Set<Long>, file: File): WaterHistoryArchiveReference
    suspend fun restore(request: WaterHistoryRestoreRequest): Int
    suspend fun rollback(ownerUid: String, transactionId: String)
}

/** Live Proto bridge. The event carries its rollback identity in the same durable write. */
internal class WaterAnalysisArchiveStore(
    private val store: DataStore<WaterAnalysesStore>,
    private val existingTankIds: suspend (String) -> Set<Long>
) : WaterAnalysisHistoryArchiveStore {
    override suspend fun snapshot(ownerUid: String, tankIds: Set<Long>, file: File): WaterHistoryArchiveReference =
        withContext(Dispatchers.IO) {
            requireOwner(ownerUid)
            val current = WaterAnalysisStoreRules.validateStore(store.data.first())
            val records = current.analysesList.asSequence().filter { it.ownerUid == ownerUid }
            require(records.all { it.tankId in tankIds }) { "Analysis snapshot contains an unarchived aquarium." }
            WaterHistoryArchive.write(records, records.count(), file).also { requireOwner(ownerUid) }
        }

    override suspend fun restore(request: WaterHistoryRestoreRequest): Int = withContext(Dispatchers.IO) {
        requireOwner(request.ownerUid)
        WaterHistoryArchive.validate(request.reference, request.file, request.tankIdMap.keys)
        if (request.reference.recordCount == 0) return@withContext 0
        OwnerTankMutationGate.shared.withTanks(request.ownerUid, request.tankIdMap.values) {
            val currentTankIds = existingTankIds(request.ownerUid)
            require(request.tankIdMap.values.all { it in currentTankIds }) { "Restore target aquarium is missing." }
            var added = 0
            store.updateDataAwaitingCommit { current ->
                requireOwner(request.ownerUid)
                request.tankIdMap.values.forEach { tankId ->
                    check(!TankCareIntegrityJournal.isWriteBlocked(request.ownerUid, tankId))
                }
                val result = WaterAnalysisArchiveImport.merge(current, request)
                added = result.second
                result.first
            }
            added
        }
    }

    /** Called before rollback removes tanks, including after process death. */
    override suspend fun rollback(ownerUid: String, transactionId: String) {
        requireOwner(ownerUid)
        val tankIds = store.data.first().analysesList.filter { it.matchesImport(ownerUid, transactionId) }
            .map { it.tankId }.toSet()
        if (tankIds.isEmpty()) return
        OwnerTankMutationGate.shared.withTanks(ownerUid, tankIds) {
            store.updateDataAwaitingCommit { current ->
                requireOwner(ownerUid)
                current.toBuilder().clearAnalyses().addAllAnalyses(current.analysesList.filterNot {
                    it.matchesImport(ownerUid, transactionId)
                }).build().let(WaterAnalysisStoreRules::validateStore)
            }
        }
    }

    private fun requireOwner(ownerUid: String) {
        check(UserDataScope.requireCurrentUid() == ownerUid) { "Archive owner session changed." }
    }
}

internal object WaterAnalysisArchiveImport {
    fun merge(current: WaterAnalysesStore, request: WaterHistoryRestoreRequest): Pair<WaterAnalysesStore, Int> {
        WaterAnalysisStoreRules.validateStore(current)
        val existing = current.analysesList.filter { it.ownerUid == request.ownerUid }
            .associateBy(WaterAnalysisImportIdentity::key).toMutableMap()
        val builder = current.toBuilder()
        var nextId = WaterAnalysisIdentityRules.nextUniqueId(current.analysesList)
        var added = 0
        WaterHistoryArchive.visit(request.file, request.reference.recordCount) { source ->
            val tankId = requireNotNull(request.tankIdMap[source.tankId])
            val key = WaterAnalysisImportIdentity.key(source)
            val previous = existing[key]
            if (previous == null) {
                val restored = WaterAnalysisImportIdentity.remap(source,
                    WaterAnalysisImportTarget(request.ownerUid, tankId, nextId, request.transactionId))
                nextId = Math.addExact(nextId, 1L)
                builder.addAnalyses(restored)
                existing[key] = restored
                added += 1
            } else {
                require(previous.tankId == tankId && WaterAnalysisImportIdentity.sameEvidence(previous, source)) {
                    "An archived analysis identity conflicts with existing history."
                }
            }
        }
        return WaterAnalysisStoreRules.validateStore(builder.build()) to added
    }
}

private fun StoredWaterAnalysis.matchesImport(ownerUid: String, transactionId: String): Boolean =
    this.ownerUid == ownerUid && hasImportOrigin() && importOrigin.restoreTransactionId == transactionId

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.archive.WaterHistoryArchiveReference
import java.io.File

/** Recovery runs before feature entry; it must select the durably committed authority, never recopy it. */
internal class WaterAnalysisRecoveryAuthority(
    private val legacy: ProtoWaterAnalysisDeletionIntegrity,
    private val room: RoomWaterAnalysisDeletionIntegrity,
    private val active: suspend (String) -> Boolean
) : WaterAnalysisDeletionIntegrity {
    override suspend fun prepare(tankId: Long): String = selected().prepare(tankId)
    override suspend fun restore(tankId: Long, transactionId: String) = selected().restore(tankId, transactionId)
    override suspend fun finish(tankId: Long, transactionId: String?) = selected().finish(tankId, transactionId)

    suspend fun reconcileResolvedStages(owner: String) {
        if (active(owner)) room.reconcileResolvedStages(owner) else legacy.reconcileResolvedStages(owner)
    }

    private suspend fun selected(): WaterAnalysisDeletionIntegrity =
        if (active(UserDataScope.requireCurrentUid())) room else legacy
}

internal class WaterAnalysisArchiveAuthority(
    private val legacy: WaterAnalysisHistoryArchiveStore,
    private val room: WaterAnalysisHistoryArchiveStore,
    private val active: suspend (String) -> Boolean
) : WaterAnalysisHistoryArchiveStore {
    override suspend fun snapshot(ownerUid: String, tankIds: Set<Long>, file: File): WaterHistoryArchiveReference =
        selected(ownerUid).snapshot(ownerUid, tankIds, file)
    override suspend fun restore(request: WaterHistoryRestoreRequest): Int = selected(request.ownerUid).restore(request)
    override suspend fun rollback(ownerUid: String, transactionId: String) =
        selected(ownerUid).rollback(ownerUid, transactionId)

    private suspend fun selected(owner: String): WaterAnalysisHistoryArchiveStore = if (active(owner)) room else legacy
}

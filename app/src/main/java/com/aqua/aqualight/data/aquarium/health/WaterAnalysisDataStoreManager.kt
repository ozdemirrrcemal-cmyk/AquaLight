package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationDeletionIntegrity
import com.aqua.aqualight.data.auth.OwnerSessionWriteLease
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.store.updateDataAwaitingCommit
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private val Context.waterAnalysesDataStore: DataStore<WaterAnalysesStore> by dataStore(
    fileName = "water_analyses.pb",
    serializer = WaterAnalysesSerializer
)

internal class WaterAnalysisDataStoreManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val tankStore = AquariumTankDataStoreManager(appContext)
    private val observations by lazy { HealthObservationDeletionIntegrity(appContext) }
    val room by lazy { WaterAnalysisRoomRuntime(appContext, appContext.waterAnalysesDataStore, tankStore) }
    val deletionIntegrity by lazy {
        CombinedHealthDeletionIntegrity(WaterAnalysisRecoveryAuthority(
            ProtoWaterAnalysisDeletionIntegrity(appContext.waterAnalysesDataStore, room.database),
            room.deletion, room.cutover::requireSettledAuthority), observations)
    }
    val archiveStore by lazy {
        WaterAnalysisArchiveAuthority(WaterAnalysisArchiveStore(appContext.waterAnalysesDataStore) { owner ->
            tankStore.tanksSnapshotForOwner(owner).map { it.id }.toSet()
        }, room.archive, room.cutover::requireSettledAuthority)
    }


    init {
        TankCareIntegrityJournal.initialize(appContext)
    }

    suspend fun addAnalysis(
        draft: WaterAnalysisDraftRecord,
        session: OwnerSessionWriteLease,
        prepareEvaluation: suspend () -> StoredWaterEvaluation? = { null }
    ): Long {
        activate(session)
        return room.writer(session).create(draft, prepareEvaluation)
    }

    suspend fun deleteAnalysis(tankId: Long, analysisId: Long, session: OwnerSessionWriteLease) {
        activate(session)
        room.writer(session).delete(tankId, analysisId)
    }

    suspend fun activate(session: OwnerSessionWriteLease) = session.withWrite {
        UserDataScope.withOwnerUid(session.ownerUid) { room.cutover.activate(session.ownerUid) }
    }

    /** Called under the session transition barrier, before restore/deletion/orphan recovery. */
    suspend fun resumePendingCutover(owner: String) {
        if (room.cutover.requiresResume(owner)) {
            UserDataScope.withOwnerUid(owner) { room.cutover.activate(owner) }
        }
    }

    /** Cleanup-only: the deletion coordinator or recovery must hold the shared tank gate. */
    suspend fun deleteAnalysesForTank(tankId: Long) {
        WaterAnalysisIdentityRules.requirePositive("tankId", tankId)
        val ownerUid = UserDataScope.requireCurrentUid()
        observations.remove(tankId)
        if (room.cutover.requireSettledAuthority(ownerUid)) {
            room.deletion.remove(tankId)
            return
        }
        appContext.waterAnalysesDataStore.updateDataAwaitingCommit { currentStore ->
            requireOwnerScope(ownerUid)
            currentStore.replaceAllValidated(
                currentStore.analysesList.filterNot { stored ->
                    stored.tankId == tankId && stored.belongsToOwner(ownerUid)
                }
            )
        }
    }

    suspend fun repairOrphanedTankAnalyses(ownerUid: String): Int {
        val targetOwnerUid = requireOwnerUid(ownerUid)
        val validTankIds = tankStore.tanksSnapshotForOwner(targetOwnerUid)
            .mapTo(mutableSetOf()) { tank -> tank.id }
        observations.repairOrphans(targetOwnerUid, validTankIds)
        if (room.cutover.requireSettledAuthority(targetOwnerUid)) {
            return withContext(NonCancellable + Dispatchers.IO) {
                WaterAnalysisRoomOrphanRepair(room.database).repair(targetOwnerUid, validTankIds)
            }
        }
        var removedCount = 0

        appContext.waterAnalysesDataStore.updateData { currentStore ->
            val retained = currentStore.analysesList.filterNot { stored ->
                val remove =
                    stored.belongsToOwner(targetOwnerUid) &&
                        stored.tankId !in validTankIds
                if (remove) removedCount += 1
                remove
            }
            if (removedCount == 0) {
                currentStore
            } else {
                currentStore.replaceAllValidated(retained)
            }
        }
        return removedCount
    }

    suspend fun clearAllAnalyses(ownerUid: String? = null) {
        val targetOwnerUid = ownerUid?.let(::requireOwnerUid)
            ?: UserDataScope.requireCurrentUid()
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            appContext.waterAnalysesDataStore.updateData { currentStore ->
                currentStore.replaceAllValidated(
                    currentStore.analysesList.filterNot { stored -> stored.belongsToOwner(targetOwnerUid) }
                )
            }
            withContext(Dispatchers.IO) {
                observations.clearOwner(targetOwnerUid)
                room.cutover.sources.clear(targetOwnerUid)
                WaterAnalysisOwnerCleanup(room.database).clear(targetOwnerUid)
            }
        }
        currentCoroutineContext().ensureActive()
    }

}

private fun requireOwnerUid(value: String): String {
    val normalized = UserDataScope.normalizeOwnerUid(value)
    if (normalized.isBlank()) {
        throw StoreInvariantViolation("Water-analysis owner uid must not be blank.")
    }
    return normalized
}

private fun requireOwnerScope(expectedOwnerUid: String) {
    if (UserDataScope.requireCurrentUid() != expectedOwnerUid) {
        throw StoreInvariantViolation(
            "The active owner changed while a water-analysis write was in progress."
        )
    }
}

private fun WaterAnalysesStore.replaceAllValidated(
    analyses: List<StoredWaterAnalysis>
): WaterAnalysesStore =
    toBuilder()
        .clearAnalyses()
        .addAllAnalyses(analyses)
        .build()
        .let(WaterAnalysisStoreRules::validateStore)

private fun StoredWaterAnalysis.belongsToOwner(ownerUid: String): Boolean =
    UserDataScope.belongsToOwner(
        recordOwnerUid = this.ownerUid,
        ownerUid = ownerUid
    )

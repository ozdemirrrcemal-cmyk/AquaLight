package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import com.aqua.aqualight.data.user.archive.UserDataRestoreJournal
import com.aqua.aqualight.data.user.archive.requireNoActiveRestore
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.health.room.WaterAnalysisDatabase
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.OwnerSessionWriteLease
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.store.updateDataAwaitingCommit
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val deletionSupport by lazy {
        ProtoWaterAnalysisDeletionIntegrity(appContext.waterAnalysesDataStore,
            WaterAnalysisDatabase.getInstance(appContext))
    }
    val archiveStore by lazy {
        WaterAnalysisArchiveStore(appContext.waterAnalysesDataStore) { owner ->
            tankStore.tanksSnapshotForOwner(owner).map { it.id }.toSet()
        }
    }
    val deletionIntegrity: ProtoWaterAnalysisDeletionIntegrity get() = deletionSupport


    init {
        TankCareIntegrityJournal.initialize(appContext)
    }

    fun analysesForOwnerFlow(ownerUid: String): Flow<List<WaterAnalysisRecord>> {
        val owner = requireOwnerUid(ownerUid)
        return appContext.waterAnalysesDataStore.data.map { store ->
            WaterAnalysisStoreRules.validateStore(store)
                .analysesList
                .filter { it.belongsToOwner(owner) }
                .map(StoredWaterAnalysis::toRecordStrict)
                .let(WaterAnalysisIdentityRules::newestFirst)
        }
    }

    fun analysesForTankFlow(ownerUid: String, tankId: Long): Flow<List<WaterAnalysisRecord>> {
        WaterAnalysisIdentityRules.requirePositive("tankId", tankId)
        return analysesForOwnerFlow(ownerUid).map { analyses ->
            analyses.filter { record -> record.tankId == tankId }
        }
    }

    fun latestAnalysisFlow(ownerUid: String, tankId: Long): Flow<WaterAnalysisRecord?> {
        val owner = requireOwnerUid(ownerUid)
        WaterAnalysisIdentityRules.requirePositive("tankId", tankId)
        return appContext.waterAnalysesDataStore.data.map { store ->
            WaterAnalysisStoreRules.validateStore(store).analysesList.asSequence()
                .filter { it.belongsToOwner(owner) && it.tankId == tankId }
                .maxWithOrNull(compareBy<StoredWaterAnalysis> { it.measuredAtMillis }
                    .thenBy { it.createdAtMillis }.thenBy { it.id })
                ?.toRecordStrict()
        }
    }

    fun analysisFlow(ownerUid: String, tankId: Long, analysisId: Long): Flow<WaterAnalysisRecord?> {
        val owner = requireOwnerUid(ownerUid)
        WaterAnalysisIdentityRules.requirePositive("tankId", tankId)
        WaterAnalysisIdentityRules.requirePositive("analysisId", analysisId)
        return appContext.waterAnalysesDataStore.data.map { store ->
            WaterAnalysisStoreRules.validateStore(store).analysesList.firstOrNull { record ->
                record.id == analysisId && record.tankId == tankId && record.belongsToOwner(owner)
            }?.toRecordStrict()
        }
    }

    suspend fun addAnalysis(
        draft: WaterAnalysisDraftRecord,
        session: OwnerSessionWriteLease,
        prepareEvaluation: suspend () -> StoredWaterEvaluation? = { null }
    ): Long =
        session.withWrite {
            UserDataScope.withOwnerUid(session.ownerUid) {
                WaterAnalysisIdentityRules.requirePositive("tankId", draft.tankId)
                OwnerTankMutationGate.shared.withTanks(session.ownerUid, listOf(draft.tankId)) {
                    addAnalysisUnderGate(draft, session, prepareEvaluation)
                }
            }
        }

    private suspend fun addAnalysisUnderGate(
        draft: WaterAnalysisDraftRecord,
        session: OwnerSessionWriteLease,
        prepareEvaluation: suspend () -> StoredWaterEvaluation?
    ): Long {
        val ownerUid = session.ownerUid
        UserDataRestoreJournal(appContext).requireNoActiveRestore(ownerUid)
        tankStore.requireTankExistsForOwner(ownerUid, draft.tankId)
        // An acknowledged retry returns its frozen event even after catalogs or tank context change.
        WaterAnalysisIdentityRules.replayId(appContext.waterAnalysesDataStore.data.first(), ownerUid, draft)
            ?.let { return it }
        val evaluation = prepareEvaluation()
        var createdId = 0L

        appContext.waterAnalysesDataStore.updateDataAwaitingCommit { currentStore ->
            session.requireCurrent()
            requireOwnerScope(ownerUid)
            if (TankCareIntegrityJournal.isWriteBlocked(ownerUid, draft.tankId)) {
                throw StoreInvariantViolation(
                    "Water analysis targets a tank with an active deletion transaction."
                )
            }
            val replayId = WaterAnalysisIdentityRules.replayId(currentStore, ownerUid, draft)
            if (replayId != null) {
                createdId = replayId
                return@updateDataAwaitingCommit currentStore
            }
            val now = System.currentTimeMillis()
            val record = WaterAnalysisRecord(
                id = WaterAnalysisIdentityRules.nextUniqueId(
                    current = currentStore.analysesList,
                    nowMillis = now
                ),
                ownerUid = ownerUid,
                tankId = draft.tankId,
                measuredAtMillis = draft.measuredAtMillis,
                temperatureCelsius = draft.temperatureCelsius,
                temperatureSource = draft.temperatureSource,
                measurements = draft.measurements,
                createdAtMillis = now,
                requestId = draft.requestId,
                evaluation = evaluation
            )
            WaterAnalysisStoreRules.validateRecord(record, ownerUid)
            createdId = record.id
            currentStore.toBuilder()
                .addAnalyses(record.toStoredStrict())
                .build()
                .let(WaterAnalysisStoreRules::validateStore)
        }

        check(createdId > 0L) {
            "Water analysis creation completed without a generated id."
        }
        return createdId
    }

    suspend fun deleteAnalysis(tankId: Long, analysisId: Long, session: OwnerSessionWriteLease) = session.withWrite {
        UserDataScope.withOwnerUid(session.ownerUid) {
            UserDataRestoreJournal(appContext).requireNoActiveRestore(session.ownerUid)
            appContext.waterAnalysesDataStore.deleteAnalysisForSession(tankId, analysisId, session)
        }
    }

    /** Cleanup-only: the deletion coordinator or recovery must hold the shared tank gate. */
    suspend fun deleteAnalysesForTank(tankId: Long) {
        WaterAnalysisIdentityRules.requirePositive("tankId", tankId)
        val ownerUid = UserDataScope.requireCurrentUid()
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
                WaterAnalysisOwnerCleanup(WaterAnalysisDatabase.getInstance(appContext)).clear(targetOwnerUid)
            }
        }
        currentCoroutineContext().ensureActive()
    }

}

private suspend fun AquariumTankDataStoreManager.requireTankExistsForOwner(ownerUid: String, tankId: Long) {
    WaterAnalysisIdentityRules.requirePositive("tankId", tankId)
    if (tanksSnapshotForOwner(ownerUid).none { tank -> tank.id == tankId }) {
        throw StoreInvariantViolation(
            "Water analysis references a tank that does not exist for the active owner."
        )
    }
}

private suspend fun DataStore<WaterAnalysesStore>.deleteAnalysisForSession(
    tankId: Long,
    analysisId: Long,
    session: OwnerSessionWriteLease
) {
    WaterAnalysisIdentityRules.requirePositive("tankId", tankId)
    WaterAnalysisIdentityRules.requirePositive("analysisId", analysisId)
    val ownerUid = session.ownerUid
    OwnerTankMutationGate.shared.withTanks(ownerUid, listOf(tankId)) {
        updateDataAwaitingCommit { currentStore ->
            session.requireCurrent()
            requireOwnerScope(ownerUid)
            currentStore.replaceAllValidated(
                currentStore.analysesList.filterNot { stored ->
                    stored.id == analysisId && stored.tankId == tankId && stored.belongsToOwner(ownerUid)
                }
            )
        }
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

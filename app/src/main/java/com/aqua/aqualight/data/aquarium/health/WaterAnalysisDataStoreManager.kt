package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.waterAnalysesDataStore: DataStore<WaterAnalysesStore> by dataStore(
    fileName = "water_analyses.pb",
    serializer = WaterAnalysesSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(LocalDataRecoveryTracker.Area.WATER_ANALYSES)
        WaterAnalysisStoreRules.defaultStore()
    }
)

internal class WaterAnalysisDataStoreManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val tankStore = AquariumTankDataStoreManager(appContext)

    val analysesFlow: Flow<List<WaterAnalysisRecord>> =
        appContext.waterAnalysesDataStore.data.map { store ->
            WaterAnalysisStoreRules.validateStore(store)
                .analysesList
                .filter(StoredWaterAnalysis::belongsToCurrentUser)
                .map(StoredWaterAnalysis::toRecordStrict)
                .sortedWith(
                    compareByDescending<WaterAnalysisRecord> { record -> record.measuredAtMillis }
                        .thenByDescending { record -> record.id }
                )
        }

    fun analysesForTankFlow(tankId: Long): Flow<List<WaterAnalysisRecord>> {
        WaterAnalysisStoreRules.requireValidTankId(tankId)
        return analysesFlow.map { analyses ->
            analyses.filter { record -> record.tankId == tankId }
        }
    }

    fun analysisFlow(analysisId: Long): Flow<WaterAnalysisRecord?> {
        WaterAnalysisStoreRules.requireValidAnalysisId(analysisId)
        return analysesFlow.map { analyses ->
            analyses.firstOrNull { record -> record.id == analysisId }
        }
    }

    suspend fun addAnalysis(draft: WaterAnalysisDraftRecord): Long {
        val ownerUid = UserDataScope.requireCurrentUid()
        requireTankExistsForOwner(ownerUid, draft.tankId)
        var createdId = 0L

        appContext.waterAnalysesDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            val now = System.currentTimeMillis()
            val record = WaterAnalysisRecord(
                id = WaterAnalysisStoreRules.nextUniqueId(
                    current = currentStore.analysesList,
                    nowMillis = now
                ),
                ownerUid = ownerUid,
                tankId = draft.tankId,
                measuredAtMillis = draft.measuredAtMillis,
                temperatureCelsius = draft.temperatureCelsius,
                temperatureSource = draft.temperatureSource,
                measurements = draft.measurements,
                createdAtMillis = now
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

    suspend fun deleteAnalysis(analysisId: Long) {
        WaterAnalysisStoreRules.requireValidAnalysisId(analysisId)
        val ownerUid = UserDataScope.requireCurrentUid()
        appContext.waterAnalysesDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            currentStore.replaceAllValidated(
                currentStore.analysesList.filterNot { stored ->
                    stored.id == analysisId && stored.belongsToOwner(ownerUid)
                }
            )
        }
    }

    suspend fun deleteAnalysesForTank(tankId: Long) {
        WaterAnalysisStoreRules.requireValidTankId(tankId)
        val ownerUid = UserDataScope.requireCurrentUid()
        appContext.waterAnalysesDataStore.updateData { currentStore ->
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
        appContext.waterAnalysesDataStore.updateData { currentStore ->
            currentStore.replaceAllValidated(
                currentStore.analysesList.filterNot { stored ->
                    stored.belongsToOwner(targetOwnerUid)
                }
            )
        }
    }

    private suspend fun requireTankExistsForOwner(ownerUid: String, tankId: Long) {
        WaterAnalysisStoreRules.requireValidTankId(tankId)
        if (tankStore.tanksSnapshotForOwner(ownerUid).none { tank -> tank.id == tankId }) {
            throw StoreInvariantViolation(
                "Water analysis references a tank that does not exist for the active owner."
            )
        }
    }

    private fun requireOwnerScope(expectedOwnerUid: String) {
        if (UserDataScope.requireCurrentUid() != expectedOwnerUid) {
            throw StoreInvariantViolation(
                "The active owner changed while a water-analysis write was in progress."
            )
        }
    }

    private fun requireOwnerUid(value: String): String {
        val normalized = UserDataScope.normalizeOwnerUid(value)
        if (normalized.isBlank()) {
            throw StoreInvariantViolation("Water-analysis owner uid must not be blank.")
        }
        return normalized
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

private fun StoredWaterAnalysis.belongsToCurrentUser(): Boolean =
    UserDataScope.belongsToCurrentUser(recordOwnerUid = ownerUid)

private fun StoredWaterAnalysis.belongsToOwner(ownerUid: String): Boolean =
    UserDataScope.belongsToOwner(
        recordOwnerUid = this.ownerUid,
        ownerUid = ownerUid
    )

package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityJournal
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aquariumHealthDataStore: DataStore<AquariumHealthStore> by dataStore(
    fileName = "aquarium_health.pb",
    serializer = AquariumHealthCommercialSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.AQUARIUM_HEALTH
        )
        AquariumHealthStoreRules.defaultStore()
    }
)

internal class AquariumHealthStoreAccess(
    context: Context
) {
    private val appContext = context.applicationContext
    private val tankStore = AquariumTankDataStoreManager(appContext)

    val data: Flow<AquariumHealthStore> =
        appContext.aquariumHealthDataStore.data.map(
            AquariumHealthStoreRules::validateStore
        )

    suspend fun snapshot(): AquariumHealthStore = data.first()

    suspend fun updateOwner(
        ownerUid: String,
        transform: (AquariumHealthStore) -> AquariumHealthStore
    ) {
        val owner = requireOwnerUid(ownerUid)
        UserDataScope.withOwnerUid(owner) {
            appContext.aquariumHealthDataStore.updateData { current ->
                requireOwnerScope(owner)
                AquariumHealthStoreRules.validateStore(
                    transform(AquariumHealthStoreRules.validateStore(current))
                )
            }
        }
    }

    suspend fun updateTank(
        ownerUid: String,
        tankId: Long,
        transform: (AquariumHealthStore) -> AquariumHealthStore
    ) {
        val owner = requireOwnerUid(ownerUid)
        requirePositiveId("tankId", tankId)
        updateOwner(owner) { current ->
            TankHealthIntegrityJournal.requireWritable(owner, tankId)
            transform(current)
        }
    }

    suspend fun requireTank(
        ownerUid: String,
        tankId: Long
    ): SavedAquariumTank {
        val owner = requireOwnerUid(ownerUid)
        requirePositiveId("tankId", tankId)
        return UserDataScope.withOwnerUid(owner) {
            tankStore.tanksSnapshotForOwner(owner)
                .firstOrNull { tank -> tank.id == tankId }
                ?: throw StoreInvariantViolation(
                    "Aquarium health record targets a missing tank."
                )
        }
    }

    suspend fun tanksForOwner(ownerUid: String): List<SavedAquariumTank> {
        val owner = requireOwnerUid(ownerUid)
        return UserDataScope.withOwnerUid(owner) {
            tankStore.tanksSnapshotForOwner(owner)
        }
    }

    fun requireOwnerUid(value: String): String {
        val owner = UserDataScope.normalizeOwnerUid(value)
        if (owner.isBlank()) {
            throw StoreInvariantViolation(
                "Aquarium health persistence requires a non-blank owner."
            )
        }
        return owner
    }

    fun requirePositiveId(field: String, value: Long) {
        require(value > 0L) {
            "$field must be positive."
        }
    }

    private fun requireOwnerScope(ownerUid: String) {
        if (UserDataScope.requireCurrentUid() != ownerUid) {
            throw StoreInvariantViolation(
                "The active owner changed during an aquarium-health operation."
            )
        }
    }
}

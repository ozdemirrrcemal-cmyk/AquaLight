package com.aqua.aqualight.data.aquarium

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumHealthOperations
import com.aqua.aqualight.application.aquarium.LivestockHealthCheck
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.aquarium.store.AquariumHealthStore
import com.aqua.aqualight.platform.media.AppMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Commits media after the authoritative owner-scoped observation has been stored. */
internal class DefaultAquariumHealthOperations(
    context: Context,
    tankStore: AquariumTankDataStoreManager,
    private val dispatcher: CoroutineDispatcher
) : AquariumHealthOperations {
    private val appContext = context.applicationContext
    private val healthStore = AquariumHealthStore(tankStore)

    override suspend fun addHealthObservation(tankId: Long, observation: LivestockHealthObservation) {
        withContext(NonCancellable + dispatcher) {
            runCatching { healthStore.addHealthObservation(tankId, observation) }
                .onFailure {
                    runCatching { AppMediaStorage.rollbackPendingMedia(appContext, observation.photoUri) }
                }
                .getOrThrow()
            AppMediaStorage.commitPendingMedia(appContext, observation.photoUri)
        }
    }

    override suspend fun addHealthCheck(tankId: Long, observationId: Long, check: LivestockHealthCheck) {
        withContext(NonCancellable + dispatcher) {
            runCatching { healthStore.addHealthCheck(tankId, observationId, check) }
                .onFailure {
                    runCatching { AppMediaStorage.rollbackPendingMedia(appContext, check.photoUri) }
                }
                .getOrThrow()
            AppMediaStorage.commitPendingMedia(appContext, check.photoUri)
        }
    }

    override suspend fun closeHealthObservation(tankId: Long, observationId: Long, atMillis: Long) {
        healthStore.closeHealthObservation(tankId, observationId, atMillis)
    }
}

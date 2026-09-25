package com.aqua.aqualight.data.aquarium

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumPlantPhotoOperations
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class DefaultPlantPhotoOperations(
    context: Context,
    private val tankStore: AquariumTankDataStoreManager,
    private val dispatcher: CoroutineDispatcher
) : AquariumPlantPhotoOperations {
    private val appContext = context.applicationContext

    override suspend fun updatePlantPhoto(
        tankId: Long,
        plantId: Long,
        photoUri: String?,
        expectedOwnerUid: String
    ): Unit = withCurrentOwnerScope { ownerUid ->
        require(ownerUid == expectedOwnerUid) { "Plant photo selection belongs to another owner." }
        withContext(NonCancellable + dispatcher) {
            val previousPhoto = runCatching {
                tankStore.plantPhotos.updatePhoto(tankId, plantId, photoUri)
            }.onFailure {
                rollbackUnreferencedCandidate(appContext, tankStore, photoUri, ownerUid, AppMediaScope.PLANT)
            }.getOrThrow()

            runCatching { AppMediaStorage.commitPendingMedia(appContext, photoUri) }
            runCatching {
                AppMediaStorage.deleteAfterCommit(
                    context = appContext,
                    ownerUid = ownerUid,
                    uriString = previousPhoto
                )
            }
            Unit
        }
    }

}

internal suspend fun rollbackUnreferencedCandidate(
    appContext: Context,
    tankStore: AquariumTankDataStoreManager,
    uri: String?,
    ownerUid: String,
    scope: AppMediaScope
) {
    runCatching {
        if (AppMediaStorage.pendingMediaOwner(appContext, uri, scope) != ownerUid) {
            return@runCatching
        }
        val referenced = tankStore.tanksSnapshotForOwner(ownerUid).any { tank ->
            tank.photoUri == uri || tank.plants.any { plant -> plant.photoUri == uri } ||
                tank.livestock.any { item -> item.photoUri == uri }
        }
        if (!referenced) AppMediaStorage.rollbackPendingMedia(appContext, uri)
    }
}

package com.aqua.aqualight.data.aquarium

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumLivestockPhotoOperations
import com.aqua.aqualight.data.aquarium.catalog.livestock.LivestockSelectionValidator
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class DefaultLivestockPhotoOperations(
    context: Context,
    private val tankStore: AquariumTankDataStoreManager,
    private val dispatcher: CoroutineDispatcher
) : AquariumLivestockPhotoOperations {
    private val appContext = context.applicationContext
    private val validator = LivestockSelectionValidator(appContext)

    override suspend fun removeLivestockWithPhoto(
        tankId: Long, livestockId: Long, expectedOwnerUid: String
    ): Unit = withCurrentOwnerScope { owner ->
        require(owner == expectedOwnerUid) { "Livestock form belongs to another owner." }
        withContext(NonCancellable + dispatcher) { tankStore.removeLivestockFromTank(tankId, livestockId) }
    }

    override suspend fun saveLivestockWithPhoto(
        tankId: Long,
        livestock: AquariumLivestock,
        expectedOwnerUid: String,
        isNew: Boolean,
        photoChanged: Boolean
    ): Unit = withCurrentOwnerScope { ownerUid ->
        require(ownerUid == expectedOwnerUid) { "Livestock form belongs to another owner." }
        withContext(NonCancellable + dispatcher) {
            val commit = runCatching {
                validator.requireCurrent(livestock)
                tankStore.livestockPhotos.save(tankId, livestock.toDataLivestock(), isNew, photoChanged)
            }.onFailure {
                rollbackUnreferencedCandidate(
                    appContext, tankStore, livestock.photoUri, ownerUid, AppMediaScope.LIVESTOCK
                )
            }.getOrThrow()
            runCatching { AppMediaStorage.commitPendingMedia(appContext, commit.photoUri) }
            runCatching { AppMediaStorage.deleteAfterCommit(appContext, ownerUid, commit.previousPhotoUri) }
            Unit
        }
    }
}

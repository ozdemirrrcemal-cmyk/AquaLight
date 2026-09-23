package com.aqua.aqualight.data.aquarium

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.aqua.aqualight.application.aquarium.AquariumTankLifecycleOperations
import com.aqua.aqualight.application.aquarium.DeleteAquariumTanksResult
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class DefaultAquariumTankLifecycleOperations(
    context: Context,
    private val tankStore: AquariumTankDataStoreManager,
    private val tankDataCleaner: OwnerTankDataCleaner,
    private val dispatcher: CoroutineDispatcher
) : AquariumTankLifecycleOperations {

    private val appContext = context.applicationContext

    override suspend fun addTank(
        draft: AquariumTankDraft
    ): Long = withContext(NonCancellable) {
        withContext(dispatcher) {
            val pendingPhoto = draft.photoUri
            val result = runCatching {
                tankStore.addTankFromDraft(draft.toDataDraft())
            }
            val tankId = result.getOrElse { error ->
                runCatching {
                    AppMediaStorage.rollbackPendingMedia(
                        appContext,
                        pendingPhoto
                    )
                }
                throw error
            }

            runCatching {
                AppMediaStorage.commitPendingMedia(
                    appContext,
                    pendingPhoto
                )
            }
            tankId
        }
    }

    override suspend fun duplicateTank(
        tankId: Long
    ): Long = withContext(NonCancellable) {
        withContext(dispatcher) {
            val ownerUid = UserDataScope.requireCurrentUid()
            val source = tankStore.tanksSnapshotForOwner(ownerUid)
                .firstOrNull { tank -> tank.id == tankId }
                ?: throw IllegalArgumentException(
                    "Tank not found for the active owner."
                )

            val duplicateId = tankStore.duplicateTank(tankId)
            val duplicate = checkNotNull(
                tankStore.tanksSnapshotForOwner(ownerUid)
                    .firstOrNull { tank -> tank.id == duplicateId }
            ) {
                "Duplicated tank record is missing."
            }

            val sourceIsOwned = AppMediaStorage.isAppOwned(
                appContext,
                source.photoUri
            )
            val invalidSharedOwnership =
                sourceIsOwned &&
                    !source.photoUri.isNullOrBlank() &&
                    (
                        duplicate.photoUri.isNullOrBlank() ||
                            duplicate.photoUri == source.photoUri
                        )

            if (invalidSharedOwnership) {
                runCatching {
                    tankStore.deleteTanks(listOf(duplicateId))
                }
                error(
                    "Tank photo could not be copied with independent ownership."
                )
            }

            runCatching {
                AppMediaStorage.commitPendingMedia(
                    appContext,
                    duplicate.photoUri
                )
            }
            duplicateId
        }
    }

    override suspend fun deleteTanks(
        tankIds: Collection<Long>
    ): DeleteAquariumTanksResult = withContext(dispatcher) {
        withCurrentOwnerScope {
            tankDataCleaner.deleteTanks(tankIds)
                .toApplicationResult()
        }
    }
}

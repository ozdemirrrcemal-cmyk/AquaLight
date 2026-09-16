package com.aqua.aqualight.data.aquarium

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankCleanupIssue
import com.aqua.aqualight.application.aquarium.AquariumTankCleanupStage
import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.aqua.aqualight.application.aquarium.AquariumTankOperations
import com.aqua.aqualight.application.aquarium.AquariumTankSize
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.DeleteAquariumTanksResult
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultAquariumTankOperations(
    context: Context,
    private val tankStore: AquariumTankDataStoreManager,
    private val tankDataCleaner: OwnerTankDataCleaner,
    private val notificationPreferences: NotificationPreferenceUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AquariumTankOperations {

    private val appContext = context.applicationContext

    override val tanks: Flow<List<AquariumTankSnapshot>> = tankStore.tanksFlow.map { tanks ->
        tanks.map(SavedAquariumTank::toApplicationSnapshot)
    }

    override suspend fun addTank(draft: AquariumTankDraft): Long = withContext(NonCancellable) {
        withContext(dispatcher) {
            val pendingPhoto = draft.photoUri
            val tankId = try {
                tankStore.addTankFromDraft(draft.toDataDraft())
            } catch (error: Throwable) {
                runCatching { AppMediaStorage.rollbackPendingMedia(appContext, pendingPhoto) }
                throw error
            }

            runCatching { AppMediaStorage.commitPendingMedia(appContext, pendingPhoto) }
            tankId
        }
    }

    override suspend fun duplicateTank(tankId: Long): Long = withContext(NonCancellable) {
        withContext(dispatcher) {
            val ownerUid = UserDataScope.requireCurrentUid()
            val source = tankStore.tanksSnapshotForOwner(ownerUid)
                .firstOrNull { tank -> tank.id == tankId }
                ?: throw IllegalArgumentException("Tank not found for the active owner.")
            val duplicateId = tankStore.duplicateTank(tankId)
            val duplicate = tankStore.tanksSnapshotForOwner(ownerUid)
                .firstOrNull { tank -> tank.id == duplicateId }
                ?: throw IllegalStateException("Duplicated tank record is missing.")

            val sourceIsOwned = AppMediaStorage.isAppOwned(appContext, source.photoUri)
            val invalidSharedOwnership = sourceIsOwned &&
                !source.photoUri.isNullOrBlank() &&
                (duplicate.photoUri.isNullOrBlank() || duplicate.photoUri == source.photoUri)
            if (invalidSharedOwnership) {
                runCatching { tankStore.deleteTanks(listOf(duplicateId)) }
                throw IllegalStateException(
                    "Tank photo could not be copied with independent ownership."
                )
            }

            runCatching { AppMediaStorage.commitPendingMedia(appContext, duplicate.photoUri) }
            duplicateId
        }
    }

    override suspend fun deleteTanks(
        tankIds: Collection<Long>
    ): DeleteAquariumTanksResult = withContext(dispatcher) {
        withCurrentOwnerScope {
            tankDataCleaner.deleteTanks(tankIds).toApplicationResult()
        }
    }

    override suspend fun updateTankPhoto(tankId: Long, photoUri: String?): Unit =
        withContext(NonCancellable) {
            withContext(dispatcher) {
                val ownerUid = UserDataScope.requireCurrentUid()
                val previousPhoto = try {
                    tankStore.updateTankPhoto(tankId, photoUri)
                } catch (error: Throwable) {
                    runCatching { AppMediaStorage.rollbackPendingMedia(appContext, photoUri) }
                    throw error
                }

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

    override suspend fun updateTankName(tankId: Long, name: String) =
        tankStore.updateTankName(tankId, name)

    override suspend fun updateTankType(tankId: Long, tankType: String) =
        tankStore.updateTankType(tankId, tankType)

    override suspend fun updateTankSize(tankId: Long, size: AquariumTankSize) =
        tankStore.updateTankSize(
            tankId = tankId,
            widthCm = size.widthCm,
            lengthCm = size.lengthCm,
            heightCm = size.heightCm,
            sizeUnit = size.sizeUnit
        )

    override suspend fun updateTankVolumeUnit(tankId: Long, volumeUnit: String) =
        tankStore.updateTankVolumeUnit(tankId, volumeUnit)

    override suspend fun updateTankSetupDate(tankId: Long, setupDateEpochDay: Long) =
        tankStore.updateTankSetupDate(tankId, setupDateEpochDay)

    override suspend fun updateTankStyle(tankId: Long, tankStyle: String) =
        tankStore.updateTankStyle(tankId, tankStyle)

    override suspend fun updateTankDescription(tankId: Long, description: String) =
        tankStore.updateTankDescription(tankId, description)

    override suspend fun updateTankMaterials(
        tankId: Long,
        categoryKey: String,
        materials: List<AquariumMaterialSelection>
    ) = tankStore.updateTankMaterialsForCategory(
        tankId = tankId,
        categoryKey = categoryKey,
        materials = materials.map(AquariumMaterialSelection::toDataSelection)
    )

    override suspend fun updateTankPlants(tankId: Long, plants: List<AquariumPlantTag>) =
        tankStore.updateTankPlants(tankId, plants.map(AquariumPlantTag::toDataTag))

    override suspend fun addLivestock(tankId: Long, livestock: AquariumLivestock) =
        tankStore.addLivestockToTank(tankId, livestock.toDataLivestock())

    override suspend fun updateLivestock(tankId: Long, livestock: AquariumLivestock) =
        tankStore.updateLivestockInTank(tankId, livestock.toDataLivestock())

    override suspend fun removeLivestock(tankId: Long, livestockId: Long) =
        tankStore.removeLivestockFromTank(tankId, livestockId)

    override suspend fun updateSmartCareEnabled(tankId: Long, enabled: Boolean) =
        tankStore.updateSmartCareEnabled(tankId, enabled)

    override suspend fun updateCareRemindersEnabled(
        tankId: Long,
        enabled: Boolean
    ) = withCurrentOwnerScope { ownerUid ->
        tankStore.updateCareRemindersEnabled(tankId, enabled)
        notificationPreferences.reconcileOwner(ownerUid)
    }
}

internal fun OwnerTankDataCleaner.Result.toApplicationResult(): DeleteAquariumTanksResult =
    when (this) {
        OwnerTankDataCleaner.Result.NoOp -> DeleteAquariumTanksResult.NoOp
        is OwnerTankDataCleaner.Result.DeleteFailed -> DeleteAquariumTanksResult.DeleteFailed
        is OwnerTankDataCleaner.Result.Deleted -> DeleteAquariumTanksResult.Deleted(
            tankIds = tankIds,
            cleanupIssues = cleanupIssues.map { issue ->
                AquariumTankCleanupIssue(
                    tankId = issue.tankId,
                    stage = when (issue.stage) {
                        OwnerTankDataCleaner.CleanupStage.CARE_TASKS ->
                            AquariumTankCleanupStage.CARE_TASKS
                        OwnerTankDataCleaner.CleanupStage.DEVICE_ASSIGNMENTS ->
                            AquariumTankCleanupStage.DEVICE_ASSIGNMENTS
                    }
                )
            }
        )
    }

internal fun SavedAquariumTank.toApplicationSnapshot(): AquariumTankSnapshot =
    AquariumTankSnapshot(
        id = id,
        name = name,
        description = description,
        photoUri = photoUri,
        setupDateEpochDay = setupDateEpochDay,
        widthCm = widthCm,
        lengthCm = lengthCm,
        heightCm = heightCm,
        sizeUnit = sizeUnit,
        volumeUnit = volumeUnit,
        tankType = tankType,
        tankStyle = tankStyle,
        createdAtMillis = createdAtMillis,
        smartCareEnabled = smartCareEnabled,
        careRemindersEnabled = careRemindersEnabled,
        plants = plants.map { plant ->
            AquariumPlantTag(
                id = plant.id,
                plantName = plant.plantName,
                category = plant.category,
                markerX = plant.markerX,
                markerY = plant.markerY
            )
        },
        materials = materials.map { material ->
            AquariumMaterialSelection(
                id = material.id,
                productId = material.productId,
                categoryKey = material.categoryKey,
                categoryTitle = material.categoryTitle,
                name = material.name,
                brand = material.brand,
                note = material.note
            )
        },
        livestock = livestock.map { item ->
            AquariumLivestock(
                id = item.id,
                name = item.name,
                category = item.category,
                quantity = item.quantity,
                addedDateEpochDay = item.addedDateEpochDay,
                note = item.note
            )
        }
    )

internal fun AquariumTankDraft.toDataDraft(): TankDraft = TankDraft(
    name = name,
    description = description,
    photoUri = photoUri,
    plants = plants.map(AquariumPlantTag::toDataTag),
    materials = materials.map(AquariumMaterialSelection::toDataSelection),
    info = info,
    setupDateEpochDay = setupDateEpochDay,
    widthCm = widthCm,
    lengthCm = lengthCm,
    heightCm = heightCm,
    sizeUnit = sizeUnit,
    volumeUnit = volumeUnit,
    tankType = tankType,
    tankStyle = tankStyle
)

private fun AquariumPlantTag.toDataTag(): TankPlantTag = TankPlantTag(
    id = id,
    plantName = plantName,
    category = category,
    markerX = markerX,
    markerY = markerY
)

private fun AquariumMaterialSelection.toDataSelection(): TankMaterialSelection =
    TankMaterialSelection(
        id = id,
        productId = productId,
        categoryKey = categoryKey,
        categoryTitle = categoryTitle,
        name = name,
        brand = brand,
        note = note
    )

private fun AquariumLivestock.toDataLivestock(): SavedAquariumLivestock =
    SavedAquariumLivestock(
        id = id,
        name = name,
        category = category,
        quantity = quantity,
        addedDateEpochDay = addedDateEpochDay,
        note = note
    )

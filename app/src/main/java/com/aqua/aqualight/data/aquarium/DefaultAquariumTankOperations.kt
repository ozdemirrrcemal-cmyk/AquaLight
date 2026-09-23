package com.aqua.aqualight.data.aquarium

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankCareSettingsOperations
import com.aqua.aqualight.application.aquarium.AquariumTankCleanupIssue
import com.aqua.aqualight.application.aquarium.AquariumTankCleanupStage
import com.aqua.aqualight.application.aquarium.AquariumTankContentsOperations
import com.aqua.aqualight.application.aquarium.AquariumTankDetailsOperations
import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.aqua.aqualight.application.aquarium.AquariumTankLifecycleOperations
import com.aqua.aqualight.application.aquarium.AquariumTankOperations
import com.aqua.aqualight.application.aquarium.AquariumTankReadOperations
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.DeleteAquariumTanksResult
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.health.AquariumHealthDataStoreManager
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultAquariumTankOperations(
    context: Context,
    tankStore: AquariumTankDataStoreManager,
    healthStore: AquariumHealthDataStoreManager,
    tankDataCleaner: OwnerTankDataCleaner,
    notificationPreferences: NotificationPreferenceUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AquariumTankOperations,
    AquariumTankReadOperations by DefaultAquariumTankReadOperations(
        tankStore
    ),
    AquariumTankLifecycleOperations by DefaultAquariumTankLifecycleOperations(
        context = context,
        tankStore = tankStore,
        tankDataCleaner = tankDataCleaner,
        dispatcher = dispatcher
    ),
    AquariumTankDetailsOperations by DefaultAquariumTankDetailsOperations(
        context = context,
        tankStore = tankStore,
        dispatcher = dispatcher
    ),
    AquariumTankContentsOperations by DefaultAquariumTankContentsOperations(
        context = context,
        tankStore = tankStore,
        healthStore = healthStore
    ),
    AquariumTankCareSettingsOperations by
        DefaultAquariumTankCareSettingsOperations(
            tankStore = tankStore,
            notificationPreferences = notificationPreferences
        )

internal fun OwnerTankDataCleaner.Result.toApplicationResult():
    DeleteAquariumTanksResult = when (this) {
    OwnerTankDataCleaner.Result.NoOp ->
        DeleteAquariumTanksResult.NoOp

    is OwnerTankDataCleaner.Result.DeleteFailed ->
        DeleteAquariumTanksResult.DeleteFailed

    is OwnerTankDataCleaner.Result.Deleted ->
        DeleteAquariumTanksResult.Deleted(
            tankIds = tankIds,
            cleanupIssues = cleanupIssues.map { issue ->
                AquariumTankCleanupIssue(
                    tankId = issue.tankId,
                    stage = issue.stage.toApplicationStage()
                )
            }
        )
}

private fun OwnerTankDataCleaner.CleanupStage.toApplicationStage():
    AquariumTankCleanupStage = when (this) {
    OwnerTankDataCleaner.CleanupStage.CARE_TASKS ->
        AquariumTankCleanupStage.CARE_TASKS

    OwnerTankDataCleaner.CleanupStage.HEALTH_RECORDS ->
        AquariumTankCleanupStage.HEALTH_RECORDS

    OwnerTankDataCleaner.CleanupStage.DEVICE_ASSIGNMENTS ->
        AquariumTankCleanupStage.DEVICE_ASSIGNMENTS
}

internal fun SavedAquariumTank.toApplicationSnapshot():
    AquariumTankSnapshot = AquariumTankSnapshot(
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
            catalogId = plant.catalogId,
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
            catalogEntryId = item.catalogEntryId,
            name = item.name,
            category = item.category,
            quantity = item.quantity,
            addedDateEpochDay = item.addedDateEpochDay,
            note = item.note
        )
    }
)

internal fun AquariumTankDraft.toDataDraft(): TankDraft =
    TankDraft(
        name = name,
        description = description,
        photoUri = photoUri,
        plants = plants.map(AquariumPlantTag::toDataTag),
        materials = materials.map(
            AquariumMaterialSelection::toDataSelection
        ),
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

internal fun AquariumPlantTag.toDataTag(): TankPlantTag =
    TankPlantTag(
        id = id,
        catalogId = catalogId,
        plantName = plantName,
        category = category,
        markerX = markerX,
        markerY = markerY
    )

internal fun AquariumMaterialSelection.toDataSelection():
    TankMaterialSelection = TankMaterialSelection(
    id = id,
    productId = productId,
    categoryKey = categoryKey,
    categoryTitle = categoryTitle,
    name = name,
    brand = brand,
    note = note
)

internal fun AquariumLivestock.toDataLivestock():
    SavedAquariumLivestock = SavedAquariumLivestock(
    id = id,
    catalogEntryId = catalogEntryId,
    name = name,
    category = category,
    quantity = quantity,
    addedDateEpochDay = addedDateEpochDay,
    note = note
)

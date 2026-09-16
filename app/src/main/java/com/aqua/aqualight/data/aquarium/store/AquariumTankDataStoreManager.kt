package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumAutomationProfile
import com.aqua.aqualight.application.aquarium.AquariumCanopyDensity
import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.application.aquarium.AquariumLivestockCategory
import com.aqua.aqualight.application.aquarium.AquariumMaterialCategory
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantics
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val MATERIAL_CATEGORY_CO2 = AquariumMaterialCategory.CO2
private const val MATERIAL_CATEGORY_GRAVEL = AquariumMaterialCategory.GRAVEL
private const val MATERIAL_CATEGORY_SUBSTRATE = AquariumMaterialCategory.SUBSTRATE

private val Context.aquariumTanksDataStore: DataStore<AquariumTanksStore> by dataStore(
    fileName = "aquarium_tanks.pb",
    serializer = AquariumTanksSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.AQUARIUM_TANKS
        )
        TankStoreRules.defaultStore()
    }
)

class AquariumTankDataStoreManager(
    private val context: Context,
    private val todayEpochDay: () -> Long = { LocalDate.now().toEpochDay() }
) {

    val tanksFlow: Flow<List<SavedAquariumTank>> =
        context.aquariumTanksDataStore.data.map { store ->
            TankStoreRules.validateStore(store)
                .tanksList
                .filter { storedTank ->
                    storedTank.belongsToCurrentUser()
                }
                .map { storedTank ->
                    storedTank.toSavedAquariumTankStrict()
                }
                .asReversed()
        }

    suspend fun addTankFromDraft(
        draft: TankDraft
    ): Long {
        val ownerUid = UserDataScope.requireCurrentUid()
        var newTankId = 0L

        context.aquariumTanksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val existingIds = currentStore.tanksList
                .filter { tank -> tank.belongsToOwner(ownerUid) }
                .mapTo(mutableSetOf()) { tank -> tank.id }
            newTankId = AquariumIdGenerator.newLong(existingIds)

            val storedTank = draft.toStoredTank(
                tankId = newTankId,
                ownerUid = ownerUid,
                createdAtMillis = System.currentTimeMillis()
            )
            TankStoreRules.validateTank(storedTank)
            currentStore.appendValidated(storedTank)
        }

        return newTankId
    }

    /**
     * Prepares all filesystem side effects before entering DataStore.updateData. The transform is
     * therefore pure and retry-safe; a failed data commit rolls the copied candidate back.
     */
    suspend fun duplicateTank(
        tankId: Long
    ): Long {
        TankStoreRules.requireValidTankId(tankId)
        val ownerUid = UserDataScope.requireCurrentUid()
        val snapshot = TankStoreRules.validateStore(context.aquariumTanksDataStore.data.first())
        val sourceSnapshot = snapshot.tanksList.firstOrNull { storedTank ->
            storedTank.id == tankId && storedTank.belongsToOwner(ownerUid)
        } ?: throw IllegalArgumentException("Tank not found for the active owner.")
        TankStoreRules.validateTank(sourceSnapshot)

        val newTankId = AquariumIdGenerator.newLong(
            snapshot.tanksList
                .filter { tank -> tank.belongsToOwner(ownerUid) }
                .mapTo(mutableSetOf()) { tank -> tank.id }
        )
        val sourcePhotoAtPreparation = sourceSnapshot.photoUri
        val duplicatedPhotoUri = AppMediaStorage.copyInternalMedia(
            context = context,
            sourceUriString = sourcePhotoAtPreparation,
            targetScope = AppMediaScope.TANK,
            ownerToken = newTankId.toString(),
            ownerUid = ownerUid
        )
        val sourceWasOwned = AppMediaStorage.isAppOwned(context, sourcePhotoAtPreparation)
        if (sourceWasOwned && duplicatedPhotoUri.isNullOrBlank()) {
            throw IllegalStateException("Tank photo could not be copied with independent ownership.")
        }

        // Freeze the active per-app language before entering the retryable DataStore transform.
        // This keeps every retry deterministic and supports non-Activity contexts on API 17+.
        val duplicateNameContext = ContextCompat.getContextForLanguage(context)

        try {
            context.aquariumTanksDataStore.updateData { currentStore ->
                requireOwnerScope(ownerUid)
                check(currentStore.tanksList.none { storedTank -> storedTank.id == newTankId }) {
                    "Generated tank id was concurrently claimed."
                }
                val sourceTank = currentStore.tanksList.firstOrNull { storedTank ->
                    storedTank.id == tankId && storedTank.belongsToOwner(ownerUid)
                } ?: throw IllegalArgumentException("Tank not found for the active owner.")
                TankStoreRules.validateTank(sourceTank)
                check(sourceTank.photoUri == sourcePhotoAtPreparation) {
                    "Tank photo changed while duplication was being prepared."
                }

                val existingNames = currentStore.tanksList
                    .filter { storedTank -> storedTank.belongsToOwner(ownerUid) }
                    .mapTo(mutableSetOf()) { storedTank -> storedTank.name }
                val duplicatedTank = sourceTank.toBuilder()
                    .setId(newTankId)
                    .setOwnerUid(ownerUid)
                    .setName(
                        createDuplicateTankName(
                            originalName = sourceTank.name,
                            existingNames = existingNames,
                            localizedContext = duplicateNameContext
                        )
                    )
                    .setPhotoUri(duplicatedPhotoUri.orEmpty().trim())
                    .setCreatedAtMillis(System.currentTimeMillis())
                    .build()
                TankStoreRules.validateTank(duplicatedTank)
                currentStore.appendValidated(duplicatedTank)
            }
        } catch (error: Throwable) {
            runCatching { AppMediaStorage.rollbackPendingMedia(context, duplicatedPhotoUri) }
            throw error
        }

        return newTankId
    }

    suspend fun deleteTanks(
        tankIds: List<Long>
    ) {
        if (tankIds.isEmpty()) {
            return
        }

        val normalizedIds = tankIds.distinct()
        normalizedIds.forEach(TankStoreRules::requireValidTankId)

        val ownerUid = UserDataScope.requireCurrentUid()
        val idsToDelete = normalizedIds.toSet()
        val photoUrisToDelete = mutableSetOf<String>()
        val deletedTankIds = mutableSetOf<Long>()

        context.aquariumTanksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)

            val remainingTanks = currentStore.tanksList.filterNot { storedTank ->
                val shouldDelete = storedTank.id in idsToDelete &&
                    storedTank.belongsToOwner(ownerUid)
                if (shouldDelete) {
                    deletedTankIds += storedTank.id
                    if (storedTank.photoUri.isNotBlank()) {
                        photoUrisToDelete += storedTank.photoUri
                    }
                }
                shouldDelete
            }

            currentStore.replaceAllValidated(remainingTanks)
        }

        AppMediaStorage.deleteInternalMedia(
            context = context,
            uriStrings = photoUrisToDelete
        )
        deletedTankIds.forEach { deletedTankId ->
            AppMediaStorage.deleteOwnerTemporaryFiles(
                context = context,
                scope = AppMediaScope.TANK,
                ownerToken = deletedTankId.toString()
            )
        }
    }

    suspend fun clearAllTanks(
        ownerUid: String? = null
    ) {
        val targetOwnerUid = ownerUid
            ?.let(::requireOwnerUid)
            ?: UserDataScope.requireCurrentUid()
        val deletedPhotoUris = mutableSetOf<String>()
        val deletedTankIds = mutableSetOf<Long>()

        context.aquariumTanksDataStore.updateData { currentStore ->
            val remainingTanks = currentStore.tanksList.filterNot { storedTank ->
                val shouldDelete = storedTank.belongsToOwner(targetOwnerUid)
                if (shouldDelete) {
                    deletedTankIds += storedTank.id
                    if (storedTank.photoUri.isNotBlank()) {
                        deletedPhotoUris += storedTank.photoUri
                    }
                }
                shouldDelete
            }
            currentStore.replaceAllValidated(remainingTanks)
        }

        AppMediaStorage.deleteInternalMedia(
            context = context,
            uriStrings = deletedPhotoUris
        )
        deletedTankIds.forEach { deletedTankId ->
            AppMediaStorage.deleteOwnerTemporaryFiles(
                context = context,
                scope = AppMediaScope.TANK,
                ownerToken = deletedTankId.toString()
            )
        }
    }

    suspend fun tanksSnapshotForOwner(
        ownerUid: String
    ): List<SavedAquariumTank> {
        val targetOwnerUid = requireOwnerUid(ownerUid)
        return context.aquariumTanksDataStore.data
            .map { store ->
                TankStoreRules.validateStore(store)
                    .tanksList
                    .filter { storedTank ->
                        storedTank.belongsToOwner(targetOwnerUid)
                    }
                    .map { storedTank ->
                        storedTank.toSavedAquariumTankStrict()
                    }
            }
            .first()
    }

    /** Returns the superseded URI after the durable store commit; no file I/O runs in the transform. */
    suspend fun updateTankPhoto(
        tankId: Long,
        photoUri: String?
    ): String? {
        val normalizedPhotoUri = photoUri.orEmpty().trim()
        var previousPhotoUri: String? = null
        updateCurrentOwnerTank(tankId) { storedTank ->
            previousPhotoUri = storedTank.photoUri.takeIf { uri ->
                uri.isNotBlank() && uri != normalizedPhotoUri
            }
            storedTank.toBuilder()
                .setPhotoUri(normalizedPhotoUri)
                .build()
        }

        return previousPhotoUri
    }

    suspend fun updateTankStyle(
        tankId: Long,
        tankStyle: String
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            storedTank.toBuilder()
                .setTankStyle(tankStyle.trim())
                .build()
        }
    }

    suspend fun updateTankDescription(
        tankId: Long,
        description: String
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            storedTank.toBuilder()
                .setDescription(description.trim())
                .build()
        }
    }

    suspend fun updateTankName(
        tankId: Long,
        name: String
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            storedTank.toBuilder()
                .setName(name.trim())
                .build()
        }
    }

    suspend fun updateTankType(
        tankId: Long,
        tankType: String
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            storedTank.toBuilder()
                .setTankType(tankType.trim())
                .build()
        }
    }

    suspend fun updateTankSize(
        tankId: Long,
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        sizeUnit: String
    ) {
        val canonicalSizeUnit = sizeUnit.trim().lowercase(Locale.US)
        updateCurrentOwnerTank(tankId) { storedTank ->
            val automation = storedTank.automationProfile.toBuilder()
            if (automation.hasWaterDepthCm() && automation.waterDepthCm > heightCm) {
                automation.clearWaterDepthCm()
            }
            if (automation.hasSubstrateDepthCm() &&
                automation.substrateDepthCm >= heightCm
            ) {
                automation.clearSubstrateDepthCm()
            }
            if (automation.hasWaterDepthCm() && automation.hasSubstrateDepthCm() &&
                automation.waterDepthCm + automation.substrateDepthCm > heightCm
            ) {
                automation.clearWaterDepthCm()
            }
            storedTank.toBuilder()
                .setWidthCm(widthCm)
                .setLengthCm(lengthCm)
                .setHeightCm(heightCm)
                .setSizeUnit(canonicalSizeUnit)
                .setAutomationProfile(automation)
                .build()
        }
    }

    suspend fun updateTankVolumeUnit(
        tankId: Long,
        volumeUnit: String
    ) {
        val canonicalVolumeUnit = when (volumeUnit.trim().lowercase(Locale.US)) {
            "l" -> "L"
            "gal" -> "gal"
            else -> volumeUnit.trim()
        }
        updateCurrentOwnerTank(tankId) { storedTank ->
            storedTank.toBuilder()
                .setVolumeUnit(canonicalVolumeUnit)
                .build()
        }
    }

    suspend fun updateTankSetupDate(
        tankId: Long,
        setupDateEpochDay: Long
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            storedTank.toBuilder()
                .setSetupDateEpochDay(setupDateEpochDay)
                .build()
        }
    }

    suspend fun updateTankPlants(
        tankId: Long,
        plants: List<TankPlantTag>
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            val existingComposition = storedTank.plantsList.associate { plant ->
                plant.id to plant.catalogId
            }
            val updatedComposition = plants.associate { plant -> plant.id to plant.catalogId }
            val compositionChanged = existingComposition != updatedComposition
            val latestChangedPlantingDay = plants.asSequence()
                .filter { plant -> existingComposition[plant.id] != plant.catalogId }
                .mapNotNull(TankPlantTag::plantedAtEpochDay)
                .maxOrNull()
            val builder = storedTank.toBuilder()
                .clearPlants()
                .addAllPlants(
                    plants.map { plant ->
                        plant.toStoredPlantTag()
                    }
                )
            if (compositionChanged) {
                val automation = storedTank.automationProfile.toBuilder()
                    .setPlantDemandOverride(
                        StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_UNKNOWN
                    )
                    .setPlantCoverage(StoredPlantCoverage.STORED_PLANT_COVERAGE_UNKNOWN)
                    .setCanopyDensity(StoredCanopyDensity.STORED_CANOPY_DENSITY_UNKNOWN)
                    .setLatestSurfaceGrowth(
                        StoredSurfaceGrowth.STORED_SURFACE_GROWTH_UNKNOWN
                    )
                    .clearLatestObservationEpochDay()
                    .clearObservedAreaPercent()
                    .setObservationLocation("")
                val lifecycleResetDay = latestChangedPlantingDay ?: todayEpochDay()
                if (!automation.hasLastMajorPlantingEpochDay() ||
                    lifecycleResetDay > automation.lastMajorPlantingEpochDay
                ) {
                    automation.setLastMajorPlantingEpochDay(lifecycleResetDay)
                }
                builder.setAutomationProfile(automation)
            }
            builder.build()
        }
    }

    suspend fun updateTankMaterialsForCategory(
        tankId: Long,
        categoryKey: String,
        materials: List<TankMaterialSelection>
    ) {
        val canonicalCategoryKey = categoryKey.trim()
        require(canonicalCategoryKey.isNotBlank()) {
            "categoryKey must not be blank"
        }
        if (materials.any { material -> material.categoryKey.trim() != canonicalCategoryKey }) {
            throw StoreInvariantViolation(
                "Every material must belong to the category being replaced."
            )
        }

        updateCurrentOwnerTank(tankId) { storedTank ->
            val previousProductIds = storedTank.materialsList
                .filter { material -> material.categoryKey == canonicalCategoryKey }
                .mapTo(sortedSetOf()) { material -> material.productId }
            val otherMaterials = storedTank.materialsList.filterNot { material ->
                material.categoryKey == canonicalCategoryKey
            }
            val updatedCategoryMaterials = materials.map { material ->
                material.toStoredMaterial()
            }
            val updatedProductIds = updatedCategoryMaterials
                .mapTo(sortedSetOf()) { material -> material.productId }
            val builder = storedTank.toBuilder()
                .clearMaterials()
                .addAllMaterials(otherMaterials)
                .addAllMaterials(updatedCategoryMaterials)
            val automation = storedTank.automationProfile.toBuilder()
            var automationChanged = false
            if (canonicalCategoryKey == MATERIAL_CATEGORY_CO2) {
                val readiness = if (updatedCategoryMaterials.isEmpty()) {
                    StoredCo2Readiness.STORED_CO2_READINESS_NOT_INSTALLED
                } else if (previousProductIds != updatedProductIds) {
                    StoredCo2Readiness.STORED_CO2_READINESS_UNKNOWN
                } else {
                    automation.co2Readiness.takeUnless { value ->
                        value == StoredCo2Readiness.STORED_CO2_READINESS_NOT_INSTALLED
                    } ?: StoredCo2Readiness.STORED_CO2_READINESS_UNKNOWN
                }
                automation.setCo2Readiness(readiness)
                automationChanged = true
            }
            if (canonicalCategoryKey in setOf(
                    MATERIAL_CATEGORY_SUBSTRATE,
                    MATERIAL_CATEGORY_GRAVEL
                ) && previousProductIds != updatedProductIds
            ) {
                val lifecycleResetDay = todayEpochDay()
                automation
                    .clearWaterDepthCm()
                    .clearSubstrateDepthCm()
                    .setPlantCoverage(StoredPlantCoverage.STORED_PLANT_COVERAGE_UNKNOWN)
                    .setCanopyDensity(StoredCanopyDensity.STORED_CANOPY_DENSITY_UNKNOWN)
                    .setLatestSurfaceGrowth(StoredSurfaceGrowth.STORED_SURFACE_GROWTH_UNKNOWN)
                    .clearLatestObservationEpochDay()
                    .clearObservedAreaPercent()
                    .setObservationLocation("")
                if (!automation.hasLastMajorPlantingEpochDay() ||
                    lifecycleResetDay > automation.lastMajorPlantingEpochDay
                ) {
                    automation.setLastMajorPlantingEpochDay(lifecycleResetDay)
                }
                automationChanged = true
            }
            if (automationChanged) {
                builder.setAutomationProfile(automation)
            }
            builder.build()
        }
    }

    suspend fun addLivestockToTank(
        tankId: Long,
        livestock: SavedAquariumLivestock
    ) {
        val storedLivestock = livestock.toStoredLivestockStrict()
        updateCurrentOwnerTank(tankId) { storedTank ->
            if (storedTank.livestockList.any { item -> item.id == storedLivestock.id }) {
                throw StoreInvariantViolation(
                    "Duplicate livestock id ${storedLivestock.id} in tank ${storedTank.id}."
                )
            }
            val updatedLivestock = storedTank.livestockList + storedLivestock
            storedTank.toBuilder()
                .addLivestock(storedLivestock)
                .setAutomationProfile(
                    storedTank.automationProfile.withLivestockInventory(updatedLivestock)
                )
                .build()
        }
    }

    suspend fun updateLivestockInTank(
        tankId: Long,
        livestock: SavedAquariumLivestock
    ) {
        val storedReplacement = livestock.toStoredLivestockStrict()
        updateCurrentOwnerTank(tankId) { storedTank ->
            var replaced = false
            val updatedLivestock = storedTank.livestockList.map { storedLivestock ->
                if (storedLivestock.id == storedReplacement.id) {
                    replaced = true
                    storedReplacement
                } else {
                    storedLivestock
                }
            }
            if (!replaced) {
                throw IllegalArgumentException(
                    "Livestock record not found in the selected tank."
                )
            }
            storedTank.toBuilder()
                .clearLivestock()
                .addAllLivestock(updatedLivestock)
                .setAutomationProfile(
                    storedTank.automationProfile.withLivestockInventory(updatedLivestock)
                )
                .build()
        }
    }

    suspend fun removeLivestockFromTank(
        tankId: Long,
        livestockId: Long
    ) {
        require(livestockId > 0L) {
            "livestockId must be positive"
        }
        updateCurrentOwnerTank(tankId) { storedTank ->
            val exists = storedTank.livestockList.any { item -> item.id == livestockId }
            if (!exists) {
                throw IllegalArgumentException(
                    "Livestock record not found in the selected tank."
                )
            }
            val updatedLivestock = storedTank.livestockList.filterNot { item ->
                item.id == livestockId
            }
            storedTank.toBuilder()
                .clearLivestock()
                .addAllLivestock(updatedLivestock)
                .setAutomationProfile(
                    storedTank.automationProfile.withLivestockInventory(updatedLivestock)
                )
                .build()
        }
    }

    suspend fun updateSmartCareEnabled(
        tankId: Long,
        enabled: Boolean
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            storedTank.toBuilder()
                .setSmartCareDisabled(!enabled)
                .build()
        }
    }

    suspend fun updateCareRemindersEnabled(
        tankId: Long,
        enabled: Boolean
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            storedTank.toBuilder()
                .setCareRemindersDisabled(!enabled)
                .build()
        }
    }

    suspend fun updateTankAutomationProfile(
        tankId: Long,
        profile: AquariumAutomationProfile
    ) {
        updateCurrentOwnerTank(tankId) { storedTank ->
            val hasCo2Component = storedTank.materialsList.any { material ->
                material.categoryKey == MATERIAL_CATEGORY_CO2
            }
            storedTank.toBuilder()
                .setAutomationProfile(
                    profile.withCo2Inventory(hasCo2Component).toStoredAutomationProfile()
                )
                .build()
        }
    }

    /** Optimistic write used when a recommendation must not overwrite concurrent tank edits. */
    suspend fun updateTankAutomationProfileIfUnchanged(
        tankId: Long,
        expectedTank: SavedAquariumTank,
        profile: AquariumAutomationProfile
    ) {
        require(expectedTank.id == tankId)
        updateCurrentOwnerTank(tankId) { storedTank ->
            check(storedTank.toSavedAquariumTankStrict() == expectedTank) {
                "Tank changed while the lighting recommendation was being prepared."
            }
            val hasCo2Component = storedTank.materialsList.any { material ->
                material.categoryKey == MATERIAL_CATEGORY_CO2
            }
            storedTank.toBuilder()
                .setAutomationProfile(
                    profile.withCo2Inventory(hasCo2Component).toStoredAutomationProfile()
                )
                .build()
        }
    }

    private suspend fun updateCurrentOwnerTank(
        tankId: Long,
        transform: (StoredTank) -> StoredTank
    ) {
        TankStoreRules.requireValidTankId(tankId)
        val ownerUid = UserDataScope.requireCurrentUid()

        context.aquariumTanksDataStore.updateData { currentStore ->
            requireOwnerScope(ownerUid)
            var replaced = false
            val updatedTanks = currentStore.tanksList.map { storedTank ->
                if (storedTank.id == tankId && storedTank.belongsToOwner(ownerUid)) {
                    replaced = true
                    val updatedTank = transform(storedTank)
                    if (updatedTank.id != storedTank.id || updatedTank.ownerUid != ownerUid) {
                        throw StoreInvariantViolation(
                            "Tank mutations must preserve record identity and owner."
                        )
                    }
                    TankStoreRules.validateTank(updatedTank)
                } else {
                    storedTank
                }
            }

            if (!replaced) {
                throw IllegalArgumentException(
                    "Tank not found for the active owner."
                )
            }
            currentStore.replaceAllValidated(updatedTanks)
        }
    }

    private fun TankDraft.toStoredTank(
        tankId: Long,
        ownerUid: String,
        createdAtMillis: Long
    ): StoredTank {
        val profile = automationProfile.withCo2Inventory(
            materials.any { material ->
                material.categoryKey.trim() == MATERIAL_CATEGORY_CO2
            }
        )
        return StoredTank.newBuilder()
            .setId(tankId)
            .setOwnerUid(ownerUid)
            .setName(name.trim())
            .setDescription(description.trim())
            .setPhotoUri(photoUri.orEmpty().trim())
            .setSetupDateEpochDay(setupDateEpochDay ?: 0L)
            .setWidthCm(widthCm)
            .setLengthCm(lengthCm)
            .setHeightCm(heightCm)
            .setSizeUnit(sizeUnit.trim().lowercase(Locale.US))
            .setVolumeUnit(
                when (volumeUnit.trim().lowercase(Locale.US)) {
                    "l" -> "L"
                    "gal" -> "gal"
                    else -> volumeUnit.trim()
                }
            )
            .setTankType(tankType.trim())
            .setTankStyle(tankStyle.trim())
            .setCreatedAtMillis(createdAtMillis)
            .setSmartCareDisabled(false)
            .setCareRemindersDisabled(false)
            .setAutomationProfile(profile.toStoredAutomationProfile())
            .addAllPlants(
                plants.map { plant ->
                    plant.toStoredPlantTag(defaultPlantedAtEpochDay = setupDateEpochDay)
                }
            )
            .addAllMaterials(
                materials.map { material ->
                    material.toStoredMaterial()
                }
            )
            .build()
    }

    private fun TankPlantTag.toStoredPlantTag(
        defaultPlantedAtEpochDay: Long? = null
    ): StoredPlantTag {
        return StoredPlantTag.newBuilder()
            .setId(id)
            .setCatalogId(catalogId.trim())
            .setPlantName(plantName.trim())
            .setCategory(category.trim())
            .setLightDemand(lightDemand.toStored())
            .setPlantedAtEpochDay(plantedAtEpochDay ?: defaultPlantedAtEpochDay ?: 0L)
            .setMarkerX(markerX)
            .setMarkerY(markerY)
            .build()
    }

    private fun TankMaterialSelection.toStoredMaterial(): StoredMaterial {
        val canonicalProductId = productId.trim()
        val canonicalCategoryKey = categoryKey.trim()
        val catalogSemantic = AquariumSubstrateSemantics.resolve(
            productId = canonicalProductId,
            categoryKey = canonicalCategoryKey
        )
        return StoredMaterial.newBuilder()
            .setId(id)
            .setProductId(canonicalProductId)
            .setCategoryKey(canonicalCategoryKey)
            .setCategoryTitle(categoryTitle.trim())
            .setName(name.trim())
            .setBrand(brand.trim())
            .setNote(note.trim())
            .setSubstrateSemantic(catalogSemantic.toStored())
            .build()
    }

    private fun SavedAquariumLivestock.toStoredLivestockStrict(): StoredLivestock {
        return StoredLivestock.newBuilder()
            .setId(id)
            .setName(name.trim())
            .setCategory(category.trim())
            .setQuantity(quantity)
            .setAddedDateEpochDay(addedDateEpochDay ?: 0L)
            .setNote(note.trim())
            .build()
    }

    private fun StoredTank.toSavedAquariumTankStrict(): SavedAquariumTank {
        TankStoreRules.validateTank(this)
        return SavedAquariumTank(
            id = id,
            ownerUid = ownerUid,
            name = name,
            description = description,
            photoUri = photoUri.takeIf(String::isNotBlank),
            setupDateEpochDay = setupDateEpochDay.takeIf { value -> value > 0L },
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm,
            sizeUnit = sizeUnit,
            volumeUnit = volumeUnit,
            tankType = tankType,
            tankStyle = tankStyle,
            createdAtMillis = createdAtMillis,
            smartCareEnabled = !smartCareDisabled,
            careRemindersEnabled = !careRemindersDisabled,
            plants = plantsList.map { plant ->
                SavedAquariumPlant(
                    id = plant.id,
                    catalogId = plant.catalogId,
                    plantName = plant.plantName,
                    category = plant.category,
                    lightDemand = plant.lightDemand.toDomain(),
                    plantedAtEpochDay = plant.plantedAtEpochDay.takeIf { value -> value > 0L },
                    markerX = plant.markerX,
                    markerY = plant.markerY
                )
            },
            materials = materialsList.map { material ->
                SavedAquariumMaterial(
                    id = material.id,
                    productId = material.productId,
                    categoryKey = material.categoryKey,
                    categoryTitle = material.categoryTitle,
                    name = material.name,
                    brand = material.brand,
                    note = material.note,
                    substrateSemantic = material.substrateSemantic.toDomain()
                )
            },
            livestock = livestockList.map { livestock ->
                SavedAquariumLivestock(
                    id = livestock.id,
                    name = livestock.name,
                    category = livestock.category,
                    quantity = livestock.quantity,
                    addedDateEpochDay = livestock.addedDateEpochDay.takeIf { value -> value > 0L },
                    note = livestock.note
                )
            },
            automationProfile = automationProfile.toDomain()
        )
    }

    private fun AquariumTanksStore.appendValidated(
        tank: StoredTank
    ): AquariumTanksStore {
        TankStoreRules.validateTank(tank)
        return TankStoreRules.validateStore(
            toBuilder()
                .addTanks(tank)
                .build()
        )
    }

    private fun AquariumTanksStore.replaceAllValidated(
        tanks: Iterable<StoredTank>
    ): AquariumTanksStore {
        return TankStoreRules.validateStore(
            toBuilder()
                .clearTanks()
                .addAllTanks(tanks)
                .build()
        )
    }

    private fun StoredTank.belongsToCurrentUser(): Boolean {
        return UserDataScope.belongsToCurrentUser(
            recordOwnerUid = ownerUid
        )
    }

    private fun StoredTank.belongsToOwner(
        ownerUid: String
    ): Boolean {
        return UserDataScope.belongsToOwner(
            recordOwnerUid = this.ownerUid,
            ownerUid = ownerUid
        )
    }

    private fun requireOwnerScope(
        expectedOwnerUid: String
    ) {
        if (UserDataScope.requireCurrentUid() != expectedOwnerUid) {
            throw StoreInvariantViolation(
                "The active owner changed while a tank write was in progress."
            )
        }
    }

    private fun requireOwnerUid(
        value: String
    ): String {
        val ownerUid = UserDataScope.normalizeOwnerUid(value)
        require(ownerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }
        return ownerUid
    }

    private fun createDuplicateTankName(
        originalName: String,
        existingNames: Set<String>,
        localizedContext: Context
    ): String {
        val baseName = originalName.trim()
        require(baseName.isNotBlank()) {
            "Source tank name must not be blank."
        }

        var copyNumber = 1
        while (true) {
            val suffixText = if (copyNumber == 1) {
                localizedContext.getString(R.string.aquarium_duplicate_name_suffix)
            } else {
                localizedContext.getString(
                    R.string.aquarium_duplicate_name_numbered_suffix,
                    copyNumber
                )
            }
            val suffix = " " + suffixText
            val maxBaseLength = TankStoreRules.MAX_NAME_CHARS - suffix.length
            if (maxBaseLength <= 0) {
                throw StoreInvariantViolation(
                    "Tank duplicate suffix exceeds the commercial name limit."
                )
            }
            val candidate = baseName
                .take(maxBaseLength)
                .trimEnd() + suffix
            if (candidate !in existingNames) {
                return candidate
            }
            copyNumber += 1
        }
    }
}

private fun AquariumAutomationProfile.toStoredAutomationProfile(): StoredTankAutomationProfile {
    val builder = StoredTankAutomationProfile.newBuilder()
        .setContractRevision(contractRevision)
        .setPlantDemandOverride(plantDemandOverride.toStored())
        .setPlantCoverage(plantCoverage.toStored())
        .setCanopyDensity(canopyDensity.toStored())
        .setCo2Readiness(co2Readiness.toStored())
        .setDaylightExposure(daylightExposure.toStored())
        .setLatestSurfaceGrowth(latestSurfaceGrowth.toStored())
        .setObservationLocation(observationLocation.trim())
        .setShelterAvailability(shelterAvailability.toStored())
    waterDepthCm?.let(builder::setWaterDepthCm)
    substrateDepthCm?.let(builder::setSubstrateDepthCm)
    daylightStartMinute?.let(builder::setDaylightStartMinute)
    daylightEndMinute?.let(builder::setDaylightEndMinute)
    preferredLightEndMinute?.let(builder::setPreferredLightEndMinute)
    lastMajorPlantingEpochDay?.let(builder::setLastMajorPlantingEpochDay)
    latestObservationEpochDay?.let(builder::setLatestObservationEpochDay)
    observedAreaPercent?.let(builder::setObservedAreaPercent)
    updatedAtMillis?.let(builder::setUpdatedAtMillis)
    return builder.build()
}

private fun AquariumAutomationProfile.withCo2Inventory(
    hasCo2Component: Boolean
): AquariumAutomationProfile = copy(
    co2Readiness = if (hasCo2Component) {
        co2Readiness.takeUnless { readiness ->
            readiness == AquariumCo2Readiness.NOT_INSTALLED
        } ?: AquariumCo2Readiness.UNKNOWN
    } else {
        AquariumCo2Readiness.NOT_INSTALLED
    }
)

private fun StoredTankAutomationProfile.withLivestockInventory(
    livestock: List<StoredLivestock>
): StoredTankAutomationProfile {
    val hasShrimp = livestock.any { item ->
        item.category == AquariumLivestockCategory.SHRIMP
    }
    val builder = toBuilder().setShelterAvailability(
        if (hasShrimp) {
            shelterAvailability.takeUnless { value ->
                value == StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_NOT_REQUIRED
            } ?: StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_UNKNOWN
        } else {
            StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_NOT_REQUIRED
        }
    )
    if (!hasShrimp && latestSurfaceGrowth ==
        StoredSurfaceGrowth.STORED_SURFACE_GROWTH_TARGET_BIOFILM
    ) {
        builder
            .setLatestSurfaceGrowth(StoredSurfaceGrowth.STORED_SURFACE_GROWTH_UNKNOWN)
            .clearLatestObservationEpochDay()
            .clearObservedAreaPercent()
            .setObservationLocation("")
    }
    return builder.build()
}

private fun StoredTankAutomationProfile.toDomain(): AquariumAutomationProfile =
    AquariumAutomationProfile(
        contractRevision = contractRevision,
        waterDepthCm = waterDepthCm.takeIf { hasWaterDepthCm() },
        substrateDepthCm = substrateDepthCm.takeIf { hasSubstrateDepthCm() },
        plantDemandOverride = plantDemandOverride.toDomain(),
        plantCoverage = plantCoverage.toDomain(),
        canopyDensity = canopyDensity.toDomain(),
        co2Readiness = co2Readiness.toDomain(),
        daylightExposure = daylightExposure.toDomain(),
        daylightStartMinute = daylightStartMinute.takeIf { hasDaylightStartMinute() },
        daylightEndMinute = daylightEndMinute.takeIf { hasDaylightEndMinute() },
        preferredLightEndMinute = preferredLightEndMinute.takeIf {
            hasPreferredLightEndMinute()
        },
        lastMajorPlantingEpochDay = lastMajorPlantingEpochDay.takeIf {
            hasLastMajorPlantingEpochDay()
        },
        latestSurfaceGrowth = latestSurfaceGrowth.toDomain(),
        latestObservationEpochDay = latestObservationEpochDay.takeIf {
            hasLatestObservationEpochDay()
        },
        observedAreaPercent = observedAreaPercent.takeIf { hasObservedAreaPercent() },
        observationLocation = observationLocation,
        shelterAvailability = shelterAvailability.toDomain(),
        updatedAtMillis = updatedAtMillis.takeIf { hasUpdatedAtMillis() }
    )

private fun AquariumPlantLightDemand.toStored(): StoredPlantLightDemand = when (this) {
    AquariumPlantLightDemand.UNKNOWN ->
        StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_UNKNOWN
    AquariumPlantLightDemand.LOW -> StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_LOW
    AquariumPlantLightDemand.MEDIUM -> StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_MEDIUM
    AquariumPlantLightDemand.HIGH -> StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_HIGH
}

private fun StoredPlantLightDemand.toDomain(): AquariumPlantLightDemand = when (this) {
    StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_LOW -> AquariumPlantLightDemand.LOW
    StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_MEDIUM -> AquariumPlantLightDemand.MEDIUM
    StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_HIGH -> AquariumPlantLightDemand.HIGH
    StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_UNKNOWN,
    StoredPlantLightDemand.UNRECOGNIZED -> AquariumPlantLightDemand.UNKNOWN
}

private fun AquariumPlantCoverage.toStored(): StoredPlantCoverage = when (this) {
    AquariumPlantCoverage.UNKNOWN -> StoredPlantCoverage.STORED_PLANT_COVERAGE_UNKNOWN
    AquariumPlantCoverage.SPARSE -> StoredPlantCoverage.STORED_PLANT_COVERAGE_SPARSE
    AquariumPlantCoverage.MEDIUM -> StoredPlantCoverage.STORED_PLANT_COVERAGE_MEDIUM
    AquariumPlantCoverage.DENSE -> StoredPlantCoverage.STORED_PLANT_COVERAGE_DENSE
}

private fun StoredPlantCoverage.toDomain(): AquariumPlantCoverage = when (this) {
    StoredPlantCoverage.STORED_PLANT_COVERAGE_SPARSE -> AquariumPlantCoverage.SPARSE
    StoredPlantCoverage.STORED_PLANT_COVERAGE_MEDIUM -> AquariumPlantCoverage.MEDIUM
    StoredPlantCoverage.STORED_PLANT_COVERAGE_DENSE -> AquariumPlantCoverage.DENSE
    StoredPlantCoverage.STORED_PLANT_COVERAGE_UNKNOWN,
    StoredPlantCoverage.UNRECOGNIZED -> AquariumPlantCoverage.UNKNOWN
}

private fun AquariumCanopyDensity.toStored(): StoredCanopyDensity = when (this) {
    AquariumCanopyDensity.UNKNOWN -> StoredCanopyDensity.STORED_CANOPY_DENSITY_UNKNOWN
    AquariumCanopyDensity.OPEN -> StoredCanopyDensity.STORED_CANOPY_DENSITY_OPEN
    AquariumCanopyDensity.PARTIAL -> StoredCanopyDensity.STORED_CANOPY_DENSITY_PARTIAL
    AquariumCanopyDensity.CLOSED -> StoredCanopyDensity.STORED_CANOPY_DENSITY_CLOSED
}

private fun StoredCanopyDensity.toDomain(): AquariumCanopyDensity = when (this) {
    StoredCanopyDensity.STORED_CANOPY_DENSITY_OPEN -> AquariumCanopyDensity.OPEN
    StoredCanopyDensity.STORED_CANOPY_DENSITY_PARTIAL -> AquariumCanopyDensity.PARTIAL
    StoredCanopyDensity.STORED_CANOPY_DENSITY_CLOSED -> AquariumCanopyDensity.CLOSED
    StoredCanopyDensity.STORED_CANOPY_DENSITY_UNKNOWN,
    StoredCanopyDensity.UNRECOGNIZED -> AquariumCanopyDensity.UNKNOWN
}

private fun AquariumCo2Readiness.toStored(): StoredCo2Readiness = when (this) {
    AquariumCo2Readiness.NOT_INSTALLED -> StoredCo2Readiness.STORED_CO2_READINESS_NOT_INSTALLED
    AquariumCo2Readiness.UNKNOWN -> StoredCo2Readiness.STORED_CO2_READINESS_UNKNOWN
    AquariumCo2Readiness.READY_AT_LIGHT_ON ->
        StoredCo2Readiness.STORED_CO2_READINESS_READY_AT_LIGHT_ON
    AquariumCo2Readiness.NOT_READY_AT_LIGHT_ON ->
        StoredCo2Readiness.STORED_CO2_READINESS_NOT_READY_AT_LIGHT_ON
}

private fun StoredCo2Readiness.toDomain(): AquariumCo2Readiness = when (this) {
    StoredCo2Readiness.STORED_CO2_READINESS_NOT_INSTALLED ->
        AquariumCo2Readiness.NOT_INSTALLED
    StoredCo2Readiness.STORED_CO2_READINESS_READY_AT_LIGHT_ON ->
        AquariumCo2Readiness.READY_AT_LIGHT_ON
    StoredCo2Readiness.STORED_CO2_READINESS_NOT_READY_AT_LIGHT_ON ->
        AquariumCo2Readiness.NOT_READY_AT_LIGHT_ON
    StoredCo2Readiness.STORED_CO2_READINESS_UNKNOWN,
    StoredCo2Readiness.UNRECOGNIZED -> AquariumCo2Readiness.UNKNOWN
}

private fun AquariumDaylightExposure.toStored(): StoredDaylightExposure = when (this) {
    AquariumDaylightExposure.UNKNOWN -> StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_UNKNOWN
    AquariumDaylightExposure.LOW -> StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_LOW
    AquariumDaylightExposure.INDIRECT ->
        StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_INDIRECT
    AquariumDaylightExposure.DIRECT -> StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_DIRECT
}

private fun StoredDaylightExposure.toDomain(): AquariumDaylightExposure = when (this) {
    StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_LOW -> AquariumDaylightExposure.LOW
    StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_INDIRECT ->
        AquariumDaylightExposure.INDIRECT
    StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_DIRECT -> AquariumDaylightExposure.DIRECT
    StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_UNKNOWN,
    StoredDaylightExposure.UNRECOGNIZED -> AquariumDaylightExposure.UNKNOWN
}

private fun AquariumSurfaceGrowth.toStored(): StoredSurfaceGrowth = when (this) {
    AquariumSurfaceGrowth.UNKNOWN -> StoredSurfaceGrowth.STORED_SURFACE_GROWTH_UNKNOWN
    AquariumSurfaceGrowth.NONE -> StoredSurfaceGrowth.STORED_SURFACE_GROWTH_NONE
    AquariumSurfaceGrowth.TARGET_BIOFILM ->
        StoredSurfaceGrowth.STORED_SURFACE_GROWTH_TARGET_BIOFILM
    AquariumSurfaceGrowth.STABLE_ALGAE -> StoredSurfaceGrowth.STORED_SURFACE_GROWTH_STABLE_ALGAE
    AquariumSurfaceGrowth.WORSENING_ALGAE ->
        StoredSurfaceGrowth.STORED_SURFACE_GROWTH_WORSENING_ALGAE
}

private fun StoredSurfaceGrowth.toDomain(): AquariumSurfaceGrowth = when (this) {
    StoredSurfaceGrowth.STORED_SURFACE_GROWTH_NONE -> AquariumSurfaceGrowth.NONE
    StoredSurfaceGrowth.STORED_SURFACE_GROWTH_TARGET_BIOFILM ->
        AquariumSurfaceGrowth.TARGET_BIOFILM
    StoredSurfaceGrowth.STORED_SURFACE_GROWTH_STABLE_ALGAE ->
        AquariumSurfaceGrowth.STABLE_ALGAE
    StoredSurfaceGrowth.STORED_SURFACE_GROWTH_WORSENING_ALGAE ->
        AquariumSurfaceGrowth.WORSENING_ALGAE
    StoredSurfaceGrowth.STORED_SURFACE_GROWTH_UNKNOWN,
    StoredSurfaceGrowth.UNRECOGNIZED -> AquariumSurfaceGrowth.UNKNOWN
}

private fun AquariumShelterAvailability.toStored(): StoredShelterAvailability = when (this) {
    AquariumShelterAvailability.NOT_REQUIRED ->
        StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_NOT_REQUIRED
    AquariumShelterAvailability.UNKNOWN ->
        StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_UNKNOWN
    AquariumShelterAvailability.ADEQUATE ->
        StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_ADEQUATE
    AquariumShelterAvailability.LIMITED ->
        StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_LIMITED
    AquariumShelterAvailability.NONE -> StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_NONE
}

private fun StoredShelterAvailability.toDomain(): AquariumShelterAvailability = when (this) {
    StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_UNKNOWN ->
        AquariumShelterAvailability.UNKNOWN
    StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_ADEQUATE ->
        AquariumShelterAvailability.ADEQUATE
    StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_LIMITED ->
        AquariumShelterAvailability.LIMITED
    StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_NONE ->
        AquariumShelterAvailability.NONE
    StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_NOT_REQUIRED,
    StoredShelterAvailability.UNRECOGNIZED -> AquariumShelterAvailability.NOT_REQUIRED
}

private fun AquariumSubstrateSemantic.toStored(): StoredSubstrateSemantic = when (this) {
    AquariumSubstrateSemantic.NOT_APPLICABLE ->
        StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NOT_APPLICABLE
    AquariumSubstrateSemantic.UNKNOWN ->
        StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_UNKNOWN
    AquariumSubstrateSemantic.INERT -> StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_INERT
    AquariumSubstrateSemantic.NUTRIENT_BASE ->
        StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NUTRIENT_BASE
    AquariumSubstrateSemantic.ACTIVE_SOIL ->
        StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_ACTIVE_SOIL
    AquariumSubstrateSemantic.ADDITIVE ->
        StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_ADDITIVE
}

private fun StoredSubstrateSemantic.toDomain(): AquariumSubstrateSemantic = when (this) {
    StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_UNKNOWN ->
        AquariumSubstrateSemantic.UNKNOWN
    StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_INERT ->
        AquariumSubstrateSemantic.INERT
    StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NUTRIENT_BASE ->
        AquariumSubstrateSemantic.NUTRIENT_BASE
    StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_ACTIVE_SOIL ->
        AquariumSubstrateSemantic.ACTIVE_SOIL
    StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_ADDITIVE ->
        AquariumSubstrateSemantic.ADDITIVE
    StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NOT_APPLICABLE,
    StoredSubstrateSemantic.UNRECOGNIZED -> AquariumSubstrateSemantic.NOT_APPLICABLE
}

package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
    private val context: Context
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
            storedTank.toBuilder()
                .setWidthCm(widthCm)
                .setLengthCm(lengthCm)
                .setHeightCm(heightCm)
                .setSizeUnit(canonicalSizeUnit)
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
            storedTank.toBuilder()
                .clearPlants()
                .addAllPlants(
                    plants.map { plant ->
                        plant.toStoredPlantTag()
                    }
                )
                .build()
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
            val otherMaterials = storedTank.materialsList.filterNot { material ->
                material.categoryKey == canonicalCategoryKey
            }
            val updatedCategoryMaterials = materials.map { material ->
                material.toStoredMaterial()
            }

            storedTank.toBuilder()
                .clearMaterials()
                .addAllMaterials(otherMaterials)
                .addAllMaterials(updatedCategoryMaterials)
                .build()
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
            storedTank.toBuilder()
                .addLivestock(storedLivestock)
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
            storedTank.toBuilder()
                .clearLivestock()
                .addAllLivestock(
                    storedTank.livestockList.filterNot { item ->
                        item.id == livestockId
                    }
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
            .addAllPlants(
                plants.map { plant ->
                    plant.toStoredPlantTag()
                }
            )
            .addAllMaterials(
                materials.map { material ->
                    material.toStoredMaterial()
                }
            )
            .build()
    }

    private fun TankPlantTag.toStoredPlantTag(): StoredPlantTag {
        return StoredPlantTag.newBuilder()
            .setId(id)
            .setPlantName(plantName.trim())
            .setCategory(category.trim())
            .setMarkerX(markerX)
            .setMarkerY(markerY)
            .build()
    }

    private fun TankMaterialSelection.toStoredMaterial(): StoredMaterial {
        return StoredMaterial.newBuilder()
            .setId(id)
            .setProductId(productId.trim())
            .setCategoryKey(categoryKey.trim())
            .setCategoryTitle(categoryTitle.trim())
            .setName(name.trim())
            .setBrand(brand.trim())
            .setNote(note.trim())
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
                    plantName = plant.plantName,
                    category = plant.category,
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
                    note = material.note
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
            }
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

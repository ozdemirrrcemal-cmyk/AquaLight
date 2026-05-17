package com.aqua.aqualight.data.tanks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.ui.tabs.aquarium.create.TankDraft
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.TankPlantTag
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.TankMaterialSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aquariumTanksDataStore: DataStore<AquariumTanksStore> by dataStore(
    fileName = "aquarium_tanks.pb",
    serializer = AquariumTanksSerializer
)

class AquariumTankDataStoreManager(
    private val context: Context
) {

    val tanksFlow: Flow<List<SavedAquariumTank>> =
        context.aquariumTanksDataStore.data.map { store ->
            store.getTanksList()
                .map { it.toSavedAquariumTank() }
                .asReversed()
        }

    suspend fun addTankFromDraft(
        draft: TankDraft
    ): Long {
        val nowMillis = System.currentTimeMillis()
        val tankId = nowMillis

        val storedTank = draft.toStoredTank(
            tankId = tankId,
            createdAtMillis = nowMillis
        )

        context.aquariumTanksDataStore.updateData { currentStore ->
            currentStore.toBuilder()
                .addTanks(storedTank)
                .build()
        }

        return tankId
    }

    suspend fun updateTankPhoto(
        tankId: Long,
        photoUri: String?
    ) {
        context.aquariumTanksDataStore.updateData { currentStore ->
            val updatedTanks = currentStore.getTanksList().map { storedTank ->
                if (storedTank.id == tankId) {
                    storedTank.toBuilder()
                        .setPhotoUri(photoUri.orEmpty())
                        .build()
                } else {
                    storedTank
                }
            }

            currentStore.toBuilder()
                .clearTanks()
                .addAllTanks(updatedTanks)
                .build()
        }
    }
	
	suspend fun updateTankStyle(
    tankId: Long,
    tankStyle: String
) {
    context.aquariumTanksDataStore.updateData { currentStore ->
        val updatedTanks = currentStore.getTanksList().map { storedTank ->
            if (storedTank.id == tankId) {
                storedTank.toBuilder()
                    .setTankStyle(tankStyle)
                    .build()
            } else {
                storedTank
            }
        }

        currentStore.toBuilder()
            .clearTanks()
            .addAllTanks(updatedTanks)
            .build()
    }
}

suspend fun updateTankDescription(
    tankId: Long,
    description: String
) {
    context.aquariumTanksDataStore.updateData { currentStore ->
        val updatedTanks = currentStore.getTanksList().map { storedTank ->
            if (storedTank.id == tankId) {
                storedTank.toBuilder()
                    .setDescription(description)
                    .build()
            } else {
                storedTank
            }
        }

        currentStore.toBuilder()
            .clearTanks()
            .addAllTanks(updatedTanks)
            .build()
    }
}
	
	suspend fun updateTankName(
    tankId: Long,
    name: String
) {
    context.aquariumTanksDataStore.updateData { currentStore ->
        val updatedTanks = currentStore.getTanksList().map { storedTank ->
            if (storedTank.id == tankId) {
                storedTank.toBuilder()
                    .setName(name.ifBlank { "Unnamed Aquarium" })
                    .build()
            } else {
                storedTank
            }
        }

        currentStore.toBuilder()
            .clearTanks()
            .addAllTanks(updatedTanks)
            .build()
    }
}

suspend fun updateTankType(
    tankId: Long,
    tankType: String
) {
    context.aquariumTanksDataStore.updateData { currentStore ->
        val updatedTanks = currentStore.getTanksList().map { storedTank ->
            if (storedTank.id == tankId) {
                storedTank.toBuilder()
                    .setTankType(tankType)
                    .build()
            } else {
                storedTank
            }
        }

        currentStore.toBuilder()
            .clearTanks()
            .addAllTanks(updatedTanks)
            .build()
    }
}

suspend fun updateTankSize(
    tankId: Long,
    widthCm: Int,
    lengthCm: Int,
    heightCm: Int
) {
    context.aquariumTanksDataStore.updateData { currentStore ->
        val updatedTanks = currentStore.getTanksList().map { storedTank ->
            if (storedTank.id == tankId) {
                storedTank.toBuilder()
                    .setWidthCm(widthCm)
                    .setLengthCm(lengthCm)
                    .setHeightCm(heightCm)
                    .build()
            } else {
                storedTank
            }
        }

        currentStore.toBuilder()
            .clearTanks()
            .addAllTanks(updatedTanks)
            .build()
    }
}

suspend fun updateTankVolumeUnit(
    tankId: Long,
    volumeUnit: String
) {
    context.aquariumTanksDataStore.updateData { currentStore ->
        val updatedTanks = currentStore.getTanksList().map { storedTank ->
            if (storedTank.id == tankId) {
                storedTank.toBuilder()
                    .setVolumeUnit(volumeUnit)
                    .build()
            } else {
                storedTank
            }
        }

        currentStore.toBuilder()
            .clearTanks()
            .addAllTanks(updatedTanks)
            .build()
    }
}

suspend fun updateTankSetupDate(
    tankId: Long,
    setupDateMillis: Long
) {
    context.aquariumTanksDataStore.updateData { currentStore ->
        val updatedTanks = currentStore.getTanksList().map { storedTank ->
            if (storedTank.id == tankId) {
                storedTank.toBuilder()
                    .setSetupDateMillis(setupDateMillis)
                    .build()
            } else {
                storedTank
            }
        }

        currentStore.toBuilder()
            .clearTanks()
            .addAllTanks(updatedTanks)
            .build()
    }
}

    suspend fun updateTankPlants(
        tankId: Long,
        plants: List<TankPlantTag>
    ) {
        context.aquariumTanksDataStore.updateData { currentStore ->
            val updatedTanks = currentStore.getTanksList().map { storedTank ->
                if (storedTank.id == tankId) {
                    storedTank.toBuilder()
                        .clearPlants()
                        .addAllPlants(
                            plants.map { plant ->
                                StoredPlantTag.newBuilder()
                                    .setId(plant.id)
                                    .setPlantName(plant.plantName)
                                    .setCategory(plant.category)
                                    .setMarkerX(plant.markerX)
                                    .setMarkerY(plant.markerY)
                                    .build()
                            }
                        )
                        .build()
                } else {
                    storedTank
                }
            }

            currentStore.toBuilder()
                .clearTanks()
                .addAllTanks(updatedTanks)
                .build()
        }
    }
	
	
	suspend fun updateTankMaterialsForCategory(
    tankId: Long,
    categoryKey: String,
    materials: List<TankMaterialSelection>
) {
    context.aquariumTanksDataStore.updateData { currentStore ->
        val updatedTanks = currentStore.getTanksList().map { storedTank ->
            if (storedTank.id == tankId) {
                val otherMaterials = storedTank.getMaterialsList()
                    .filterNot { material ->
                        material.categoryKey == categoryKey
                    }

                val updatedCategoryMaterials = materials.map { material ->
                    StoredMaterial.newBuilder()
                        .setId(material.id)
                        .setProductId(material.productId)
                        .setCategoryKey(material.categoryKey)
                        .setCategoryTitle(material.categoryTitle)
                        .setName(material.name)
                        .setBrand(material.brand)
                        .setNote(material.note)
                        .build()
                }

                storedTank.toBuilder()
                    .clearMaterials()
                    .addAllMaterials(otherMaterials)
                    .addAllMaterials(updatedCategoryMaterials)
                    .build()
            } else {
                storedTank
            }
        }

        currentStore.toBuilder()
            .clearTanks()
            .addAllTanks(updatedTanks)
            .build()
    }
}

    private fun TankDraft.toStoredTank(
        tankId: Long,
        createdAtMillis: Long
    ): StoredTank {
        return StoredTank.newBuilder()
            .setId(tankId)
            .setName(name.ifBlank { "Unnamed Aquarium" })
            .setDescription(description)
            .setPhotoUri(photoUri.orEmpty())
            .setSetupDateMillis(setupDateMillis ?: 0L)
            .setWidthCm(widthCm)
            .setLengthCm(lengthCm)
            .setHeightCm(heightCm)
            .setVolumeUnit(volumeUnit)
            .setTankType(tankType)
            .setTankStyle(tankStyle)
            .setCreatedAtMillis(createdAtMillis)
            .addAllPlants(
                plants.map { plant ->
                    StoredPlantTag.newBuilder()
                        .setId(plant.id)
                        .setPlantName(plant.plantName)
                        .setCategory(plant.category)
                        .setMarkerX(plant.markerX)
                        .setMarkerY(plant.markerY)
                        .build()
                }
            )
            .addAllMaterials(
                materials.map { material ->
                    StoredMaterial.newBuilder()
                        .setId(material.id)
                        .setProductId(material.productId)
                        .setCategoryKey(material.categoryKey)
                        .setCategoryTitle(material.categoryTitle)
                        .setName(material.name)
                        .setBrand(material.brand)
                        .setNote(material.note)
                        .build()
                }
            )
            .build()
    }

    private fun StoredTank.toSavedAquariumTank(): SavedAquariumTank {
        return SavedAquariumTank(
            id = id,
            name = name,
            description = description,
            photoUri = photoUri.ifBlank { null },
            setupDateMillis = setupDateMillis.takeIf { it > 0L },
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm,
            volumeUnit = volumeUnit,
            tankType = tankType,
            tankStyle = tankStyle,
            createdAtMillis = createdAtMillis,
            plants = getPlantsList().map { plant ->
                SavedAquariumPlant(
                    id = plant.id,
                    plantName = plant.plantName,
                    category = plant.category,
                    markerX = plant.markerX,
                    markerY = plant.markerY
                )
            },
            materials = getMaterialsList().map { material ->
                SavedAquariumMaterial(
                    id = material.id,
                    productId = material.productId,
                    categoryKey = material.categoryKey,
                    categoryTitle = material.categoryTitle,
                    name = material.name,
                    brand = material.brand,
                    note = material.note
                )
            }
        )
    }
}
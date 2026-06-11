package com.aqua.aqualight.data.tanks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        .filter { storedTank ->
          storedTank.belongsToCurrentUser()
        }
        .map { storedTank ->
          storedTank.toSavedAquariumTank()
        }
        .asReversed()
    }

  suspend fun addTankFromDraft(
    draft: TankDraft
  ): Long {
    val ownerUid = UserDataScope.requireCurrentUid()
    val nowMillis = System.currentTimeMillis()
    val tankId = nowMillis

    val storedTank = draft.toStoredTank(
      tankId = tankId,
      ownerUid = ownerUid,
      createdAtMillis = nowMillis
    )

    context.aquariumTanksDataStore.updateData { currentStore ->
      currentStore.toBuilder()
        .addTanks(storedTank)
        .build()
    }

    return tankId
  }

  suspend fun duplicateTank(
    tankId: Long
  ): Long {
    val ownerUid = UserDataScope.requireCurrentUid()
    var newTankId = System.currentTimeMillis()

    context.aquariumTanksDataStore.updateData { currentStore ->
      val sourceTank = currentStore.getTanksList().firstOrNull { storedTank ->
        storedTank.id == tankId && storedTank.belongsToOwner(ownerUid)
      } ?: throw IllegalArgumentException("Tank not found.")

      val existingIds = currentStore.getTanksList().map { storedTank ->
        storedTank.id
      }.toMutableSet()

      while (existingIds.contains(newTankId)) {
        newTankId++
      }

      val existingNames = currentStore.getTanksList()
        .filter { storedTank ->
          storedTank.belongsToOwner(ownerUid)
        }
        .map { storedTank ->
          storedTank.name
        }.toSet()

      val duplicatedTank = sourceTank.toBuilder()
        .setId(newTankId)
        .setOwnerUid(ownerUid)
        .setName(
          createDuplicateTankName(
            originalName = sourceTank.name,
            existingNames = existingNames
          )
        )
        .setCreatedAtMillis(System.currentTimeMillis())
        .build()

      currentStore.toBuilder()
        .addTanks(duplicatedTank)
        .build()
    }

    return newTankId
  }

  suspend fun deleteTanks(
    tankIds: List<Long>
  ) {
    if (tankIds.isEmpty()) {
      return
    }

    val ownerUid = UserDataScope.requireCurrentUid()
    val idsToDelete = tankIds.toSet()

    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList()
        .filterNot { storedTank ->
          storedTank.id in idsToDelete && storedTank.belongsToOwner(ownerUid)
        }

      currentStore.toBuilder()
        .clearTanks()
        .addAllTanks(updatedTanks)
        .build()
    }
  }

  suspend fun clearAllTanks(
    ownerUid: String? = null
  ) {
    val targetOwnerUid = ownerUid.orCurrentOwnerUidOrReturn()

    context.aquariumTanksDataStore.updateData { currentStore ->
      val remainingTanks = currentStore.getTanksList()
        .filterNot { storedTank ->
          storedTank.belongsToOwner(targetOwnerUid)
        }

      currentStore.toBuilder()
        .clearTanks()
        .addAllTanks(remainingTanks)
        .build()
    }
  }

  suspend fun assignLegacyTanksToOwner(
    ownerUid: String
  ) {
    val targetOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

    if (targetOwnerUid.isBlank()) {
      return
    }

    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.ownerUid.isBlank()) {
          storedTank.toBuilder()
            .setOwnerUid(targetOwnerUid)
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

  suspend fun tanksSnapshotForOwner(
    ownerUid: String
  ): List<SavedAquariumTank> {
    val targetOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

    if (targetOwnerUid.isBlank()) {
      return emptyList()
    }

    return context.aquariumTanksDataStore.data
      .map { store ->
        store.getTanksList()
          .filter { storedTank ->
            storedTank.belongsToOwner(targetOwnerUid)
          }
          .map { storedTank ->
            storedTank.toSavedAquariumTank()
          }
      }
      .first()
  }

  suspend fun updateTankPhoto(
    tankId: Long,
    photoUri: String?
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
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
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
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
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
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
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
          storedTank.toBuilder()
            .setName(
              name.ifBlank {
                "Unnamed Aquarium"
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

  suspend fun updateTankType(
    tankId: Long,
    tankType: String
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
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
    heightCm: Int,
    sizeUnit: String
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
          storedTank.toBuilder()
            .setWidthCm(widthCm)
            .setLengthCm(lengthCm)
            .setHeightCm(heightCm)
            .setSizeUnit(
              sizeUnit.ifBlank {
                "cm"
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

  suspend fun updateTankVolumeUnit(
    tankId: Long,
    volumeUnit: String
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
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
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
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
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
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
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
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

  suspend fun addLivestockToTank(
    tankId: Long,
    livestock: SavedAquariumLivestock
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
          storedTank.toBuilder()
            .addLivestock(
              livestock.toStoredLivestock()
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

  suspend fun updateLivestockInTank(
    tankId: Long,
    livestock: SavedAquariumLivestock
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
          val updatedLivestock = storedTank.getLivestockList().map { storedLivestock ->
            if (storedLivestock.id == livestock.id) {
              livestock.toStoredLivestock()
            } else {
              storedLivestock
            }
          }

          storedTank.toBuilder()
            .clearLivestock()
            .addAllLivestock(updatedLivestock)
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

  suspend fun removeLivestockFromTank(
    tankId: Long,
    livestockId: Long
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
          val updatedLivestock = storedTank.getLivestockList()
            .filterNot { storedLivestock ->
              storedLivestock.id == livestockId
            }

          storedTank.toBuilder()
            .clearLivestock()
            .addAllLivestock(updatedLivestock)
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

  suspend fun updateSmartCareEnabled(
    tankId: Long,
    enabled: Boolean
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
          storedTank.toBuilder()
            .setSmartCareDisabled(!enabled)
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

  suspend fun updateCareRemindersEnabled(
    tankId: Long,
    enabled: Boolean
  ) {
    context.aquariumTanksDataStore.updateData { currentStore ->
      val updatedTanks = currentStore.getTanksList().map { storedTank ->
        if (storedTank.id == tankId && storedTank.belongsToCurrentUser()) {
          storedTank.toBuilder()
            .setCareRemindersDisabled(!enabled)
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
    ownerUid: String,
    createdAtMillis: Long
  ): StoredTank {
    return StoredTank.newBuilder()
      .setId(tankId)
      .setOwnerUid(ownerUid)
      .setName(
        name.ifBlank {
          "Unnamed Aquarium"
        }
      )
      .setDescription(description)
      .setPhotoUri(photoUri.orEmpty())
      .setSetupDateMillis(setupDateMillis ?: 0L)
      .setWidthCm(widthCm)
      .setLengthCm(lengthCm)
      .setHeightCm(heightCm)
      .setSizeUnit(
        sizeUnit.ifBlank {
          "cm"
        }
      )
      .setVolumeUnit(volumeUnit)
      .setTankType(tankType)
      .setTankStyle(tankStyle)
      .setCreatedAtMillis(createdAtMillis)
      .setSmartCareDisabled(false)
      .setCareRemindersDisabled(false)
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

  private fun SavedAquariumLivestock.toStoredLivestock(): StoredLivestock {
    return StoredLivestock.newBuilder()
      .setId(id)
      .setName(name)
      .setCategory(category)
      .setQuantity(quantity.coerceAtLeast(1))
      .setAddedDateMillis(addedDateMillis ?: 0L)
      .setNote(note)
      .build()
  }

  private fun StoredTank.toSavedAquariumTank(): SavedAquariumTank {
    return SavedAquariumTank(
      id = id,
      ownerUid = ownerUid,
      name = name,
      description = description,
      photoUri = photoUri.ifBlank {
        null
      },
      setupDateMillis = setupDateMillis.takeIf {
        it > 0L
      },
      widthCm = widthCm,
      lengthCm = lengthCm,
      heightCm = heightCm,
      sizeUnit = sizeUnit.ifBlank {
        "cm"
      },
      volumeUnit = volumeUnit,
      tankType = tankType,
      tankStyle = tankStyle,
      createdAtMillis = createdAtMillis,
      smartCareEnabled = !smartCareDisabled,
      careRemindersEnabled = !careRemindersDisabled,
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
      },
      livestock = getLivestockList().map { livestock ->
        SavedAquariumLivestock(
          id = livestock.id,
          name = livestock.name,
          category = livestock.category,
          quantity = livestock.quantity,
          addedDateMillis = livestock.addedDateMillis.takeIf {
            it > 0L
          },
          note = livestock.note
        )
      }
    )
  }

  private fun String?.orCurrentOwnerUidOrReturn(): String {
    val explicitOwnerUid = UserDataScope.normalizeOwnerUid(this)

    if (explicitOwnerUid.isNotBlank()) {
      return explicitOwnerUid
    }

    return UserDataScope.currentUid()
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

  private fun createDuplicateTankName(
    originalName: String,
    existingNames: Set<String>
  ): String {
    val baseName = originalName.ifBlank {
      "Unnamed Aquarium"
    }

    val firstCopyName = "$baseName Copy"

    if (!existingNames.contains(firstCopyName)) {
      return firstCopyName
    }

    var copyNumber = 2

    while (existingNames.contains("$baseName Copy $copyNumber")) {
      copyNumber++
    }

    return "$baseName Copy $copyNumber"
  }
}
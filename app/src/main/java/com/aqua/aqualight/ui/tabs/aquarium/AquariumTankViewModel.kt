package com.aqua.aqualight.ui.tabs.aquarium

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank

class AquariumTankViewModel(
  application: Application
) : AndroidViewModel(application) {

  private val appContext = application.applicationContext

  private val tankDataStoreManager = AquariumTankDataStoreManager(
    appContext
  )

  private val careTaskDataStoreManager = CareTaskDataStoreManager.create(
    appContext
  )

  private val assignmentRepository =
    TankDeviceAssignmentRepositoryProvider.get(appContext)

  private val tankDataCleaner = OwnerTankDataCleaner(
    deleteTankRecords = tankDataStoreManager::deleteTanks,
    deleteCareTasksForTank = careTaskDataStoreManager::deleteTasksForTank,
    removeDeviceAssignmentsForTank = assignmentRepository::removeAssignmentsForTank
  )

  val tanks: LiveData<List<SavedAquariumTank>> =
    tankDataStoreManager.tanksFlow.asLiveData()

  suspend fun addTankFromDraft(
    draft: TankDraft
  ): Long {
    return tankDataStoreManager.addTankFromDraft(
      draft = draft
    )
  }

  suspend fun duplicateTank(
    tankId: Long
  ): Long {
    return tankDataStoreManager.duplicateTank(
      tankId = tankId
    )
  }

  suspend fun deleteTanks(
    tankIds: List<Long>
  ): OwnerTankDataCleaner.Result {
    return tankDataCleaner.deleteTanks(
      tankIds = tankIds
    )
  }

  suspend fun updateTankPhoto(
    tankId: Long,
    photoUri: String?
  ) {
    tankDataStoreManager.updateTankPhoto(
      tankId = tankId,
      photoUri = photoUri
    )
  }

  suspend fun updateTankName(
    tankId: Long,
    name: String
  ) {
    tankDataStoreManager.updateTankName(
      tankId = tankId,
      name = name
    )
  }

  suspend fun updateTankType(
    tankId: Long,
    tankType: String
  ) {
    tankDataStoreManager.updateTankType(
      tankId = tankId,
      tankType = tankType
    )
  }

  suspend fun updateTankSize(
    tankId: Long,
    widthCm: Int,
    lengthCm: Int,
    heightCm: Int,
    sizeUnit: String
  ) {
    tankDataStoreManager.updateTankSize(
      tankId = tankId,
      widthCm = widthCm,
      lengthCm = lengthCm,
      heightCm = heightCm,
      sizeUnit = sizeUnit
    )
  }

  suspend fun updateTankVolumeUnit(
    tankId: Long,
    volumeUnit: String
  ) {
    tankDataStoreManager.updateTankVolumeUnit(
      tankId = tankId,
      volumeUnit = volumeUnit
    )
  }

  suspend fun updateTankSetupDate(
    tankId: Long,
    setupDateMillis: Long
  ) {
    tankDataStoreManager.updateTankSetupDate(
      tankId = tankId,
      setupDateMillis = setupDateMillis
    )
  }

  suspend fun updateTankStyle(
    tankId: Long,
    tankStyle: String
  ) {
    tankDataStoreManager.updateTankStyle(
      tankId = tankId,
      tankStyle = tankStyle
    )
  }

  suspend fun updateTankDescription(
    tankId: Long,
    description: String
  ) {
    tankDataStoreManager.updateTankDescription(
      tankId = tankId,
      description = description
    )
  }

  suspend fun updateTankMaterialsForCategory(
    tankId: Long,
    categoryKey: String,
    materials: List<TankMaterialSelection>
  ) {
    tankDataStoreManager.updateTankMaterialsForCategory(
      tankId = tankId,
      categoryKey = categoryKey,
      materials = materials
    )
  }

  suspend fun updateTankPlants(
    tankId: Long,
    plants: List<TankPlantTag>
  ) {
    tankDataStoreManager.updateTankPlants(
      tankId = tankId,
      plants = plants
    )
  }

  suspend fun addLivestockToTank(
    tankId: Long,
    livestock: SavedAquariumLivestock
  ) {
    tankDataStoreManager.addLivestockToTank(
      tankId = tankId,
      livestock = livestock
    )
  }

  suspend fun updateLivestockInTank(
    tankId: Long,
    livestock: SavedAquariumLivestock
  ) {
    tankDataStoreManager.updateLivestockInTank(
      tankId = tankId,
      livestock = livestock
    )
  }

  suspend fun removeLivestockFromTank(
    tankId: Long,
    livestockId: Long
  ) {
    tankDataStoreManager.removeLivestockFromTank(
      tankId = tankId,
      livestockId = livestockId
    )
  }

  suspend fun updateSmartCareEnabled(
    tankId: Long,
    enabled: Boolean
  ) {
    tankDataStoreManager.updateSmartCareEnabled(
      tankId = tankId,
      enabled = enabled
    )
  }

  suspend fun updateCareRemindersEnabled(
    tankId: Long,
    enabled: Boolean
  ) {
    tankDataStoreManager.updateCareRemindersEnabled(
      tankId = tankId,
      enabled = enabled
    )

    if (enabled) {
      careTaskDataStoreManager.reschedulePendingRemindersForTank(
        tankId = tankId
      )
    } else {
      careTaskDataStoreManager.cancelPendingRemindersForTank(
        tankId = tankId
      )
    }
  }
}

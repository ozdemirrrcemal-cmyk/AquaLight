package com.aqua.aqualight.ui.tabs.aquarium

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankMaterialSelection
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager

class AquariumTankViewModel(
    private val tankDataStoreManager: AquariumTankDataStoreManager,
    private val careTaskDataStoreManager: CareTaskDataStoreManager,
    private val assignmentRepository: TankDeviceAssignmentRepository,
    private val tankDataCleaner: OwnerTankDataCleaner = OwnerTankDataCleaner(
        deleteTankRecords = tankDataStoreManager::deleteTanks,
        deleteCareTasksForTank = careTaskDataStoreManager::deleteTasksForTank,
        removeDeviceAssignmentsForTank = assignmentRepository::removeAssignmentsForTank
    )
) : ViewModel() {

    val tanks: LiveData<List<SavedAquariumTank>> =
        tankDataStoreManager.tanksFlow.asLiveData()

    suspend fun addTankFromDraft(draft: TankDraft): Long {
        return tankDataStoreManager.addTankFromDraft(draft)
    }

    suspend fun duplicateTank(tankId: Long): Long {
        return tankDataStoreManager.duplicateTank(tankId)
    }

    suspend fun deleteTanks(tankIds: List<Long>): OwnerTankDataCleaner.Result {
        return tankDataCleaner.deleteTanks(tankIds)
    }

    suspend fun updateTankPhoto(tankId: Long, photoUri: String?) {
        tankDataStoreManager.updateTankPhoto(tankId, photoUri)
    }

    suspend fun updateTankName(tankId: Long, name: String) {
        tankDataStoreManager.updateTankName(tankId, name)
    }

    suspend fun updateTankType(tankId: Long, tankType: String) {
        tankDataStoreManager.updateTankType(tankId, tankType)
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

    suspend fun updateTankVolumeUnit(tankId: Long, volumeUnit: String) {
        tankDataStoreManager.updateTankVolumeUnit(tankId, volumeUnit)
    }

    suspend fun updateTankSetupDate(tankId: Long, setupDateMillis: Long) {
        tankDataStoreManager.updateTankSetupDate(tankId, setupDateMillis)
    }

    suspend fun updateTankStyle(tankId: Long, tankStyle: String) {
        tankDataStoreManager.updateTankStyle(tankId, tankStyle)
    }

    suspend fun updateTankDescription(tankId: Long, description: String) {
        tankDataStoreManager.updateTankDescription(tankId, description)
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
        tankDataStoreManager.addLivestockToTank(tankId, livestock)
    }

    suspend fun updateLivestockInTank(
        tankId: Long,
        livestock: SavedAquariumLivestock
    ) {
        tankDataStoreManager.updateLivestockInTank(tankId, livestock)
    }

    suspend fun removeLivestockFromTank(
        tankId: Long,
        livestockId: Long
    ) {
        tankDataStoreManager.removeLivestockFromTank(tankId, livestockId)
    }

    suspend fun updateSmartCareEnabled(tankId: Long, enabled: Boolean) {
        tankDataStoreManager.updateSmartCareEnabled(tankId, enabled)
    }

    suspend fun updateCareRemindersEnabled(tankId: Long, enabled: Boolean) {
        tankDataStoreManager.updateCareRemindersEnabled(tankId, enabled)
        if (enabled) {
            careTaskDataStoreManager.reschedulePendingRemindersForTank(tankId)
        } else {
            careTaskDataStoreManager.cancelPendingRemindersForTank(tankId)
        }
    }
}

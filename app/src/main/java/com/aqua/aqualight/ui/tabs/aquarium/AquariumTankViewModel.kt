package com.aqua.aqualight.ui.tabs.aquarium

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.aqua.aqualight.application.aquarium.AquariumTankOperations
import com.aqua.aqualight.application.aquarium.AquariumTankSize
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.DeleteAquariumTanksResult

class AquariumTankViewModel(
    private val operations: AquariumTankOperations
) : ViewModel() {

    val tanks: LiveData<List<AquariumTankSnapshot>> = operations.tanks.asLiveData()

    suspend fun addTankFromDraft(draft: AquariumTankDraft): Long = operations.addTank(draft)

    suspend fun duplicateTank(tankId: Long): Long = operations.duplicateTank(tankId)

    suspend fun deleteTanks(tankIds: List<Long>): DeleteAquariumTanksResult =
        operations.deleteTanks(tankIds)

    suspend fun updateTankPhoto(tankId: Long, photoUri: String?) =
        operations.updateTankPhoto(tankId, photoUri)

    suspend fun updateTankName(tankId: Long, name: String) =
        operations.updateTankName(tankId, name)

    suspend fun updateTankType(tankId: Long, tankType: String) =
        operations.updateTankType(tankId, tankType)

    suspend fun updateTankSize(
        tankId: Long,
        widthCm: Int,
        lengthCm: Int,
        heightCm: Int,
        sizeUnit: String
    ) = operations.updateTankSize(
        tankId = tankId,
        size = AquariumTankSize(
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm,
            sizeUnit = sizeUnit
        )
    )

    suspend fun updateTankVolumeUnit(tankId: Long, volumeUnit: String) =
        operations.updateTankVolumeUnit(tankId, volumeUnit)

    suspend fun updateTankSetupDate(tankId: Long, setupDateMillis: Long) =
        operations.updateTankSetupDate(tankId, setupDateMillis)

    suspend fun updateTankStyle(tankId: Long, tankStyle: String) =
        operations.updateTankStyle(tankId, tankStyle)

    suspend fun updateTankDescription(tankId: Long, description: String) =
        operations.updateTankDescription(tankId, description)

    suspend fun updateTankMaterialsForCategory(
        tankId: Long,
        categoryKey: String,
        materials: List<AquariumMaterialSelection>
    ) = operations.updateTankMaterials(tankId, categoryKey, materials)

    suspend fun updateTankPlants(tankId: Long, plants: List<AquariumPlantTag>) =
        operations.updateTankPlants(tankId, plants)

    suspend fun addLivestockToTank(tankId: Long, livestock: AquariumLivestock) =
        operations.addLivestock(tankId, livestock)

    suspend fun updateLivestockInTank(tankId: Long, livestock: AquariumLivestock) =
        operations.updateLivestock(tankId, livestock)

    suspend fun removeLivestockFromTank(tankId: Long, livestockId: Long) =
        operations.removeLivestock(tankId, livestockId)

    suspend fun updateSmartCareEnabled(tankId: Long, enabled: Boolean) =
        operations.updateSmartCareEnabled(tankId, enabled)

    suspend fun updateCareRemindersEnabled(tankId: Long, enabled: Boolean) =
        operations.updateCareRemindersEnabled(tankId, enabled)
}

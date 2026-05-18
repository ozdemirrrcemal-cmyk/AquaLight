package com.aqua.aqualight.ui.tabs.aquarium

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import com.aqua.aqualight.ui.tabs.aquarium.create.TankDraft
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.TankMaterialSelection
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.TankPlantTag
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank

class AquariumTankViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val tankDataStoreManager = AquariumTankDataStoreManager(
        application.applicationContext
    )

    val tanks: LiveData<List<SavedAquariumTank>> =
        tankDataStoreManager.tanksFlow.asLiveData()

    suspend fun addTankFromDraft(
        draft: TankDraft
    ): Long {
        return tankDataStoreManager.addTankFromDraft(draft)
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
    ) {
        tankDataStoreManager.deleteTanks(
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
        heightCm: Int
    ) {
        tankDataStoreManager.updateTankSize(
            tankId = tankId,
            widthCm = widthCm,
            lengthCm = lengthCm,
            heightCm = heightCm
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
}
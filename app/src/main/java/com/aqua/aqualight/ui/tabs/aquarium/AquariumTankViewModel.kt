package com.aqua.aqualight.ui.tabs.aquarium

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import com.aqua.aqualight.ui.tabs.aquarium.create.TankDraft
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
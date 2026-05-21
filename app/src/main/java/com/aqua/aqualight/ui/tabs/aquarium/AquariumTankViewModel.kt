package com.aqua.aqualight.ui.tabs.aquarium

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import com.aqua.aqualight.ui.tabs.aquarium.create.TankDraft
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import kotlinx.coroutines.launch

class AquariumTankViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val tankDataStoreManager = AquariumTankDataStoreManager(
        application.applicationContext
    )

    private val _tanks = MutableLiveData<List<SavedAquariumTank>>(emptyList())
    val tanks: LiveData<List<SavedAquariumTank>> = _tanks

    init {
        viewModelScope.launch {
            tankDataStoreManager.tanksFlow.collect { tanks ->
                _tanks.value = tanks
            }
        }
    }

    suspend fun addTankFromDraft(
        draft: TankDraft
    ): Long {
        return tankDataStoreManager.addTankFromDraft(draft)
    }
}
package com.aqua.aqualight.data.aquarium

import com.aqua.aqualight.application.aquarium.AquariumTankReadOperations
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultAquariumTankReadOperations(
    tankStore: AquariumTankDataStoreManager
) : AquariumTankReadOperations {

    override val tanks: Flow<List<AquariumTankSnapshot>> =
        tankStore.tanksFlow.map { tanks ->
            tanks.map(SavedAquariumTank::toApplicationSnapshot)
        }
}

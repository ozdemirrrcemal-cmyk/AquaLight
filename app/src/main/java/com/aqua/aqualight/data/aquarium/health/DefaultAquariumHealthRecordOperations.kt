package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthRecordOperations
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestOperations
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationOperations
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservationOperations

class DefaultAquariumHealthRecordOperations(
    manager: AquariumHealthDataStoreManager,
    clock: () -> Long = System::currentTimeMillis
) : AquariumHealthRecordOperations {

    override val waterTests: AquariumWaterTestOperations =
        DefaultAquariumWaterTestOperations(
            store = manager.waterTests,
            clock = clock
        )

    override val livestockObservations: LivestockHealthObservationOperations =
        DefaultLivestockHealthObservationOperations(
            store = manager.livestockObservations,
            clock = clock
        )

    override val plantObservations: PlantHealthObservationOperations =
        DefaultPlantHealthObservationOperations(
            store = manager.plantObservations,
            clock = clock
        )
}

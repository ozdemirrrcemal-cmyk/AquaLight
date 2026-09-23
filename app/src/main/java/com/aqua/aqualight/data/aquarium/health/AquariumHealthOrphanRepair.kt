package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank

internal data class AquariumHealthOrphanRepairResult(
    val store: AquariumHealthStore,
    val removedCount: Int
)

internal object AquariumHealthOrphanRepair {

    fun repair(
        store: AquariumHealthStore,
        ownerUid: String,
        tanks: List<SavedAquariumTank>
    ): AquariumHealthOrphanRepairResult {
        val validTankIds = tanks.mapTo(mutableSetOf()) { tank -> tank.id }
        val livestockByTank = tanks.associate { tank ->
            tank.id to tank.livestock.mapTo(mutableSetOf()) { item -> item.id }
        }
        val plantsByTank = tanks.associate { tank ->
            tank.id to tank.plants.mapTo(mutableSetOf()) { plant -> plant.id }
        }

        val waterTests = store.waterTestsList.filterNot { test ->
            test.ownerUid == ownerUid && test.tankId !in validTankIds
        }
        val livestockObservations =
            store.livestockObservationsList.filterNot { observation ->
                isLivestockOrphan(
                    observation = observation,
                    ownerUid = ownerUid,
                    validTankIds = validTankIds,
                    livestockByTank = livestockByTank
                )
            }
        val plantObservations =
            store.plantObservationsList.filterNot { observation ->
                isPlantOrphan(
                    observation = observation,
                    ownerUid = ownerUid,
                    validTankIds = validTankIds,
                    plantsByTank = plantsByTank
                )
            }

        val updated = store.toBuilder()
            .clearWaterTests()
            .addAllWaterTests(waterTests)
            .clearLivestockObservations()
            .addAllLivestockObservations(livestockObservations)
            .clearPlantObservations()
            .addAllPlantObservations(plantObservations)
            .build()

        return AquariumHealthOrphanRepairResult(
            store = updated,
            removedCount =
                store.waterTestsCount - waterTests.size +
                    store.livestockObservationsCount - livestockObservations.size +
                    store.plantObservationsCount - plantObservations.size
        )
    }

    private fun isLivestockOrphan(
        observation: StoredLivestockHealthObservation,
        ownerUid: String,
        validTankIds: Set<Long>,
        livestockByTank: Map<Long, Set<Long>>
    ): Boolean {
        if (observation.ownerUid != ownerUid) return false
        if (observation.tankId !in validTankIds) return true
        return observation.livestockId > 0L &&
            observation.livestockId !in livestockByTank[observation.tankId].orEmpty()
    }

    private fun isPlantOrphan(
        observation: StoredPlantHealthObservation,
        ownerUid: String,
        validTankIds: Set<Long>,
        plantsByTank: Map<Long, Set<Long>>
    ): Boolean {
        if (observation.ownerUid != ownerUid) return false
        if (observation.tankId !in validTankIds) return true
        return observation.plantId > 0L &&
            observation.plantId !in plantsByTank[observation.tankId].orEmpty()
    }
}

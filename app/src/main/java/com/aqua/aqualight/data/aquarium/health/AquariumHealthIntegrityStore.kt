package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityJournal

internal data class TankHealthIntegritySnapshot(
    val waterTests: List<StoredAquariumWaterTest>,
    val livestockObservations: List<StoredLivestockHealthObservation>,
    val plantObservations: List<StoredPlantHealthObservation>
) {
    val recordCount: Int
        get() = waterTests.size +
            livestockObservations.size +
            plantObservations.size
}

internal class AquariumHealthIntegrityStore(
    private val access: AquariumHealthStoreAccess
) {

    suspend fun snapshotForTank(
        ownerUid: String,
        tankId: Long
    ): TankHealthIntegritySnapshot {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("tankId", tankId)
        val store = access.snapshot()
        return TankHealthIntegritySnapshot(
            waterTests = store.waterTestsList.filter { test ->
                test.ownerUid == owner && test.tankId == tankId
            },
            livestockObservations =
                store.livestockObservationsList.filter { observation ->
                    observation.ownerUid == owner &&
                        observation.tankId == tankId
                },
            plantObservations =
                store.plantObservationsList.filter { observation ->
                    observation.ownerUid == owner &&
                        observation.tankId == tankId
                }
        )
    }

    suspend fun deleteRecordsForTank(
        ownerUid: String,
        tankId: Long
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("tankId", tankId)
        access.updateOwner(owner) { current ->
            current.toBuilder()
                .clearWaterTests()
                .addAllWaterTests(
                    current.waterTestsList.filterNot { test ->
                        test.ownerUid == owner && test.tankId == tankId
                    }
                )
                .clearLivestockObservations()
                .addAllLivestockObservations(
                    current.livestockObservationsList.filterNot { observation ->
                        observation.ownerUid == owner &&
                            observation.tankId == tankId
                    }
                )
                .clearPlantObservations()
                .addAllPlantObservations(
                    current.plantObservationsList.filterNot { observation ->
                        observation.ownerUid == owner &&
                            observation.tankId == tankId
                    }
                )
                .build()
        }
    }

    suspend fun restoreSnapshotForIntegrity(
        ownerUid: String,
        tankId: Long,
        snapshot: TankHealthIntegritySnapshot
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("tankId", tankId)
        requireSnapshot(owner, tankId, snapshot)

        access.updateOwner(owner) { current ->
            TankHealthIntegrityJournal.requireWritable(owner, tankId)
            val waterIds = snapshot.waterTests.mapTo(mutableSetOf()) { it.id }
            val livestockIds =
                snapshot.livestockObservations.mapTo(mutableSetOf()) { it.id }
            val plantIds =
                snapshot.plantObservations.mapTo(mutableSetOf()) { it.id }

            current.toBuilder()
                .clearWaterTests()
                .addAllWaterTests(
                    current.waterTestsList.filterNot { test ->
                        test.ownerUid == owner && test.id in waterIds
                    } + snapshot.waterTests
                )
                .clearLivestockObservations()
                .addAllLivestockObservations(
                    current.livestockObservationsList.filterNot { observation ->
                        observation.ownerUid == owner &&
                            observation.id in livestockIds
                    } + snapshot.livestockObservations
                )
                .clearPlantObservations()
                .addAllPlantObservations(
                    current.plantObservationsList.filterNot { observation ->
                        observation.ownerUid == owner &&
                            observation.id in plantIds
                    } + snapshot.plantObservations
                )
                .build()
        }
    }

    suspend fun clearAllRecords(ownerUid: String) {
        val owner = access.requireOwnerUid(ownerUid)
        access.updateOwner(owner) { current ->
            current.toBuilder()
                .clearWaterTests()
                .addAllWaterTests(
                    current.waterTestsList.filterNot { it.ownerUid == owner }
                )
                .clearLivestockObservations()
                .addAllLivestockObservations(
                    current.livestockObservationsList.filterNot {
                        it.ownerUid == owner
                    }
                )
                .clearPlantObservations()
                .addAllPlantObservations(
                    current.plantObservationsList.filterNot {
                        it.ownerUid == owner
                    }
                )
                .build()
        }
    }

    suspend fun repairOrphanedRecords(ownerUid: String): Int {
        val owner = access.requireOwnerUid(ownerUid)
        val tanks = access.tanksForOwner(owner)
        var removedCount = 0
        access.updateOwner(owner) { current ->
            val result = AquariumHealthOrphanRepair.repair(
                store = current,
                ownerUid = owner,
                tanks = tanks
            )
            removedCount = result.removedCount
            result.store
        }
        return removedCount
    }

    private fun requireSnapshot(
        ownerUid: String,
        tankId: Long,
        snapshot: TankHealthIntegritySnapshot
    ) {
        snapshot.waterTests.forEach { test ->
            AquariumHealthStoredRecordRules.validateWaterTest(test, ownerUid)
            require(test.tankId == tankId) {
                "Health rollback water test references another tank."
            }
        }
        snapshot.livestockObservations.forEach { observation ->
            AquariumHealthStoredRecordRules.validateLivestockObservation(
                observation,
                ownerUid
            )
            require(observation.tankId == tankId) {
                "Health rollback livestock observation references another tank."
            }
        }
        snapshot.plantObservations.forEach { observation ->
            AquariumHealthStoredRecordRules.validatePlantObservation(
                observation,
                ownerUid
            )
            require(observation.tankId == tankId) {
                "Health rollback plant observation references another tank."
            }
        }
    }
}

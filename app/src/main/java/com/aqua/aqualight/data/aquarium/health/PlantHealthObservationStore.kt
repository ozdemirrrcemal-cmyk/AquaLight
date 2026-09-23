package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservation
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservationInput
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class PlantHealthObservationStore(
    private val access: AquariumHealthStoreAccess
) {

    fun observe(
        ownerUid: String,
        tankId: Long
    ): Flow<List<PlantHealthObservation>> {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("tankId", tankId)
        return access.data.map { store ->
            if (UserDataScope.currentUid() != owner) {
                emptyList()
            } else {
                store.plantObservationsList
                    .asSequence()
                    .filter { observation ->
                        observation.ownerUid == owner &&
                            observation.tankId == tankId
                    }
                    .map(AquariumHealthRecordMapper::plantObservationToApplication)
                    .sortedWith(
                        compareByDescending<PlantHealthObservation> {
                            it.observedAtMillis
                        }.thenByDescending { it.id }
                    )
                    .toList()
            }
        }
    }

    suspend fun add(
        ownerUid: String,
        input: PlantHealthObservationInput,
        nowMillis: Long
    ): Long {
        val owner = access.requireOwnerUid(ownerUid)
        val canonical = canonicalize(input)
        AquariumHealthMeasurementPolicy.validatePlantObservationInput(
            canonical,
            nowMillis
        )
        requireTarget(owner, canonical)

        var createdId = 0L
        access.updateTank(owner, canonical.tankId) { current ->
            val id = AquariumHealthStoreRules.nextUniqueId(current, nowMillis)
            createdId = id
            current.toBuilder()
                .addPlantObservations(
                    AquariumHealthRecordMapper.plantObservationToStored(
                        input = canonical,
                        metadata = HealthRecordWriteMetadata(
                            id = id,
                            ownerUid = owner,
                            createdAtMillis = nowMillis,
                            updatedAtMillis = nowMillis
                        )
                    )
                )
                .build()
        }
        check(createdId > 0L) {
            "Plant health observation creation completed without an id."
        }
        return createdId
    }

    suspend fun update(
        ownerUid: String,
        observationId: Long,
        input: PlantHealthObservationInput,
        nowMillis: Long
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("observationId", observationId)
        val canonical = canonicalize(input)
        AquariumHealthMeasurementPolicy.validatePlantObservationInput(
            canonical,
            nowMillis
        )
        requireTarget(owner, canonical)

        access.updateTank(owner, canonical.tankId) { current ->
            val target = requireNotNull(
                current.plantObservationsList.firstOrNull { observation ->
                    observation.ownerUid == owner &&
                        observation.id == observationId
                }
            ) {
                "Plant health observation not found for the active owner."
            }
            require(target.tankId == canonical.tankId) {
                "A plant observation cannot move between tanks."
            }
            current.toBuilder()
                .clearPlantObservations()
                .addAllPlantObservations(
                    current.plantObservationsList.map { stored ->
                        if (stored === target) {
                            AquariumHealthRecordMapper.plantObservationToStored(
                                input = canonical,
                                metadata = HealthRecordWriteMetadata(
                                    id = target.id,
                                    ownerUid = owner,
                                    createdAtMillis = target.createdAtMillis,
                                    updatedAtMillis = nowMillis
                                )
                            )
                        } else {
                            stored
                        }
                    }
                )
                .build()
        }
    }

    suspend fun delete(
        ownerUid: String,
        observationId: Long
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("observationId", observationId)
        val current = access.snapshot()
        val target = requireNotNull(
            current.plantObservationsList.firstOrNull { observation ->
                observation.ownerUid == owner &&
                    observation.id == observationId
            }
        ) {
            "Plant health observation not found for the active owner."
        }

        access.updateTank(owner, target.tankId) { store ->
            store.toBuilder()
                .clearPlantObservations()
                .addAllPlantObservations(
                    store.plantObservationsList.filterNot { observation ->
                        observation.ownerUid == owner &&
                            observation.id == observationId
                    }
                )
                .build()
        }
    }

    suspend fun retainPlantTargets(
        ownerUid: String,
        tankId: Long,
        validPlantIds: Set<Long>
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("tankId", tankId)
        require(validPlantIds.all { id -> id > 0L }) {
            "Plant target ids must be positive."
        }
        access.updateTank(owner, tankId) { current ->
            current.toBuilder()
                .clearPlantObservations()
                .addAllPlantObservations(
                    current.plantObservationsList.filterNot { observation ->
                        observation.ownerUid == owner &&
                            observation.tankId == tankId &&
                            observation.plantId > 0L &&
                            observation.plantId !in validPlantIds
                    }
                )
                .build()
        }
    }

    private suspend fun requireTarget(
        ownerUid: String,
        input: PlantHealthObservationInput
    ) {
        val tank = access.requireTank(ownerUid, input.tankId)
        val plantId = input.plantId
        if (plantId != null) {
            require(tank.plants.any { plant -> plant.id == plantId }) {
                "Plant health observation targets a missing plant."
            }
        }
    }

    private fun canonicalize(
        input: PlantHealthObservationInput
    ): PlantHealthObservationInput = input.copy(
        symptomKey = input.symptomKey.trim(),
        algaeTypeKey = input.algaeTypeKey?.trim(),
        note = input.note.trim()
    )
}

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class LivestockHealthObservationStore(
    private val access: AquariumHealthStoreAccess
) {

    fun observe(
        ownerUid: String,
        tankId: Long
    ): Flow<List<LivestockHealthObservation>> {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("tankId", tankId)
        return access.data.map { store ->
            if (UserDataScope.currentUid() != owner) {
                emptyList()
            } else {
                store.livestockObservationsList
                    .asSequence()
                    .filter { observation ->
                        observation.ownerUid == owner &&
                            observation.tankId == tankId
                    }
                    .map(
                        AquariumHealthRecordMapper::livestockObservationToApplication
                    )
                    .sortedWith(
                        compareByDescending<LivestockHealthObservation> {
                            it.observedAtMillis
                        }.thenByDescending { it.id }
                    )
                    .toList()
            }
        }
    }

    suspend fun add(
        ownerUid: String,
        input: LivestockHealthObservationInput,
        nowMillis: Long
    ): Long {
        val owner = access.requireOwnerUid(ownerUid)
        val canonical = canonicalize(input)
        AquariumHealthMeasurementPolicy.validateObservationInput(
            canonical,
            nowMillis
        )
        requireTarget(owner, canonical)

        var createdId = 0L
        access.updateTank(owner, canonical.tankId) { current ->
            val id = AquariumHealthStoreRules.nextUniqueId(current, nowMillis)
            createdId = id
            current.toBuilder()
                .addLivestockObservations(
                    AquariumHealthRecordMapper.livestockObservationToStored(
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
            "Livestock health observation creation completed without an id."
        }
        return createdId
    }

    suspend fun update(
        ownerUid: String,
        observationId: Long,
        input: LivestockHealthObservationInput,
        nowMillis: Long
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("observationId", observationId)
        val canonical = canonicalize(input)
        AquariumHealthMeasurementPolicy.validateObservationInput(
            canonical,
            nowMillis
        )
        requireTarget(owner, canonical)

        access.updateTank(owner, canonical.tankId) { current ->
            val target = requireNotNull(
                current.livestockObservationsList.firstOrNull { observation ->
                    observation.ownerUid == owner &&
                        observation.id == observationId
                }
            ) {
                "Livestock health observation not found for the active owner."
            }
            require(target.tankId == canonical.tankId) {
                "A livestock observation cannot move between tanks."
            }
            current.toBuilder()
                .clearLivestockObservations()
                .addAllLivestockObservations(
                    current.livestockObservationsList.map { stored ->
                        if (stored === target) {
                            AquariumHealthRecordMapper.livestockObservationToStored(
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
            current.livestockObservationsList.firstOrNull { observation ->
                observation.ownerUid == owner &&
                    observation.id == observationId
            }
        ) {
            "Livestock health observation not found for the active owner."
        }

        access.updateTank(owner, target.tankId) { store ->
            store.toBuilder()
                .clearLivestockObservations()
                .addAllLivestockObservations(
                    store.livestockObservationsList.filterNot { observation ->
                        observation.ownerUid == owner &&
                            observation.id == observationId
                    }
                )
                .build()
        }
    }

    suspend fun removeForLivestock(
        ownerUid: String,
        tankId: Long,
        livestockId: Long
    ) {
        val owner = access.requireOwnerUid(ownerUid)
        access.requirePositiveId("tankId", tankId)
        access.requirePositiveId("livestockId", livestockId)
        access.updateTank(owner, tankId) { current ->
            current.toBuilder()
                .clearLivestockObservations()
                .addAllLivestockObservations(
                    current.livestockObservationsList.filterNot { observation ->
                        observation.ownerUid == owner &&
                            observation.tankId == tankId &&
                            observation.livestockId == livestockId
                    }
                )
                .build()
        }
    }

    private suspend fun requireTarget(
        ownerUid: String,
        input: LivestockHealthObservationInput
    ) {
        val tank = access.requireTank(ownerUid, input.tankId)
        val livestockId = input.livestockId
        if (livestockId != null) {
            val livestock = requireNotNull(
                tank.livestock.firstOrNull { item -> item.id == livestockId }
            ) {
                "Livestock health observation targets missing livestock."
            }
            require(livestock.category == input.categoryKey) {
                "Livestock health observation category does not match its target."
            }
        }
    }

    private fun canonicalize(
        input: LivestockHealthObservationInput
    ): LivestockHealthObservationInput = input.copy(
        categoryKey = input.categoryKey.trim(),
        symptomKey = input.symptomKey.trim(),
        note = input.note.trim()
    )
}

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservation
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservationOperations
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import kotlinx.coroutines.flow.Flow

internal class DefaultPlantHealthObservationOperations(
    private val store: PlantHealthObservationStore,
    private val clock: () -> Long
) : PlantHealthObservationOperations {

    override fun observe(
        tankId: Long
    ): Flow<List<PlantHealthObservation>> {
        return store.observe(
            ownerUid = UserDataScope.requireCurrentUid(),
            tankId = tankId
        )
    }

    override suspend fun add(
        input: PlantHealthObservationInput
    ): Long = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(
            symptomKey = input.symptomKey.trim(),
            algaeTypeKey = input.algaeTypeKey?.trim(),
            note = input.note.trim()
        )
        AquariumHealthMeasurementPolicy.validatePlantObservationInput(
            canonical,
            now
        )
        store.add(ownerUid, canonical, now)
    }

    override suspend fun update(
        observationId: Long,
        input: PlantHealthObservationInput
    ) = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(
            symptomKey = input.symptomKey.trim(),
            algaeTypeKey = input.algaeTypeKey?.trim(),
            note = input.note.trim()
        )
        AquariumHealthMeasurementPolicy.validatePlantObservationInput(
            canonical,
            now
        )
        store.update(ownerUid, observationId, canonical, now)
    }

    override suspend fun delete(
        observationId: Long
    ) = withCurrentOwnerScope { ownerUid ->
        store.delete(ownerUid, observationId)
    }
}

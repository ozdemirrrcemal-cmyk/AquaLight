package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationOperations
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import kotlinx.coroutines.flow.Flow

internal class DefaultLivestockHealthObservationOperations(
    private val store: LivestockHealthObservationStore,
    private val clock: () -> Long
) : LivestockHealthObservationOperations {

    override fun observe(
        tankId: Long
    ): Flow<List<LivestockHealthObservation>> {
        return store.observe(
            ownerUid = UserDataScope.requireCurrentUid(),
            tankId = tankId
        )
    }

    override suspend fun add(
        input: LivestockHealthObservationInput
    ): Long = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(
            categoryKey = input.categoryKey.trim(),
            symptomKey = input.symptomKey.trim(),
            note = input.note.trim()
        )
        AquariumHealthMeasurementPolicy.validateObservationInput(canonical, now)
        store.add(ownerUid, canonical, now)
    }

    override suspend fun update(
        observationId: Long,
        input: LivestockHealthObservationInput
    ) = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(
            categoryKey = input.categoryKey.trim(),
            symptomKey = input.symptomKey.trim(),
            note = input.note.trim()
        )
        AquariumHealthMeasurementPolicy.validateObservationInput(canonical, now)
        store.update(ownerUid, observationId, canonical, now)
    }

    override suspend fun delete(
        observationId: Long
    ) = withCurrentOwnerScope { ownerUid ->
        store.delete(ownerUid, observationId)
    }
}

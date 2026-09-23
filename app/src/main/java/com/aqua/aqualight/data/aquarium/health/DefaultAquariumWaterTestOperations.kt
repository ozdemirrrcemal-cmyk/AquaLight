package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestOperations
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestRecord
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import kotlinx.coroutines.flow.Flow

internal class DefaultAquariumWaterTestOperations(
    private val store: AquariumWaterTestStore,
    private val clock: () -> Long
) : AquariumWaterTestOperations {

    override fun observe(
        tankId: Long
    ): Flow<List<AquariumWaterTestRecord>> {
        return store.observe(
            ownerUid = UserDataScope.requireCurrentUid(),
            tankId = tankId
        )
    }

    override suspend fun add(
        input: AquariumWaterTestInput
    ): Long = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(note = input.note.trim())
        AquariumHealthMeasurementPolicy.validateWaterTestInput(canonical, now)
        store.add(ownerUid, canonical, now)
    }

    override suspend fun update(
        testId: Long,
        input: AquariumWaterTestInput
    ) = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(note = input.note.trim())
        AquariumHealthMeasurementPolicy.validateWaterTestInput(canonical, now)
        store.update(ownerUid, testId, canonical, now)
    }

    override suspend fun delete(
        testId: Long
    ) = withCurrentOwnerScope { ownerUid ->
        store.delete(ownerUid, testId)
    }
}

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.AquariumHealthRecordOperations
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestRecord
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import kotlinx.coroutines.flow.Flow

class DefaultAquariumHealthRecordOperations(
    private val manager: AquariumHealthDataStoreManager,
    private val clock: () -> Long = System::currentTimeMillis
) : AquariumHealthRecordOperations {

    override fun waterTests(
        tankId: Long
    ): Flow<List<AquariumWaterTestRecord>> {
        val ownerUid = UserDataScope.requireCurrentUid()
        return manager.waterTestsForOwnerFlow(ownerUid, tankId)
    }

    override fun livestockObservations(
        tankId: Long
    ): Flow<List<LivestockHealthObservation>> {
        val ownerUid = UserDataScope.requireCurrentUid()
        return manager.observationsForOwnerFlow(ownerUid, tankId)
    }

    override suspend fun addWaterTest(
        input: AquariumWaterTestInput
    ): Long = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(note = input.note.trim())
        AquariumHealthMeasurementPolicy.validateWaterTestInput(canonical, now)
        manager.addWaterTest(
            ownerUid = ownerUid,
            input = canonical,
            nowMillis = now
        )
    }

    override suspend fun updateWaterTest(
        testId: Long,
        input: AquariumWaterTestInput
    ) = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(note = input.note.trim())
        AquariumHealthMeasurementPolicy.validateWaterTestInput(canonical, now)
        manager.updateWaterTest(
            ownerUid = ownerUid,
            testId = testId,
            input = canonical,
            nowMillis = now
        )
    }

    override suspend fun deleteWaterTest(
        testId: Long
    ) = withCurrentOwnerScope { ownerUid ->
        manager.deleteWaterTest(ownerUid, testId)
    }

    override suspend fun addLivestockObservation(
        input: LivestockHealthObservationInput
    ): Long = withCurrentOwnerScope { ownerUid ->
        val now = clock()
        val canonical = input.copy(
            categoryKey = input.categoryKey.trim(),
            symptomKey = input.symptomKey.trim(),
            note = input.note.trim()
        )
        AquariumHealthMeasurementPolicy.validateObservationInput(canonical, now)
        manager.addObservation(
            ownerUid = ownerUid,
            input = canonical,
            nowMillis = now
        )
    }

    override suspend fun updateLivestockObservation(
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
        manager.updateObservation(
            ownerUid = ownerUid,
            observationId = observationId,
            input = canonical,
            nowMillis = now
        )
    }

    override suspend fun deleteLivestockObservation(
        observationId: Long
    ) = withCurrentOwnerScope { ownerUid ->
        manager.deleteObservation(ownerUid, observationId)
    }
}

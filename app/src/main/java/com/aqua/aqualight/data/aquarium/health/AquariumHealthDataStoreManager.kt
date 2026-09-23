package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.AquariumWaterReading
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestRecord
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.ObservationIntensity
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityJournal
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aquariumHealthDataStore: DataStore<AquariumHealthStore> by dataStore(
    fileName = "aquarium_health.pb",
    serializer = AquariumHealthCommercialSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.AQUARIUM_HEALTH
        )
        AquariumHealthStoreRules.defaultStore()
    }
)

internal data class TankHealthIntegritySnapshot(
    val waterTests: List<StoredAquariumWaterTest>,
    val observations: List<StoredLivestockHealthObservation>
) {
    val recordCount: Int
        get() = waterTests.size + observations.size
}

class AquariumHealthDataStoreManager private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val tankStore = AquariumTankDataStoreManager(appContext)

    fun waterTestsForOwnerFlow(
        ownerUid: String,
        tankId: Long
    ): Flow<List<AquariumWaterTestRecord>> {
        val owner = requireOwnerUid(ownerUid)
        requirePositiveId("tankId", tankId)
        return appContext.aquariumHealthDataStore.data.map { store ->
            if (UserDataScope.currentUid() != owner) {
                return@map emptyList()
            }
            AquariumHealthStoreRules.validateStore(store)
                .waterTestsList
                .asSequence()
                .filter { test ->
                    test.ownerUid == owner && test.tankId == tankId
                }
                .map { stored -> stored.toApplicationRecord() }
                .sortedWith(
                    compareByDescending<AquariumWaterTestRecord> { it.measuredAtMillis }
                        .thenByDescending { it.id }
                )
                .toList()
        }
    }

    fun observationsForOwnerFlow(
        ownerUid: String,
        tankId: Long
    ): Flow<List<LivestockHealthObservation>> {
        val owner = requireOwnerUid(ownerUid)
        requirePositiveId("tankId", tankId)
        return appContext.aquariumHealthDataStore.data.map { store ->
            if (UserDataScope.currentUid() != owner) {
                return@map emptyList()
            }
            AquariumHealthStoreRules.validateStore(store)
                .livestockObservationsList
                .asSequence()
                .filter { observation ->
                    observation.ownerUid == owner && observation.tankId == tankId
                }
                .map { stored -> stored.toApplicationRecord() }
                .sortedWith(
                    compareByDescending<LivestockHealthObservation> { it.observedAtMillis }
                        .thenByDescending { it.id }
                )
                .toList()
        }
    }

    suspend fun addWaterTest(
        ownerUid: String,
        input: AquariumWaterTestInput,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val owner = requireOwnerUid(ownerUid)
        requireOwnerScope(owner)
        val canonicalInput = canonicalize(input)
        AquariumHealthMeasurementPolicy.validateWaterTestInput(
            canonicalInput,
            nowMillis
        )
        requireTankExists(owner, canonicalInput.tankId)

        var createdId = 0L
        appContext.aquariumHealthDataStore.updateData { current ->
            requireOwnerScope(owner)
            TankHealthIntegrityJournal.requireWritable(owner, canonicalInput.tankId)
            val id = AquariumHealthStoreRules.nextUniqueId(current, nowMillis)
            val stored = canonicalInput.toStoredWaterTest(
                id = id,
                ownerUid = owner,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis
            )
            createdId = id
            AquariumHealthStoreRules.validateStore(
                current.toBuilder()
                    .addWaterTests(stored)
                    .build()
            )
        }
        check(createdId > 0L) {
            "Aquarium health water-test creation completed without an id."
        }
        return createdId
    }

    suspend fun updateWaterTest(
        ownerUid: String,
        testId: Long,
        input: AquariumWaterTestInput,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val owner = requireOwnerUid(ownerUid)
        requireOwnerScope(owner)
        requirePositiveId("testId", testId)
        val canonicalInput = canonicalize(input)
        AquariumHealthMeasurementPolicy.validateWaterTestInput(
            canonicalInput,
            nowMillis
        )
        requireTankExists(owner, canonicalInput.tankId)

        appContext.aquariumHealthDataStore.updateData { current ->
            requireOwnerScope(owner)
            var found = false
            val updated = current.waterTestsList.map { stored ->
                if (stored.ownerUid == owner && stored.id == testId) {
                    found = true
                    TankHealthIntegrityJournal.requireWritable(owner, stored.tankId)
                    if (stored.tankId != canonicalInput.tankId) {
                        throw StoreInvariantViolation(
                            "A water-test record cannot move between tanks."
                        )
                    }
                    canonicalInput.toStoredWaterTest(
                        id = stored.id,
                        ownerUid = owner,
                        createdAtMillis = stored.createdAtMillis,
                        updatedAtMillis = nowMillis
                    )
                } else {
                    stored
                }
            }
            if (!found) {
                throw IllegalArgumentException(
                    "Water-test record not found for the active owner."
                )
            }
            AquariumHealthStoreRules.validateStore(
                current.toBuilder()
                    .clearWaterTests()
                    .addAllWaterTests(updated)
                    .build()
            )
        }
    }

    suspend fun deleteWaterTest(
        ownerUid: String,
        testId: Long
    ) {
        val owner = requireOwnerUid(ownerUid)
        requireOwnerScope(owner)
        requirePositiveId("testId", testId)

        appContext.aquariumHealthDataStore.updateData { current ->
            requireOwnerScope(owner)
            val target = current.waterTestsList.firstOrNull { stored ->
                stored.ownerUid == owner && stored.id == testId
            } ?: throw IllegalArgumentException(
                "Water-test record not found for the active owner."
            )
            TankHealthIntegrityJournal.requireWritable(owner, target.tankId)
            val remaining = current.waterTestsList.filterNot { stored ->
                stored.ownerUid == owner && stored.id == testId
            }
            AquariumHealthStoreRules.validateStore(
                current.toBuilder()
                    .clearWaterTests()
                    .addAllWaterTests(remaining)
                    .build()
            )
        }
    }

    suspend fun addObservation(
        ownerUid: String,
        input: LivestockHealthObservationInput,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val owner = requireOwnerUid(ownerUid)
        requireOwnerScope(owner)
        val canonicalInput = canonicalize(input)
        AquariumHealthMeasurementPolicy.validateObservationInput(
            canonicalInput,
            nowMillis
        )
        requireObservationTarget(owner, canonicalInput)

        var createdId = 0L
        appContext.aquariumHealthDataStore.updateData { current ->
            requireOwnerScope(owner)
            TankHealthIntegrityJournal.requireWritable(owner, canonicalInput.tankId)
            val id = AquariumHealthStoreRules.nextUniqueId(current, nowMillis)
            val stored = canonicalInput.toStoredObservation(
                id = id,
                ownerUid = owner,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis
            )
            createdId = id
            AquariumHealthStoreRules.validateStore(
                current.toBuilder()
                    .addLivestockObservations(stored)
                    .build()
            )
        }
        check(createdId > 0L) {
            "Livestock health observation creation completed without an id."
        }
        return createdId
    }

    suspend fun updateObservation(
        ownerUid: String,
        observationId: Long,
        input: LivestockHealthObservationInput,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val owner = requireOwnerUid(ownerUid)
        requireOwnerScope(owner)
        requirePositiveId("observationId", observationId)
        val canonicalInput = canonicalize(input)
        AquariumHealthMeasurementPolicy.validateObservationInput(
            canonicalInput,
            nowMillis
        )
        requireObservationTarget(owner, canonicalInput)

        appContext.aquariumHealthDataStore.updateData { current ->
            requireOwnerScope(owner)
            var found = false
            val updated = current.livestockObservationsList.map { stored ->
                if (stored.ownerUid == owner && stored.id == observationId) {
                    found = true
                    TankHealthIntegrityJournal.requireWritable(owner, stored.tankId)
                    if (stored.tankId != canonicalInput.tankId) {
                        throw StoreInvariantViolation(
                            "A livestock observation cannot move between tanks."
                        )
                    }
                    canonicalInput.toStoredObservation(
                        id = stored.id,
                        ownerUid = owner,
                        createdAtMillis = stored.createdAtMillis,
                        updatedAtMillis = nowMillis
                    )
                } else {
                    stored
                }
            }
            if (!found) {
                throw IllegalArgumentException(
                    "Livestock health observation not found for the active owner."
                )
            }
            AquariumHealthStoreRules.validateStore(
                current.toBuilder()
                    .clearLivestockObservations()
                    .addAllLivestockObservations(updated)
                    .build()
            )
        }
    }

    suspend fun deleteObservation(
        ownerUid: String,
        observationId: Long
    ) {
        val owner = requireOwnerUid(ownerUid)
        requireOwnerScope(owner)
        requirePositiveId("observationId", observationId)

        appContext.aquariumHealthDataStore.updateData { current ->
            requireOwnerScope(owner)
            val target = current.livestockObservationsList.firstOrNull { stored ->
                stored.ownerUid == owner && stored.id == observationId
            } ?: throw IllegalArgumentException(
                "Livestock health observation not found for the active owner."
            )
            TankHealthIntegrityJournal.requireWritable(owner, target.tankId)
            val remaining = current.livestockObservationsList.filterNot { stored ->
                stored.ownerUid == owner && stored.id == observationId
            }
            AquariumHealthStoreRules.validateStore(
                current.toBuilder()
                    .clearLivestockObservations()
                    .addAllLivestockObservations(remaining)
                    .build()
            )
        }
    }

    suspend fun removeObservationsForLivestock(
        ownerUid: String,
        tankId: Long,
        livestockId: Long
    ) {
        val owner = requireOwnerUid(ownerUid)
        requirePositiveId("tankId", tankId)
        requirePositiveId("livestockId", livestockId)
        UserDataScope.withOwnerUid(owner) {
            appContext.aquariumHealthDataStore.updateData { current ->
                requireOwnerScope(owner)
                TankHealthIntegrityJournal.requireWritable(owner, tankId)
                AquariumHealthStoreRules.validateStore(
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
                )
            }
        }
    }

    internal suspend fun snapshotForTank(
        ownerUid: String,
        tankId: Long
    ): TankHealthIntegritySnapshot {
        val owner = requireOwnerUid(ownerUid)
        requirePositiveId("tankId", tankId)
        val store = appContext.aquariumHealthDataStore.data
            .map(AquariumHealthStoreRules::validateStore)
            .first()
        return TankHealthIntegritySnapshot(
            waterTests = store.waterTestsList.filter { test ->
                test.ownerUid == owner && test.tankId == tankId
            },
            observations = store.livestockObservationsList.filter { observation ->
                observation.ownerUid == owner && observation.tankId == tankId
            }
        )
    }

    internal suspend fun deleteRecordsForTank(
        ownerUid: String,
        tankId: Long
    ) {
        val owner = requireOwnerUid(ownerUid)
        requirePositiveId("tankId", tankId)
        UserDataScope.withOwnerUid(owner) {
            appContext.aquariumHealthDataStore.updateData { current ->
                requireOwnerScope(owner)
                AquariumHealthStoreRules.validateStore(
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
                        .build()
                )
            }
        }
    }

    internal suspend fun restoreSnapshotForIntegrity(
        ownerUid: String,
        tankId: Long,
        snapshot: TankHealthIntegritySnapshot
    ) {
        val owner = requireOwnerUid(ownerUid)
        requirePositiveId("tankId", tankId)
        snapshot.waterTests.forEach { test ->
            AquariumHealthStoreRules.validateWaterTest(test, owner)
            require(test.tankId == tankId) {
                "Health rollback snapshot references another tank."
            }
        }
        snapshot.observations.forEach { observation ->
            AquariumHealthStoreRules.validateObservation(observation, owner)
            require(observation.tankId == tankId) {
                "Health rollback snapshot references another tank."
            }
        }

        UserDataScope.withOwnerUid(owner) {
            appContext.aquariumHealthDataStore.updateData { current ->
                requireOwnerScope(owner)
                TankHealthIntegrityJournal.requireWritable(owner, tankId)
                val waterIds = snapshot.waterTests.mapTo(mutableSetOf()) { it.id }
                val observationIds =
                    snapshot.observations.mapTo(mutableSetOf()) { it.id }
                AquariumHealthStoreRules.validateStore(
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
                                    observation.id in observationIds
                            } + snapshot.observations
                        )
                        .build()
                )
            }
        }
    }

    suspend fun clearAllRecords(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        UserDataScope.withOwnerUid(owner) {
            appContext.aquariumHealthDataStore.updateData { current ->
                requireOwnerScope(owner)
                AquariumHealthStoreRules.validateStore(
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
                        .build()
                )
            }
        }
    }

    suspend fun repairOrphanedRecords(
        ownerUid: String
    ): Int {
        val owner = requireOwnerUid(ownerUid)
        val tanks = tankStore.tanksSnapshotForOwner(owner)
        val validTankIds = tanks.mapTo(mutableSetOf()) { tank -> tank.id }
        val livestockByTank = tanks.associate { tank ->
            tank.id to tank.livestock.mapTo(mutableSetOf()) { livestock -> livestock.id }
        }
        var removedCount = 0

        UserDataScope.withOwnerUid(owner) {
            appContext.aquariumHealthDataStore.updateData { current ->
                requireOwnerScope(owner)
                val waterTests = current.waterTestsList.filterNot { test ->
                    val orphan = test.ownerUid == owner && test.tankId !in validTankIds
                    if (orphan) removedCount += 1
                    orphan
                }
                val observations = current.livestockObservationsList.filterNot { observation ->
                    val missingTank =
                        observation.ownerUid == owner &&
                            observation.tankId !in validTankIds
                    val missingLivestock =
                        observation.ownerUid == owner &&
                            observation.livestockId > 0L &&
                            observation.livestockId !in
                            livestockByTank[observation.tankId].orEmpty()
                    val orphan = missingTank || missingLivestock
                    if (orphan) removedCount += 1
                    orphan
                }
                AquariumHealthStoreRules.validateStore(
                    current.toBuilder()
                        .clearWaterTests()
                        .addAllWaterTests(waterTests)
                        .clearLivestockObservations()
                        .addAllLivestockObservations(observations)
                        .build()
                )
            }
        }
        return removedCount
    }

    private suspend fun requireTankExists(
        ownerUid: String,
        tankId: Long
    ) {
        val exists = tankStore.tanksSnapshotForOwner(ownerUid)
            .any { tank -> tank.id == tankId }
        if (!exists) {
            throw StoreInvariantViolation(
                "Aquarium health record targets a missing tank."
            )
        }
    }

    private suspend fun requireObservationTarget(
        ownerUid: String,
        input: LivestockHealthObservationInput
    ) {
        val tank = tankStore.tanksSnapshotForOwner(ownerUid)
            .firstOrNull { tank -> tank.id == input.tankId }
            ?: throw StoreInvariantViolation(
                "Livestock health observation targets a missing tank."
            )

        val livestockId = input.livestockId ?: return
        val livestock = tank.livestock.firstOrNull { item -> item.id == livestockId }
            ?: throw StoreInvariantViolation(
                "Livestock health observation targets missing livestock."
            )
        if (livestock.category != input.categoryKey) {
            throw StoreInvariantViolation(
                "Livestock health observation category does not match its target."
            )
        }
    }

    private fun canonicalize(
        input: AquariumWaterTestInput
    ): AquariumWaterTestInput = input.copy(
        readings = input.readings.toList(),
        note = input.note.trim()
    )

    private fun canonicalize(
        input: LivestockHealthObservationInput
    ): LivestockHealthObservationInput = input.copy(
        categoryKey = input.categoryKey.trim(),
        symptomKey = input.symptomKey.trim(),
        note = input.note.trim()
    )

    private fun requireOwnerUid(value: String): String {
        val owner = UserDataScope.normalizeOwnerUid(value)
        if (owner.isBlank()) {
            throw StoreInvariantViolation(
                "Aquarium health persistence requires a non-blank owner."
            )
        }
        return owner
    }

    private fun requireOwnerScope(ownerUid: String) {
        if (UserDataScope.requireCurrentUid() != ownerUid) {
            throw StoreInvariantViolation(
                "The active owner changed during an aquarium-health operation."
            )
        }
    }

    private fun requirePositiveId(field: String, value: Long) {
        if (value <= 0L) {
            throw IllegalArgumentException("$field must be positive.")
        }
    }

    private fun AquariumWaterTestInput.toStoredWaterTest(
        id: Long,
        ownerUid: String,
        createdAtMillis: Long,
        updatedAtMillis: Long
    ): StoredAquariumWaterTest = StoredAquariumWaterTest.newBuilder()
        .setId(id)
        .setOwnerUid(ownerUid)
        .setTankId(tankId)
        .setMeasuredAtMillis(measuredAtMillis)
        .addAllReadings(
            readings.map { reading ->
                StoredAquariumWaterReading.newBuilder()
                    .setParameter(reading.parameter.name)
                    .setValue(reading.value)
                    .build()
            }
        )
        .setNote(note)
        .setCreatedAtMillis(createdAtMillis)
        .setUpdatedAtMillis(updatedAtMillis)
        .build()
        .also(AquariumHealthStoreRules::validateWaterTest)

    private fun LivestockHealthObservationInput.toStoredObservation(
        id: Long,
        ownerUid: String,
        createdAtMillis: Long,
        updatedAtMillis: Long
    ): StoredLivestockHealthObservation =
        StoredLivestockHealthObservation.newBuilder()
            .setId(id)
            .setOwnerUid(ownerUid)
            .setTankId(tankId)
            .setLivestockId(livestockId ?: 0L)
            .setCategoryKey(categoryKey)
            .setSymptomKey(symptomKey)
            .setIntensity(intensity.name)
            .setObservedAtMillis(observedAtMillis)
            .setNote(note)
            .setCreatedAtMillis(createdAtMillis)
            .setUpdatedAtMillis(updatedAtMillis)
            .build()
            .also(AquariumHealthStoreRules::validateObservation)

    private fun StoredAquariumWaterTest.toApplicationRecord() =
        AquariumWaterTestRecord(
            id = id,
            tankId = tankId,
            measuredAtMillis = measuredAtMillis,
            readings = readingsList.map { stored ->
                AquariumWaterReading(
                    parameter = HealthWaterParameter.valueOf(stored.parameter),
                    value = stored.value
                )
            },
            note = note,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )

    private fun StoredLivestockHealthObservation.toApplicationRecord() =
        LivestockHealthObservation(
            id = id,
            tankId = tankId,
            livestockId = livestockId.takeIf { value -> value > 0L },
            categoryKey = categoryKey,
            symptomKey = symptomKey,
            intensity = ObservationIntensity.valueOf(intensity),
            observedAtMillis = observedAtMillis,
            note = note,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )

    companion object {
        fun create(context: Context): AquariumHealthDataStoreManager {
            return AquariumHealthDataStoreManager(context.applicationContext)
        }
    }
}

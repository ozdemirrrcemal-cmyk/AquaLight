package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation

object AquariumHealthStoreRules {

    fun defaultStore(): AquariumHealthStore = AquariumHealthStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.AQUARIUM_HEALTH_VERSION)
        .build()

    fun validateStore(store: AquariumHealthStore): AquariumHealthStore {
        CommercialStoreSchema.requireCurrent(
            storeName = "AquariumHealthStore",
            actualVersion = store.schemaVersion,
            expectedVersion = CommercialStoreSchema.AQUARIUM_HEALTH_VERSION
        )

        val ownerScopedIds = mutableSetOf<Pair<String, Long>>()

        store.waterTestsList.forEach { test ->
            AquariumHealthStoredRecordRules.validateWaterTest(test)
            requireUnique(
                ownerScopedIds,
                AquariumHealthStoreValueRules.canonicalOwnerUid(test.ownerUid),
                test.id
            )
        }
        store.livestockObservationsList.forEach { observation ->
            AquariumHealthStoredRecordRules.validateLivestockObservation(observation)
            requireUnique(
                ownerScopedIds,
                AquariumHealthStoreValueRules.canonicalOwnerUid(observation.ownerUid),
                observation.id
            )
        }
        store.plantObservationsList.forEach { observation ->
            AquariumHealthStoredRecordRules.validatePlantObservation(observation)
            requireUnique(
                ownerScopedIds,
                AquariumHealthStoreValueRules.canonicalOwnerUid(observation.ownerUid),
                observation.id
            )
        }

        return store
    }

    fun nextUniqueId(
        store: AquariumHealthStore,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val maxExistingId = sequenceOf(
            store.waterTestsList.asSequence().map(StoredAquariumWaterTest::getId),
            store.livestockObservationsList.asSequence()
                .map(StoredLivestockHealthObservation::getId),
            store.plantObservationsList.asSequence()
                .map(StoredPlantHealthObservation::getId)
        ).flatten().maxOrNull() ?: 0L

        val next = maxOf(nowMillis, maxExistingId + 1L)
        AquariumHealthStoreValueRules.requirePositive(
            "generated health record id",
            next
        )
        return next
    }

    private fun requireUnique(
        existing: MutableSet<Pair<String, Long>>,
        ownerUid: String,
        recordId: Long
    ) {
        if (!existing.add(ownerUid to recordId)) {
            throw StoreInvariantViolation(
                "Duplicate aquarium-health record id $recordId for owner $ownerUid."
            )
        }
    }
}

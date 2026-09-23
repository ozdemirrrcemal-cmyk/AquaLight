package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumLivestockTaxonomy
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.application.aquarium.health.LivestockHealthSymptomCatalog
import com.aqua.aqualight.application.aquarium.health.ObservationIntensity
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AquariumHealthStoreRulesTest {

    @Test
    fun defaultStoreUsesFirstHealthSchema() {
        assertEquals(
            CommercialStoreSchema.AQUARIUM_HEALTH_VERSION,
            AquariumHealthStoreRules.defaultStore().schemaVersion
        )
    }

    @Test
    fun partialWaterTestIsValid() {
        val store = AquariumHealthStoreRules.defaultStore()
            .toBuilder()
            .addWaterTests(validWaterTest())
            .build()

        assertEquals(
            store,
            AquariumHealthStoreRules.validateStore(store)
        )
    }

    @Test
    fun unsupportedSchemaFailsClosed() {
        val store = AquariumHealthStoreRules.defaultStore()
            .toBuilder()
            .setSchemaVersion(99)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            AquariumHealthStoreRules.validateStore(store)
        }
    }

    @Test
    fun duplicateWaterTestIdForOwnerIsRejected() {
        val test = validWaterTest()
        val store = AquariumHealthStoreRules.defaultStore()
            .toBuilder()
            .addWaterTests(test)
            .addWaterTests(test)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            AquariumHealthStoreRules.validateStore(store)
        }
    }

    @Test
    fun duplicateReadingParameterIsRejected() {
        val reading = StoredAquariumWaterReading.newBuilder()
            .setParameter(HealthWaterParameter.PH.name)
            .setValue(7.0)
            .build()
        val test = validWaterTest().toBuilder()
            .clearReadings()
            .addReadings(reading)
            .addReadings(reading)
            .build()
        val store = AquariumHealthStoreRules.defaultStore()
            .toBuilder()
            .addWaterTests(test)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            AquariumHealthStoreRules.validateStore(store)
        }
    }

    @Test
    fun categoryLevelObservationIsValid() {
        val store = AquariumHealthStoreRules.defaultStore()
            .toBuilder()
            .addLivestockObservations(validObservation())
            .build()

        assertEquals(
            store,
            AquariumHealthStoreRules.validateStore(store)
        )
    }

    @Test
    fun unknownSymptomIsRejected() {
        val observation = validObservation().toBuilder()
            .setSymptomKey("unknown_symptom")
            .build()
        val store = AquariumHealthStoreRules.defaultStore()
            .toBuilder()
            .addLivestockObservations(observation)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            AquariumHealthStoreRules.validateStore(store)
        }
    }

    private fun validWaterTest(): StoredAquariumWaterTest =
        StoredAquariumWaterTest.newBuilder()
            .setId(101L)
            .setOwnerUid(OWNER_UID)
            .setTankId(TANK_ID)
            .setMeasuredAtMillis(MEASURED_MILLIS)
            .addReadings(
                StoredAquariumWaterReading.newBuilder()
                    .setParameter(HealthWaterParameter.PH.name)
                    .setValue(7.2)
            )
            .setNote("")
            .setCreatedAtMillis(CREATED_MILLIS)
            .setUpdatedAtMillis(CREATED_MILLIS)
            .build()

    private fun validObservation(): StoredLivestockHealthObservation =
        StoredLivestockHealthObservation.newBuilder()
            .setId(201L)
            .setOwnerUid(OWNER_UID)
            .setTankId(TANK_ID)
            .setLivestockId(0L)
            .setCategoryKey(AquariumLivestockTaxonomy.FISH)
            .setSymptomKey(
                LivestockHealthSymptomCatalog.FISH_SURFACE_GASPING
            )
            .setIntensity(ObservationIntensity.MODERATE.name)
            .setObservedAtMillis(MEASURED_MILLIS)
            .setNote("")
            .setCreatedAtMillis(CREATED_MILLIS)
            .setUpdatedAtMillis(CREATED_MILLIS)
            .build()

    private companion object {
        const val OWNER_UID = "health-owner"
        const val TANK_ID = 7L
        const val MEASURED_MILLIS = 1_767_225_600_000L
        const val CREATED_MILLIS = 1_767_229_200_000L
    }
}

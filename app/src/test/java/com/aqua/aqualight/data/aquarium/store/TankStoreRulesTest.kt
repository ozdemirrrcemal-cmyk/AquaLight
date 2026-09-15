package com.aqua.aqualight.data.aquarium.store

import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TankStoreRulesTest {

    @Test
    fun validCommercialStoreIsAccepted() {
        val store = TankStoreRules.defaultStore().toBuilder()
            .addTanks(validTank(id = 11L, ownerUid = "owner-a"))
            .addTanks(validTank(id = 11L, ownerUid = "owner-b"))
            .build()

        assertEquals(store, TankStoreRules.validateStore(store))
    }

    @Test
    fun duplicateTankIdForSameOwnerIsRejected() {
        val store = TankStoreRules.defaultStore().toBuilder()
            .addTanks(validTank(id = 22L, ownerUid = "owner-a"))
            .addTanks(validTank(id = 22L, ownerUid = "owner-a"))
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateStore(store)
        }
    }

    @Test
    fun missingCommercialSchemaVersionIsRejected() {
        val store = AquariumTanksStore.newBuilder()
            .addTanks(validTank(id = 33L, ownerUid = "owner-a"))
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateStore(store)
        }
    }

    @Test
    fun invalidDimensionAndNestedDuplicateIdsAreRejected() {
        val invalidDimension = validTank(id = 44L, ownerUid = "owner-a")
            .toBuilder()
            .setWidthCm(0)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(invalidDimension)
        }

        val duplicatePlantIds = validTank(id = 45L, ownerUid = "owner-a")
            .toBuilder()
            .addPlants(validPlant(id = 9L))
            .addPlants(validPlant(id = 9L))
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(duplicatePlantIds)
        }
    }

    @Test
    fun schemaConstantMatchesCurrentCommercialVersion() {
        assertEquals(
            CommercialStoreSchema.AQUARIUM_TANKS_VERSION,
            TankStoreRules.defaultStore().schemaVersion
        )
    }

    @Test
    fun completeSemanticSmartLightProfileIsAccepted() {
        val profile = StoredSmartLightProfile.newBuilder()
            .setPlantDensity("HIGH")
            .setHighestPlantLightDemand("MEDIUM")
            .setCo2Status("ACTIVE")
            .setIsActiveSoil(true)
            .setWaterDepthCm(42)
            .setFixtureMountHeightCm(12)
            .setPreferredViewingStartMinuteOfDay(600)
            .setPreferredViewingEndMinuteOfDay(1_200)
            .setAlgaeObservation("NONE")
            .setPlantStressObservation("MILD")
            .setObservationDateEpochDay(20_500L)
            .build()
        val tank = validTank(id = 51L, ownerUid = "owner-a")
            .toBuilder()
            .setSmartLightProfile(profile)
            .build()

        assertEquals(tank, TankStoreRules.validateTank(tank))
    }

    @Test
    fun unknownSmartLightSemanticCodeIsRejectedWithoutSubstitution() {
        val tank = validTank(id = 52L, ownerUid = "owner-a")
            .toBuilder()
            .setSmartLightProfile(
                StoredSmartLightProfile.newBuilder()
                    .setPlantDensity("DENSE")
                    .build()
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(tank)
        }
    }

    @Test
    fun partialViewingWindowIsRejected() {
        val tank = validTank(id = 53L, ownerUid = "owner-a")
            .toBuilder()
            .setSmartLightProfile(
                StoredSmartLightProfile.newBuilder()
                    .setPreferredViewingStartMinuteOfDay(600)
                    .build()
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(tank)
        }
    }

    @Test
    fun observationsWithoutExplicitDateAreRejected() {
        val tank = validTank(id = 54L, ownerUid = "owner-a")
            .toBuilder()
            .setSmartLightProfile(
                StoredSmartLightProfile.newBuilder()
                    .setAlgaeObservation("NONE")
                    .setPlantStressObservation("NONE")
                    .build()
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(tank)
        }
    }

    @Test
    fun outOfDomainSmartLightMeasurementIsRejected() {
        val tank = validTank(id = 55L, ownerUid = "owner-a")
            .toBuilder()
            .setSmartLightProfile(
                StoredSmartLightProfile.newBuilder()
                    .setWaterDepthCm(201)
                    .build()
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(tank)
        }
    }

    private fun validTank(
        id: Long,
        ownerUid: String
    ): StoredTank = StoredTank.newBuilder()
        .setId(id)
        .setOwnerUid(ownerUid)
        .setName("Display Tank")
        .setDescription("Commercial test tank")
        .setSetupDateEpochDay(20_454L)
        .setWidthCm(60)
        .setLengthCm(40)
        .setHeightCm(40)
        .setSizeUnit("cm")
        .setVolumeUnit("L")
        .setTankType("Planted")
        .setTankStyle("Nature Aquarium")
        .setCreatedAtMillis(1_767_225_600_000L)
        .build()

    private fun validPlant(id: Long): StoredPlantTag = StoredPlantTag.newBuilder()
        .setId(id)
        .setPlantName("Anubias")
        .setCategory("Rhizome")
        .setMarkerX(0.5f)
        .setMarkerY(0.5f)
        .build()
}

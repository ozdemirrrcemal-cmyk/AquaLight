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

        val missingPlantCatalogId = validTank(id = 46L, ownerUid = "owner-a")
            .toBuilder()
            .addPlants(
                validPlant(id = 10L)
                    .toBuilder()
                    .clearCatalogId()
                    .build()
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(missingPlantCatalogId)
        }
    }

    @Test
    fun livestockRequiresStrictCatalogOrCustomIdentity() {
        val blankIdentity = validTank(id = 47L, ownerUid = "owner-a")
            .toBuilder()
            .addLivestock(
                validLivestock(id = 101L)
                    .toBuilder()
                    .clearCatalogEntryId()
                    .build()
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(blankIdentity)
        }

        val mismatchedCustomIdentity = validTank(id = 48L, ownerUid = "owner-a")
            .toBuilder()
            .addLivestock(
                validLivestock(id = 102L)
                    .toBuilder()
                    .setCatalogEntryId("custom:999")
                    .build()
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(mismatchedCustomIdentity)
        }

        val validCustomIdentity = validTank(id = 49L, ownerUid = "owner-a")
            .toBuilder()
            .addLivestock(validLivestock(id = 103L))
            .build()

        assertEquals(validCustomIdentity, TankStoreRules.validateTank(validCustomIdentity))
    }

    @Test
    fun healthTimelineRequiresBaselineAndChronologicalChecks() {
        val firstAt = 1_767_225_601_000L
        val health = StoredLivestockHealthObservation.newBuilder()
            .setId(201L)
            .setLivestockId(101L)
            .setLivestockName("Custom Fish")
            .setLivestockCategory("Fish")
            .setCatalogEntryId("custom:101")
            .setAffectedCount(1)
            .setObservedAtMillis(firstAt)
            .setStartedAtMillis(firstAt)
            .addSymptomCodes("surface_frequency_change")
            .setTrendCode("same")
            .setBaselineChangeCode("yes")
            .build()
        val tank = validTank(id = 60L, ownerUid = "owner-a").toBuilder()
            .addLivestock(validLivestock(id = 101L))
            .addHealthObservations(health)
            .build()

        assertEquals(tank, TankStoreRules.validateTank(tank))

        val withoutBaseline = tank.toBuilder().setHealthObservations(0,
            health.toBuilder().clearBaselineChangeCode()).build()
        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(withoutBaseline)
        }

        val earlyCheck = StoredLivestockHealthCheck.newBuilder()
            .setId(301L)
            .setObservedAtMillis(firstAt - 1L)
            .setAffectedCount(1)
            .setTrendCode("same")
            .build()
        val nonChronological = tank.toBuilder().setHealthObservations(0,
            health.toBuilder().addChecks(earlyCheck)).build()
        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(nonChronological)
        }
    }

    @Test
    fun schemaConstantMatchesCurrentCommercialVersion() {
        assertEquals(
            CommercialStoreSchema.AQUARIUM_TANKS_VERSION,
            TankStoreRules.defaultStore().schemaVersion
        )
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
        .setCatalogId("plant:anubias_barteri")
        .setPlantName("Anubias")
        .setCategory("Rhizome")
        .setMarkerX(0.5f)
        .setMarkerY(0.5f)
        .build()

    private fun validLivestock(id: Long): StoredLivestock = StoredLivestock.newBuilder()
        .setId(id)
        .setName("Custom Fish")
        .setCategory("Fish")
        .setQuantity(1)
        .setAddedDateEpochDay(20_454L)
        .setNote("")
        .setCatalogEntryId("custom:$id")
        .build()
}

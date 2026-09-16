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
    fun catalogPlantDemandMismatchIsRejected() {
        val mismatchedPlant = validPlant(id = 91L).toBuilder()
            .setLightDemand(StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_HIGH)
            .build()
        val tank = validTank(id = 91L, ownerUid = "owner-a").toBuilder()
            .addPlants(mismatchedPlant)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(tank)
        }
    }

    @Test
    fun substrateSemanticMismatchIsRejected() {
        val mismatchedSubstrate = StoredMaterial.newBuilder()
            .setId(92L)
            .setProductId("substrate_ada_tourmaline_bc")
            .setCategoryKey("substrate")
            .setCategoryTitle("Substrate")
            .setName("Tourmaline BC")
            .setSubstrateSemantic(
                StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_ACTIVE_SOIL
            )
            .build()
        val tank = validTank(id = 92L, ownerUid = "owner-a").toBuilder()
            .addMaterials(mismatchedSubstrate)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(tank)
        }
    }

    @Test
    fun catalogProductInWrongMaterialCategoryIsRejected() {
        val wrongCategory = StoredMaterial.newBuilder()
            .setId(93L)
            .setProductId("substrate_chihiros_aquasoil_9l")
            .setCategoryKey("gravel")
            .setCategoryTitle("Gravel")
            .setName("Chihiros Aqua Soil 9L")
            .setSubstrateSemantic(
                StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_UNKNOWN
            )
            .build()
        val tank = validTank(id = 93L, ownerUid = "owner-a").toBuilder()
            .addMaterials(wrongCategory)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(tank)
        }
    }

    @Test
    fun nonCanonicalLivestockCategoryIsRejected() {
        val unsupportedLivestock = StoredLivestock.newBuilder()
            .setId(94L)
            .setName("Cherry shrimp")
            .setCategory("Prawn")
            .setQuantity(10)
            .build()
        val tank = validTank(id = 94L, ownerUid = "owner-a").toBuilder()
            .addLivestock(unsupportedLivestock)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateTank(tank)
        }
    }

    @Test
    fun nonCanonicalMaterialCategoryIsRejected() {
        val unsupportedMaterial = StoredMaterial.newBuilder()
            .setId(95L)
            .setProductId("custom:co2-system")
            .setCategoryKey("CO2")
            .setCategoryTitle("CO2")
            .setName("Custom CO2 system")
            .setSubstrateSemantic(
                StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NOT_APPLICABLE
            )
            .build()
        val tank = validTank(id = 95L, ownerUid = "owner-a").toBuilder()
            .addMaterials(unsupportedMaterial)
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
        .setAutomationProfile(
            StoredTankAutomationProfile.newBuilder()
                .setContractRevision(1)
                .setCo2Readiness(
                    StoredCo2Readiness.STORED_CO2_READINESS_NOT_INSTALLED
                )
                .build()
        )
        .build()

    private fun validPlant(id: Long): StoredPlantTag = StoredPlantTag.newBuilder()
        .setId(id)
        .setCatalogId("plant:anubias_barteri_var_nana")
        .setPlantName("Anubias")
        .setCategory("Rhizome")
        .setLightDemand(StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_LOW)
        .setMarkerX(0.5f)
        .setMarkerY(0.5f)
        .build()
}

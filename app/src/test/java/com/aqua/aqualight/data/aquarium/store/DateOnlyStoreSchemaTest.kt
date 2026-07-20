package com.aqua.aqualight.data.aquarium.store

import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DateOnlyStoreSchemaTest {

    @Test
    fun firstCommercialTankStoreSchemaUsesDateOnlyContract() {
        assertEquals(1, CommercialStoreSchema.AQUARIUM_TANKS_VERSION)

        val store = TankStoreRules.defaultStore().toBuilder()
            .addTanks(
                StoredTank.newBuilder()
                    .setId(1L)
                    .setOwnerUid("owner-date-only")
                    .setName("Date-only Tank")
                    .setDescription("")
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
            )
            .build()

        assertEquals(store, TankStoreRules.validateStore(store))
    }

    @Test
    fun unknownTankStoreVersionIsRejected() {
        val unsupportedStore = AquariumTanksStore.newBuilder()
            .setSchemaVersion(CommercialStoreSchema.AQUARIUM_TANKS_VERSION + 1)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateStore(unsupportedStore)
        }
    }
}

package com.aqua.aqualight.data.aquarium.store

import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DateOnlyStoreSchemaTest {

    @Test
    fun currentTankStoreSchemaIsDateOnlyV2() {
        assertEquals(2, CommercialStoreSchema.AQUARIUM_TANKS_VERSION)
        assertEquals(
            CommercialStoreSchema.AQUARIUM_TANKS_VERSION,
            TankStoreRules.defaultStore().schemaVersion
        )
    }

    @Test
    fun timestampBasedV1TankStoreIsRejectedInsteadOfMigrated() {
        val v1Store = AquariumTanksStore.newBuilder()
            .setSchemaVersion(1)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            TankStoreRules.validateStore(v1Store)
        }
    }
}

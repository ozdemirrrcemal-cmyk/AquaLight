package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.store.CommercialStoreSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LivestockHealthStoreTest {
    @Test
    fun roundTripPreservesOwnerAndObservationFields() {
        val store = store(record())
        val restored = LivestockHealthStore.parseFrom(store.toByteArray())

        assertEquals(store, validateLivestockHealthStore(restored))
        assertEquals("owner-a", restored.getObservations(0).ownerUid)
        assertEquals(listOf("surface", "appetite"),
            restored.getObservations(0).symptomKeysList)
    }

    @Test
    fun duplicateIdForSameOwnerIsRejectedButOtherOwnerIsIndependent() {
        val first = record()
        assertThrows(StoreInvariantViolation::class.java) {
            validateLivestockHealthStore(store(first, first))
        }
        assertThrows(StoreInvariantViolation::class.java) {
            validateLivestockHealthStore(
                store(first, first.toBuilder().setId(10).build())
            )
        }
        validateLivestockHealthStore(store(first, first.toBuilder().setOwnerUid("owner-b").build()))
    }

    @Test
    fun invalidCountsAndUnpairedClosureAreRejected() {
        assertThrows(StoreInvariantViolation::class.java) {
            validateLivestockHealthStore(store(record().toBuilder().setAffectedCount(5).build()))
        }
        assertThrows(StoreInvariantViolation::class.java) {
            validateLivestockHealthStore(store(record().toBuilder().setCloseReason("manual").build()))
        }
    }

    @Test
    fun completeCheckHistorySurvivesProtoRoundTrip() {
        val check = StoredLivestockCheck.newBuilder()
            .setRequestId("check-a")
            .setStatus("recovered")
            .setAffectedCount(1)
            .setCheckedAtMillis(1_800_000_001_000)
            .setNote("Recovered after care")
            .build()
        val closed = record().toBuilder().addChecks(check)
            .setClosedAtMillis(1_800_000_002_000)
            .setCloseReason("recovered").build()

        val restored = LivestockHealthStore.parseFrom(store(closed).toByteArray())
        validateLivestockHealthStore(restored)
        assertEquals(check, restored.getObservations(0).getChecks(0))
    }

    private fun store(vararg records: StoredLivestockObservation): LivestockHealthStore =
        LivestockHealthStore.newBuilder()
            .setSchemaVersion(CommercialStoreSchema.LIVESTOCK_HEALTH_VERSION)
            .addAllObservations(records.asList()).build()

    private fun record(): StoredLivestockObservation =
        StoredLivestockObservation.newBuilder()
            .setId(9).setOwnerUid("owner-a").setTankId(10).setLivestockId(11)
            .setRequestId("request-a")
            .addSymptomKeys("surface").addSymptomKeys("appetite")
            .setOnsetKey("onset_today").setAffectedCount(1).setTotalCount(4)
            .setCreatedAtMillis(1_800_000_000_000)
            .build()
}

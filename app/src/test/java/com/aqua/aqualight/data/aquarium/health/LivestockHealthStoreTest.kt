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
            .addAllPhotoUris(listOf("photo-a", "photo-b", "photo-c"))
            .build()
        val closed = record().toBuilder().addChecks(check)
            .setClosedAtMillis(1_800_000_002_000)
            .setCloseReason("recovered").build()

        val restored = LivestockHealthStore.parseFrom(store(closed).toByteArray())
        validateLivestockHealthStore(restored)
        assertEquals(check, restored.getObservations(0).getChecks(0))
        assertEquals(
            listOf("photo-a", "photo-b", "photo-c"),
            restored.getObservations(0).getChecks(0).photoUrisList
        )
    }

    @Test
    fun checkInputWithMoreThanThreePhotosIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            validateCheck(
                com.aqua.aqualight.application.aquarium.health.LivestockCheckInput(
                    requestId = "check-input-too-many",
                    status = "same",
                    affectedCount = 1,
                    checkedAtMillis = System.currentTimeMillis(),
                    note = "",
                    photoUris = listOf("a", "b", "c", "d")
                )
            )
        }
    }

    @Test
    fun checkWithMoreThanThreePhotosIsRejected() {
        val check = StoredLivestockCheck.newBuilder()
            .setRequestId("check-too-many-photos")
            .setStatus("same")
            .setAffectedCount(1)
            .setCheckedAtMillis(1_800_000_001_000)
            .addAllPhotoUris(listOf("a", "b", "c", "d"))
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            validateLivestockHealthStore(
                store(record().toBuilder().addChecks(check).build())
            )
        }
    }

    @Test
    fun previousLivestockHealthSchemaVersionIsRejected() {
        val previous = store(record()).toBuilder()
            .setSchemaVersion(CommercialStoreSchema.LIVESTOCK_HEALTH_VERSION - 1)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            validateLivestockHealthStore(previous)
        }
    }

    @Test
    fun mediaReferencesIncludeObservationAndAllCheckPhotos() {
        val check = StoredLivestockCheck.newBuilder()
            .setRequestId("check-media")
            .setStatus("same")
            .setAffectedCount(1)
            .setCheckedAtMillis(1_800_000_001_000)
            .addAllPhotoUris(listOf("check-a", "check-b", "check-c"))
            .build()
        val record = record().toBuilder()
            .addAllPhotoUris(listOf("observation-a", "observation-b"))
            .addChecks(check)
            .build()

        assertEquals(
            listOf(
                "observation-a",
                "observation-b",
                "check-a",
                "check-b",
                "check-c"
            ),
            record.mediaUris()
        )
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

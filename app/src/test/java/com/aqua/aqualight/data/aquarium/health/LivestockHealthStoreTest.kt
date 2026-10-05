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

    @Test
    fun evaluationRoundTripPreservesWaterAnalysisSnapshot() {
        val evaluation = initialEvaluation().toBuilder()
            .setEvaluatedAtMillis(1_800_000_000_300)
            .setHasWaterAnalysis(true)
            .setWaterAnalysisId(44L)
            .setWaterAnalysisMeasuredAtMillis(1_800_000_000_100)
            .setWaterAnalysisCreatedAtMillis(1_800_000_000_200)
            .setHasTemperature(true)
            .setTemperatureCelsius(26.5)
            .setTemperatureSource("MANUAL")
            .addWaterMeasurements(
                StoredLivestockEvaluationWaterMeasurement.newBuilder()
                    .setParameter("PH")
                    .setValue(7.2)
                    .setMethod("MANUAL")
                    .setBasis("PH")
                    .setUnit("NONE")
                    .setHasCanonicalValue(true)
                    .setCanonicalValue(7.2)
                    .setCanonicalBasis("PH")
                    .setCanonicalUnit("NONE")
                    .build()
            )
            .build()
        val restored = LivestockHealthStore.parseFrom(
            store(record().toBuilder().setEvaluations(0, evaluation).build()).toByteArray()
        )
        val snapshot = validateLivestockHealthStore(restored)
            .getObservations(0)
            .toSnapshot()
            .evaluations
            .single()

        assertEquals(44L, snapshot.waterAnalysis?.id)
        assertEquals(26.5, snapshot.waterAnalysis?.temperatureCelsius)
        assertEquals(7.2, snapshot.waterAnalysis?.measurements?.single()?.canonicalValue)
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
            .addEvaluations(initialEvaluation())
            .build()

    private fun initialEvaluation(): StoredLivestockEvaluation =
        StoredLivestockEvaluation.newBuilder()
            .setId(90L)
            .setRequestId("evaluation-a")
            .setTrigger("INITIAL_OBSERVATION")
            .setEvaluatedAtMillis(1_800_000_000_000)
            .setAffectedCount(1)
            .setBasedOnCheckCount(0)
            .setBasedOnLatestCheckAtMillis(0L)
            .build()
}

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.AquariumWaterReading
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthSymptomCatalog
import com.aqua.aqualight.application.aquarium.health.ObservationIntensity
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

        val waterIds = mutableSetOf<Pair<String, Long>>()
        store.waterTestsList.forEach { test ->
            validateWaterTest(test)
            val key = canonicalOwnerUid(test.ownerUid) to test.id
            if (!waterIds.add(key)) {
                violation("Duplicate aquarium-health water-test id ${test.id}.")
            }
        }

        val observationIds = mutableSetOf<Pair<String, Long>>()
        store.livestockObservationsList.forEach { observation ->
            validateObservation(observation)
            val key = canonicalOwnerUid(observation.ownerUid) to observation.id
            if (!observationIds.add(key)) {
                violation(
                    "Duplicate aquarium-health livestock-observation id " +
                        "${observation.id}."
                )
            }
        }

        return store
    }

    fun validateWaterTest(
        test: StoredAquariumWaterTest,
        expectedOwnerUid: String? = null
    ): StoredAquariumWaterTest {
        requirePositive("waterTest.id", test.id)
        val ownerUid = canonicalOwnerUid(test.ownerUid)
        requireExpectedOwner(ownerUid, expectedOwnerUid)
        requirePositive("waterTest.tankId", test.tankId)
        requireTimestamp("waterTest.measuredAtMillis", test.measuredAtMillis)
        requireTimestamp("waterTest.createdAtMillis", test.createdAtMillis)
        requireTimestamp("waterTest.updatedAtMillis", test.updatedAtMillis)
        requireChronology(
            eventField = "waterTest.measuredAtMillis",
            eventAtMillis = test.measuredAtMillis,
            createdAtMillis = test.createdAtMillis,
            updatedAtMillis = test.updatedAtMillis
        )
        requireCanonicalNote(test.note)

        val readings = test.readingsList.map { stored ->
            val parameter = runCatching {
                HealthWaterParameter.valueOf(stored.parameter)
            }.getOrElse {
                violation("Water test contains an unsupported parameter.")
            }
            AquariumWaterReading(
                parameter = parameter,
                value = stored.value
            )
        }
        validateReadings(readings)
        return test
    }

    fun validateObservation(
        observation: StoredLivestockHealthObservation,
        expectedOwnerUid: String? = null
    ): StoredLivestockHealthObservation {
        requirePositive("observation.id", observation.id)
        val ownerUid = canonicalOwnerUid(observation.ownerUid)
        requireExpectedOwner(ownerUid, expectedOwnerUid)
        requirePositive("observation.tankId", observation.tankId)
        if (observation.livestockId != 0L) {
            requirePositive("observation.livestockId", observation.livestockId)
        }

        val intensity = runCatching {
            ObservationIntensity.valueOf(observation.intensity)
        }.getOrElse {
            violation("Observation contains an unsupported intensity.")
        }

        validateObservationIdentity(
            categoryKey = observation.categoryKey,
            symptomKey = observation.symptomKey,
            intensity = intensity,
            tankId = observation.tankId,
            livestockId = observation.livestockId.takeIf { id -> id > 0L },
            observedAtMillis = observation.observedAtMillis,
            note = observation.note
        )

        requireTimestamp("observation.observedAtMillis", observation.observedAtMillis)
        requireTimestamp("observation.createdAtMillis", observation.createdAtMillis)
        requireTimestamp("observation.updatedAtMillis", observation.updatedAtMillis)
        requireChronology(
            eventField = "observation.observedAtMillis",
            eventAtMillis = observation.observedAtMillis,
            createdAtMillis = observation.createdAtMillis,
            updatedAtMillis = observation.updatedAtMillis
        )
        requireCanonicalNote(observation.note)
        return observation
    }

    fun nextUniqueId(
        store: AquariumHealthStore,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val maxWaterId = store.waterTestsList.maxOfOrNull { test -> test.id } ?: 0L
        val maxObservationId =
            store.livestockObservationsList.maxOfOrNull { observation -> observation.id } ?: 0L
        val maxExistingId = maxOf(maxWaterId, maxObservationId)
        val next = maxOf(nowMillis, maxExistingId + 1L)
        requirePositive("generated health record id", next)
        return next
    }

    private fun validateReadings(readings: List<AquariumWaterReading>) {
        try {
            AquariumHealthMeasurementPolicy.validateReadings(readings)
        } catch (error: IllegalArgumentException) {
            violation(error.message ?: "Water readings violate the Health contract.")
        }
    }

    private fun validateObservationIdentity(
        categoryKey: String,
        symptomKey: String,
        intensity: ObservationIntensity,
        tankId: Long,
        livestockId: Long?,
        observedAtMillis: Long,
        note: String
    ) {
        try {
            LivestockHealthSymptomCatalog.requireApplicable(
                symptomKey = symptomKey,
                categoryKey = categoryKey
            )
            AquariumHealthMeasurementPolicy.validateObservationInput(
                input = LivestockHealthObservationInput(
                    tankId = tankId,
                    livestockId = livestockId,
                    categoryKey = categoryKey,
                    symptomKey = symptomKey,
                    intensity = intensity,
                    observedAtMillis = observedAtMillis,
                    note = note
                ),
                nowMillis = maxOf(observedAtMillis, MIN_POLICY_REFERENCE_MILLIS)
            )
        } catch (error: IllegalArgumentException) {
            violation(error.message ?: "Observation violates the Health contract.")
        }
    }

    private fun canonicalOwnerUid(value: String): String {
        return try {
            AquariumHealthMeasurementPolicy.requireCanonicalOwnerUid(value)
        } catch (error: IllegalArgumentException) {
            violation(error.message ?: "ownerUid is invalid.")
        }
    }

    private fun requireExpectedOwner(
        actualOwnerUid: String,
        expectedOwnerUid: String?
    ) {
        val expected = expectedOwnerUid?.trim().orEmpty()
        if (expected.isNotEmpty() && actualOwnerUid != expected) {
            violation("Health record owner does not match the active owner.")
        }
    }

    private fun requireCanonicalNote(value: String) {
        try {
            AquariumHealthMeasurementPolicy.requireCanonicalOptionalText(
                field = "note",
                value = value,
                maxChars = AquariumHealthMeasurementPolicy.MAX_NOTE_CHARS
            )
        } catch (error: IllegalArgumentException) {
            violation(error.message ?: "Health note is invalid.")
        }
    }

    private fun requirePositive(field: String, value: Long) {
        if (value <= 0L) {
            violation("$field must be positive.")
        }
    }

    private fun requireTimestamp(field: String, value: Long) {
        try {
            AquariumHealthMeasurementPolicy.requireStoredTimestamp(field, value)
        } catch (error: IllegalArgumentException) {
            violation(error.message ?: "$field is invalid.")
        }
    }

    private fun requireChronology(
        eventField: String,
        eventAtMillis: Long,
        createdAtMillis: Long,
        updatedAtMillis: Long
    ) {
        if (updatedAtMillis < createdAtMillis) {
            violation("updatedAtMillis must not precede createdAtMillis.")
        }
        if (eventAtMillis > updatedAtMillis) {
            violation("$eventField must not be later than updatedAtMillis.")
        }
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }

    private const val MIN_POLICY_REFERENCE_MILLIS = 946_684_800_000L
}

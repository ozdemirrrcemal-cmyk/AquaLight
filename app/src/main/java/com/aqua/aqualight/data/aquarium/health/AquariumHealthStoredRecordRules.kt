package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservationInput
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal object AquariumHealthStoredRecordRules {

    fun validateWaterTest(
        test: StoredAquariumWaterTest,
        expectedOwnerUid: String? = null
    ): StoredAquariumWaterTest {
        val values = AquariumHealthStoreValueRules
        values.requirePositive("waterTest.id", test.id)
        val ownerUid = values.canonicalOwnerUid(test.ownerUid)
        values.requireExpectedOwner(ownerUid, expectedOwnerUid)
        values.requirePositive("waterTest.tankId", test.tankId)
        values.requireTimestamp("waterTest.measuredAtMillis", test.measuredAtMillis)
        values.requireTimestamp("waterTest.createdAtMillis", test.createdAtMillis)
        values.requireTimestamp("waterTest.updatedAtMillis", test.updatedAtMillis)
        values.requireChronology(
            eventField = "waterTest.measuredAtMillis",
            eventAtMillis = test.measuredAtMillis,
            createdAtMillis = test.createdAtMillis,
            updatedAtMillis = test.updatedAtMillis
        )
        values.requireCanonicalNote(test.note)
        values.parseReadings(test.readingsList)
        return test
    }

    fun validateLivestockObservation(
        observation: StoredLivestockHealthObservation,
        expectedOwnerUid: String? = null
    ): StoredLivestockHealthObservation {
        val values = AquariumHealthStoreValueRules
        values.requirePositive("observation.id", observation.id)
        val ownerUid = values.canonicalOwnerUid(observation.ownerUid)
        values.requireExpectedOwner(ownerUid, expectedOwnerUid)
        values.requirePositive("observation.tankId", observation.tankId)
        if (observation.livestockId != 0L) {
            values.requirePositive("observation.livestockId", observation.livestockId)
        }

        val input = LivestockHealthObservationInput(
            tankId = observation.tankId,
            livestockId = observation.livestockId.takeIf { id -> id > 0L },
            categoryKey = observation.categoryKey,
            symptomKey = observation.symptomKey,
            intensity = values.parseIntensity(observation.intensity),
            observedAtMillis = observation.observedAtMillis,
            note = observation.note
        )
        validateApplicationInput {
            AquariumHealthMeasurementPolicy.validateObservationInput(
                input = input,
                nowMillis = observation.observedAtMillis
            )
        }

        values.requireTimestamp("observation.observedAtMillis", observation.observedAtMillis)
        values.requireTimestamp("observation.createdAtMillis", observation.createdAtMillis)
        values.requireTimestamp("observation.updatedAtMillis", observation.updatedAtMillis)
        values.requireChronology(
            eventField = "observation.observedAtMillis",
            eventAtMillis = observation.observedAtMillis,
            createdAtMillis = observation.createdAtMillis,
            updatedAtMillis = observation.updatedAtMillis
        )
        values.requireCanonicalNote(observation.note)
        return observation
    }

    fun validatePlantObservation(
        observation: StoredPlantHealthObservation,
        expectedOwnerUid: String? = null
    ): StoredPlantHealthObservation {
        val values = AquariumHealthStoreValueRules
        values.requirePositive("plantObservation.id", observation.id)
        val ownerUid = values.canonicalOwnerUid(observation.ownerUid)
        values.requireExpectedOwner(ownerUid, expectedOwnerUid)
        values.requirePositive("plantObservation.tankId", observation.tankId)
        if (observation.plantId != 0L) {
            values.requirePositive("plantObservation.plantId", observation.plantId)
        }

        val input = PlantHealthObservationInput(
            tankId = observation.tankId,
            plantId = observation.plantId.takeIf { id -> id > 0L },
            symptomKey = observation.symptomKey,
            algaeTypeKey = observation.algaeTypeKey.takeIf(String::isNotEmpty),
            intensity = values.parseIntensity(observation.intensity),
            observedAtMillis = observation.observedAtMillis,
            note = observation.note
        )
        validateApplicationInput {
            AquariumHealthMeasurementPolicy.validatePlantObservationInput(
                input = input,
                nowMillis = observation.observedAtMillis
            )
        }

        values.requireTimestamp(
            "plantObservation.observedAtMillis",
            observation.observedAtMillis
        )
        values.requireTimestamp(
            "plantObservation.createdAtMillis",
            observation.createdAtMillis
        )
        values.requireTimestamp(
            "plantObservation.updatedAtMillis",
            observation.updatedAtMillis
        )
        values.requireChronology(
            eventField = "plantObservation.observedAtMillis",
            eventAtMillis = observation.observedAtMillis,
            createdAtMillis = observation.createdAtMillis,
            updatedAtMillis = observation.updatedAtMillis
        )
        values.requireCanonicalNote(observation.note)
        return observation
    }

    private fun validateApplicationInput(block: () -> Unit) {
        try {
            block()
        } catch (error: IllegalArgumentException) {
            throw StoreInvariantViolation(
                error.message ?: "Health observation violates the application contract."
            ).also { violation ->
                violation.initCause(error)
            }
        }
    }
}

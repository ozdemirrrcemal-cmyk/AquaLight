package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.AquariumHealthMeasurementPolicy
import com.aqua.aqualight.application.aquarium.health.AquariumWaterReading
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.application.aquarium.health.ObservationIntensity
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal object AquariumHealthStoreValueRules {

    fun canonicalOwnerUid(value: String): String {
        return contractValue {
            AquariumHealthMeasurementPolicy.requireCanonicalOwnerUid(value)
        }
    }

    fun requireExpectedOwner(
        actualOwnerUid: String,
        expectedOwnerUid: String?
    ) {
        val expected = expectedOwnerUid?.trim().orEmpty()
        if (expected.isNotEmpty() && actualOwnerUid != expected) {
            violation("Health record owner does not match the active owner.")
        }
    }

    fun requirePositive(field: String, value: Long) {
        if (value <= 0L) {
            violation("$field must be positive.")
        }
    }

    fun requireTimestamp(field: String, value: Long) {
        contractValue {
            AquariumHealthMeasurementPolicy.requireStoredTimestamp(field, value)
        }
    }

    fun requireChronology(
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

    fun requireCanonicalNote(value: String) {
        contractValue {
            AquariumHealthMeasurementPolicy.requireCanonicalOptionalText(
                field = "note",
                value = value,
                maxChars = AquariumHealthMeasurementPolicy.MAX_NOTE_CHARS
            )
        }
    }

    fun parseIntensity(value: String): ObservationIntensity {
        return runCatching {
            ObservationIntensity.valueOf(value)
        }.getOrElse {
            violation("Health observation contains an unsupported intensity.")
        }
    }

    fun parseReadings(
        storedReadings: List<StoredAquariumWaterReading>
    ): List<AquariumWaterReading> {
        val readings = storedReadings.map { stored ->
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
        contractValue {
            AquariumHealthMeasurementPolicy.validateReadings(readings)
        }
        return readings
    }

    private fun <T> contractValue(block: () -> T): T {
        return try {
            block()
        } catch (error: IllegalArgumentException) {
            violation(error.message ?: "Health record violates the application contract.")
        }
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }
}

package com.aqua.aqualight.data.store

/**
 * Commercial local-store schema baselines.
 *
 * AquaLight has not shipped a previous public store contract. Unsupported or missing versions are
 * corruption, not a legacy compatibility path. Aquarium tanks V2 replaces timezone-dependent
 * calendar-date milliseconds with epoch-day fields.
 */
object CommercialStoreSchema {
    const val AQUARIUM_TANKS_VERSION = 2
    const val CARE_TASKS_VERSION = 1
    const val USER_PREFERENCES_VERSION = 1

    fun requireCurrent(
        storeName: String,
        actualVersion: Int,
        expectedVersion: Int
    ) {
        if (actualVersion != expectedVersion) {
            throw StoreInvariantViolation(
                "$storeName schema version $actualVersion is unsupported; expected $expectedVersion."
            )
        }
    }
}

class StoreInvariantViolation(
    message: String
) : IllegalArgumentException(message)

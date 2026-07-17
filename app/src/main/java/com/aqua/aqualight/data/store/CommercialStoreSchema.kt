package com.aqua.aqualight.data.store

/**
 * First commercial local-store schema baseline.
 *
 * AquaLight has not shipped a previous public store contract, so version 1 is
 * intentionally strict: unsupported or missing versions are corruption, not a
 * legacy compatibility path.
 */
object CommercialStoreSchema {
    const val AQUARIUM_TANKS_VERSION = 1
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

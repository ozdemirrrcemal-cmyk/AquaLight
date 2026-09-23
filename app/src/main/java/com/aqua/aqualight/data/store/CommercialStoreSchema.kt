package com.aqua.aqualight.data.store

/**
 * Commercial local-store schema versions.
 *
 * Schema changes are explicit cutovers. Unsupported or missing versions fail closed; AquaLight
 * does not install compatibility shims or implicit legacy readers.
 */
object CommercialStoreSchema {
    const val AQUARIUM_TANKS_VERSION = 2
    const val CARE_TASKS_VERSION = 1
    const val USER_PREFERENCES_VERSION = 1
    const val LIGHT_LIBRARY_VERSION = 1
    const val AQUARIUM_HEALTH_VERSION = 1

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

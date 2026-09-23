package com.aqua.aqualight.application.user

/** One-shot recovery notices exposed to the presentation layer. */
fun interface LocalDataRecoveryOperations {
    fun consumeRecoveredAreas(): Set<LocalDataRecoveryArea>
}

enum class LocalDataRecoveryArea {
    AQUARIUM_TANKS,
    CARE_TASKS,
    USER_PREFERENCES,
    LIGHT_LIBRARY,
    NOTIFICATION_PREFERENCES,
    KNOWN_DEVICES,
    TANK_DEVICE_ASSIGNMENTS,
    AQUARIUM_HEALTH
}

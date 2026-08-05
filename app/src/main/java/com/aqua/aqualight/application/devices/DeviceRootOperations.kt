package com.aqua.aqualight.application.devices

import kotlinx.coroutines.flow.Flow

/** Read-only application boundary for device root screens and route authorization. */
interface DeviceRootOperations {
    fun observe(deviceUid: String): Flow<DeviceRootSnapshot?>

    fun current(deviceUid: String): DeviceRootSnapshot?

    fun connect(deviceUid: String): Result<Unit>

    /** Second-stage authorization immediately before navigation/command dispatch. */
    fun authorizeRoute(deviceUid: String, route: DeviceRootRoute): Boolean {
        val snapshot = current(deviceUid) ?: return false
        return snapshot.catalogState == DeviceRootCatalogState.VALID &&
            route in snapshot.allowedRoutes
    }
}

data class DeviceRootSnapshot(
    val deviceUid: String,
    val title: String,
    val availability: OwnerDeviceAvailability,
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val catalogState: DeviceRootCatalogState = DeviceRootCatalogState.INVALID,
    val productKey: String = "",
    val productId: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val hardwareRevision: String = "",
    val ipAddress: String = "",
    val firmwareLabel: String = "",
    val modelLabel: String = "",
    val lightChannelCount: Int = 0,
    val timerChannelCount: Int = 0,
    val dosingChannelCount: Int = 0,
    val fanOutputCount: Int = 0,
    val temperatureSensorCount: Int = 0,
    val channelSlots: DeviceChannelSlots = DeviceChannelSlots.EMPTY,
    val capabilities: Set<DeviceRootCapability> = emptySet(),
    val supportedFeatures: List<String> = emptyList(),
    val supportedScreens: List<String> = emptyList(),
    val menuFeatures: Set<DeviceRootMenuFeature> = emptySet(),
    val allowedRoutes: Set<DeviceRootRoute> = emptySet(),
    val productDisplayName: String = "",
    val hasCustomName: Boolean = false
)

enum class DeviceRootCapability {
    MANUAL_LIGHT,
    LIGHT_PROGRAM,
    LIGHT_PRESETS,
    LIGHT_SIMULATION,
    DOSING,
    STANDALONE_TIMER,
    COOLING,
    FAN,
    TEMPERATURE,
    TIME_SYNC,
    OTA
}

enum class DeviceRootMenuFeature {
    LIGHT_MANUAL,
    LIGHT_QUICK_SETUP,
    LIGHT_PROGRAMS,
    LIGHT_PRESETS,
    LIGHT_SIMULATION,
    DOSING_CHANNELS,
    DOSING_CALIBRATION,
    DOSING_SCHEDULES,
    TIMER_CHANNELS,
    TIMER_SCHEDULES,
    COOLING_FANS,
    COOLING_TEMPERATURE,
    DEVICE_SETTINGS
}

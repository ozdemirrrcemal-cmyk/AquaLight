package com.aqua.aqualight.application.devices

import kotlinx.coroutines.flow.Flow

/** Read-only application boundary for device root screens. */
interface DeviceRootOperations {
    fun observe(deviceUid: String): Flow<DeviceRootSnapshot?>

    fun current(deviceUid: String): DeviceRootSnapshot?

    fun connect(deviceUid: String): Result<Unit>
}

data class DeviceRootSnapshot(
    val deviceUid: String,
    val title: String,
    val availability: OwnerDeviceAvailability,
    val ipAddress: String = "",
    val firmwareLabel: String = "",
    val modelLabel: String = "",
    val lightChannelCount: Int = 0,
    val timerChannelCount: Int = 0,
    val dosingChannelCount: Int = 0,
    val fanOutputCount: Int = 0,
    val featureLabels: List<String> = emptyList(),
    val menuFeatures: Set<DeviceRootMenuFeature> = emptySet()
)

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

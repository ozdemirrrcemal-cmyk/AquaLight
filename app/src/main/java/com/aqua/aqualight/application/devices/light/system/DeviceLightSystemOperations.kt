package com.aqua.aqualight.application.devices.light.system

import kotlinx.coroutines.flow.Flow

/** Firmware-independent application boundary for the WRGB Pro Elite System surface. */
interface DeviceLightSystemOperations {
    fun observe(deviceUid: String): Flow<DeviceLightSystemReadResult>

    fun current(deviceUid: String): DeviceLightSystemReadResult

    suspend fun refresh(deviceUid: String): DeviceLightSystemReadResult

    suspend fun save(
        deviceUid: String,
        settings: DeviceLightSystemSettings
    ): DeviceLightSystemMutationResult
}

sealed interface DeviceLightSystemReadResult {
    data class Available(val snapshot: DeviceLightSystemSnapshot) : DeviceLightSystemReadResult
    data class Failed(val failure: DeviceLightSystemFailure) : DeviceLightSystemReadResult
}

sealed interface DeviceLightSystemMutationResult {
    data class Success(val snapshot: DeviceLightSystemSnapshot) : DeviceLightSystemMutationResult

    data class Failed(
        val failure: DeviceLightSystemFailure,
        val partialApplyPossible: Boolean = false
    ) : DeviceLightSystemMutationResult
}

enum class DeviceLightSystemFailure {
    UNAVAILABLE,
    NOT_CONNECTED,
    UNSUPPORTED,
    REJECTED,
    INVALID_DATA
}

enum class DeviceLightFanMode {
    AUTOMATIC,
    ON,
    OFF
}

enum class DeviceLightSystemCondition {
    NORMAL,
    PROTECTION_ACTIVE,
    SENSOR_FAIL_SAFE,
    FAN_FAULT
}

data class DeviceLightSystemFanSnapshot(
    val key: String,
    val percent: Int,
    val healthy: Boolean
)

data class DeviceLightSystemTemperaturePolicy(
    val minimum: Int,
    val maximum: Int,
    val step: Int = 1
) {
    init {
        require(minimum < maximum)
        require(step > 0)
    }
}

data class DeviceLightSystemSnapshot(
    val deviceUid: String,
    val temperatureCelsius: Double?,
    val condition: DeviceLightSystemCondition,
    val sensorHealthy: Boolean,
    val fans: List<DeviceLightSystemFanSnapshot>,
    val mode: DeviceLightFanMode,
    val startTemperatureCelsius: Int,
    val fullSpeedTemperatureCelsius: Int,
    val startTemperaturePolicy: DeviceLightSystemTemperaturePolicy,
    val fullSpeedTemperaturePolicy: DeviceLightSystemTemperaturePolicy,
    val protectionThresholdCelsius: Int,
    val protectionThresholdPolicy: DeviceLightSystemTemperaturePolicy,
    val protectionActive: Boolean
)

data class DeviceLightSystemSettings(
    val mode: DeviceLightFanMode,
    val startTemperatureCelsius: Int,
    val fullSpeedTemperatureCelsius: Int,
    val protectionThresholdCelsius: Int
)

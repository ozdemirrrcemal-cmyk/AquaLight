package com.aqua.aqualight.application.devices.cooling

import kotlinx.coroutines.flow.Flow

/**
 * Owner-scoped application boundary for the Cooling automatic temperature editor.
 *
 * Presentation receives firmware-backed values and edit policy only. WebSocket transport,
 * firmware payloads and persistence details remain behind the data implementation.
 */
interface DeviceCoolingAutomaticSettingsOperations {
    fun observeAutomaticSettings(deviceUid: String): Flow<DeviceCoolingAutomaticSettingsSnapshot>

    fun currentAutomaticSettings(deviceUid: String): DeviceCoolingAutomaticSettingsSnapshot

    suspend fun refreshAutomaticSettings(deviceUid: String): Result<Unit>

    suspend fun saveAutomaticTemperatureRange(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double
    ): Result<Unit>
}

data class DeviceCoolingAutomaticSettingsSnapshot(
    val available: Boolean = false,
    val loaded: Boolean = false,
    val editable: Boolean = false,
    val startTemperatureC: Double? = null,
    val maximumSpeedTemperatureC: Double? = null,
    val tankTemperatureC: Double? = null,
    val fanPercentNow: Double? = null,
    val policy: DeviceCoolingAutomaticTemperaturePolicy? = null
)

data class DeviceCoolingAutomaticTemperaturePolicy(
    val startMinimumC: Double,
    val startMaximumC: Double,
    val maximumSpeedMinimumC: Double,
    val maximumSpeedMaximumC: Double,
    val stepC: Double,
    val minimumGapC: Double
) {
    init {
        require(startMinimumC <= startMaximumC)
        require(maximumSpeedMinimumC <= maximumSpeedMaximumC)
        require(stepC > 0.0)
        require(minimumGapC >= stepC)
    }
}

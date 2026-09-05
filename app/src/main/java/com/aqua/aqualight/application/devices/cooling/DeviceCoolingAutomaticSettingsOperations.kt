package com.aqua.aqualight.application.devices.cooling

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import kotlinx.coroutines.flow.Flow

/**
 * Owner-scoped application boundary for the Cooling automatic temperature editor.
 *
 * Presentation receives firmware-backed values, edit policy and typed operation semantics only.
 * WebSocket transport, firmware payloads, exceptions and persistence details remain behind data.
 */
interface DeviceCoolingAutomaticSettingsOperations {
    fun observeAutomaticSettings(deviceUid: String): Flow<DeviceCoolingAutomaticSettingsSnapshot>

    fun currentAutomaticSettings(deviceUid: String): DeviceCoolingAutomaticSettingsSnapshot

    suspend fun refreshAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticCommandResult

    suspend fun saveAutomaticTemperatureRange(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double
    ): DeviceCoolingAutomaticCommandResult

    /** A null [silentModeEnabled] means firmware policy does not expose writable Silent Mode. */
    suspend fun saveAutomaticSettings(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double,
        silentModeEnabled: Boolean?
    ): DeviceCoolingAutomaticCommandResult {
        if (silentModeEnabled != null) {
            return DeviceCoolingAutomaticCommandResult.Failed(
                DeviceCoolingAutomaticFailure.Unsupported
            )
        }
        return saveAutomaticTemperatureRange(
            deviceUid = deviceUid,
            startTemperatureC = startTemperatureC,
            maximumSpeedTemperatureC = maximumSpeedTemperatureC
        )
    }
}

const val DEVICE_COOLING_FAN_PERCENT_MINIMUM = 0
const val DEVICE_COOLING_FAN_PERCENT_MAXIMUM = 100

data class DeviceCoolingAutomaticSettingsSnapshot(
    val available: Boolean = false,
    val loaded: Boolean = false,
    val editable: Boolean = false,
    val startTemperatureC: Double? = null,
    val maximumSpeedTemperatureC: Double? = null,
    val tankTemperatureC: Double? = null,
    val fanPercentNow: Double? = null,
    val operatingState: DeviceCoolingOperatingState? = null,
    /** Null when firmware policy does not support Silent Mode. */
    val silentModeEnabled: Boolean? = null,
    val silentModeMaximumFanPercent: Int? = null,
    val policy: DeviceCoolingAutomaticTemperaturePolicy? = null
) {
    init {
        require(
            silentModeMaximumFanPercent == null ||
                silentModeMaximumFanPercent in
                (DEVICE_COOLING_FAN_PERCENT_MINIMUM + 1)..DEVICE_COOLING_FAN_PERCENT_MAXIMUM
        )
    }
}

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

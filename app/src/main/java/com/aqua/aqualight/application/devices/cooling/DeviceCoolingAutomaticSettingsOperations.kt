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

    /**
     * Future-ready Automatic Cooling save contract.
     *
     * A null [silentModeEnabled] means the connected firmware does not expose Silent Mode yet.
     * Existing implementations keep using the proven temperature-range persistence path. Once
     * firmware support lands, the data adapter can override this method and persist the complete
     * Automatic settings payload atomically without changing presentation code.
     */
    suspend fun saveAutomaticSettings(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double,
        silentModeEnabled: Boolean?
    ): Result<Unit> {
        if (silentModeEnabled != null) {
            return Result.failure(
                UnsupportedOperationException(
                    "Silent Mode is not supported by the current Cooling firmware contract."
                )
            )
        }
        return saveAutomaticTemperatureRange(
            deviceUid = deviceUid,
            startTemperatureC = startTemperatureC,
            maximumSpeedTemperatureC = maximumSpeedTemperatureC
        )
    }
}

const val DEVICE_COOLING_AUTOMATIC_SILENT_MODE_MAXIMUM_FAN_PERCENT = 50
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
    /** Null until the firmware exposes an authoritative Silent Mode value. */
    val silentModeEnabled: Boolean? = null,
    val silentModeMaximumFanPercent: Int =
        DEVICE_COOLING_AUTOMATIC_SILENT_MODE_MAXIMUM_FAN_PERCENT,
    val policy: DeviceCoolingAutomaticTemperaturePolicy? = null
) {
    init {
        require(
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

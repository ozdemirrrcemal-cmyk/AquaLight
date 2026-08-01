package com.aqua.aqualight.application.devices

import kotlinx.coroutines.flow.Flow

interface DeviceCoolingOperations {
    fun observeCooling(deviceUid: String): Flow<DeviceCoolingSnapshot?>

    fun currentCooling(deviceUid: String): DeviceCoolingSnapshot?

    suspend fun refresh(deviceUid: String): DeviceCoolingOperationResult

    suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingModeOption,
        save: Boolean = true
    ): DeviceCoolingOperationResult

    suspend fun setTemperatureRange(
        deviceUid: String,
        minTemperatureC: Double,
        maxTemperatureC: Double,
        save: Boolean = true
    ): DeviceCoolingOperationResult

    suspend fun setFanDisplayName(
        deviceUid: String,
        fanKey: String,
        displayName: String?,
        save: Boolean = true
    ): DeviceCoolingOperationResult
}

data class DeviceCoolingSnapshot(
    val supported: Boolean,
    val mode: DeviceCoolingModeOption,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val temperatureSupported: Boolean,
    val readingValid: Boolean,
    val temperatureC: Double?,
    val sampledAtMs: Long,
    val fanCount: Int,
    val fanDisplayNamesEditable: Boolean,
    val fans: List<DeviceCoolingFanSnapshot>
)

data class DeviceCoolingFanSnapshot(
    val key: String,
    val displayName: String,
    val percentNow: Double,
    val displayNameEditable: Boolean
)

enum class DeviceCoolingModeOption {
    AUTO,
    ON,
    OFF
}

sealed interface DeviceCoolingOperationResult {
    data object Success : DeviceCoolingOperationResult
    data object Unsupported : DeviceCoolingOperationResult
    data object NotConnected : DeviceCoolingOperationResult
    data class Failed(val reason: String) : DeviceCoolingOperationResult
}

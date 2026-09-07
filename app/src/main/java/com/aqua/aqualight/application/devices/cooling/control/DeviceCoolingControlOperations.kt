package com.aqua.aqualight.application.devices.cooling.control

import kotlinx.coroutines.flow.Flow

/** Stable application boundary for Cooling mode, manual output and live control telemetry. */
interface DeviceCoolingControlOperations {
    fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult>

    fun currentControl(deviceUid: String): DeviceCoolingControlResult

    suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult

    suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult

    suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult
}

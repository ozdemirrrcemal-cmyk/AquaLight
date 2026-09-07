package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.program.DeviceCoolingProgramOperations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Explicit cutover boundaries while the legacy Cooling transport is removed.
 *
 * These objects never read or write WebSocket state. They keep presentation on the
 * stable application interfaces until strict aql.cooling.v1 wiring is connected.
 */
internal object DisconnectedDeviceCoolingAutomaticSettingsOperations :
    DeviceCoolingAutomaticSettingsOperations {

    private val snapshot = DeviceCoolingAutomaticSettingsSnapshot()

    override fun observeAutomaticSettings(
        deviceUid: String
    ): Flow<DeviceCoolingAutomaticSettingsSnapshot> = flowOf(snapshot)

    override fun currentAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticSettingsSnapshot = snapshot

    override suspend fun refreshAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticCommandResult = unavailable()

    override suspend fun saveAutomaticTemperatureRange(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double
    ): DeviceCoolingAutomaticCommandResult = unavailable()

    override suspend fun saveAutomaticSettings(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double,
        silentModeEnabled: Boolean?
    ): DeviceCoolingAutomaticCommandResult = unavailable()

    private fun unavailable(): DeviceCoolingAutomaticCommandResult =
        DeviceCoolingAutomaticCommandResult.Failed(DeviceCoolingAutomaticFailure.Unavailable)
}

internal object DisconnectedDeviceCoolingTemperatureHistoryOperations :
    DeviceCoolingTemperatureHistoryOperations {

    override suspend fun loadTemperatureHistory(
        deviceUid: String,
        range: DeviceCoolingTemperatureHistoryRange
    ): DeviceCoolingTemperatureHistoryLoadResult =
        DeviceCoolingTemperatureHistoryLoadResult.Unavailable
}

internal object DisconnectedDeviceCoolingControlOperations : DeviceCoolingControlOperations {
    private val unavailable =
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)

    override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> =
        flowOf(unavailable)

    override fun currentControl(deviceUid: String): DeviceCoolingControlResult = unavailable

    override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult = unavailable

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult = unavailable

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult = unavailable
}

internal object DisconnectedDeviceCoolingProgramOperations : DeviceCoolingProgramOperations {
    override suspend fun readProgram(deviceUid: String): CoolingProgramReadResult =
        CoolingProgramReadResult.Unavailable

    override suspend fun saveProgram(
        deviceUid: String,
        slots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult = CoolingProgramSaveResult.Unavailable
}

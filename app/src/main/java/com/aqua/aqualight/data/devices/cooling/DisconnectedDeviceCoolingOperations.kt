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
 * Explicit cutover boundary while the legacy Cooling transport is removed.
 *
 * This object never reads or writes WebSocket state. It keeps presentation on the
 * stable application interfaces until the strict aql.cooling.v1 data connection
 * is wired in a separate change.
 */
internal object DisconnectedDeviceCoolingOperations :
    DeviceCoolingAutomaticSettingsOperations,
    DeviceCoolingTemperatureHistoryOperations,
    DeviceCoolingControlOperations,
    DeviceCoolingProgramOperations {

    private val automaticSnapshot = DeviceCoolingAutomaticSettingsSnapshot()
    private val controlUnavailable =
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)

    override fun observeAutomaticSettings(
        deviceUid: String
    ): Flow<DeviceCoolingAutomaticSettingsSnapshot> = flowOf(automaticSnapshot)

    override fun currentAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticSettingsSnapshot = automaticSnapshot

    override suspend fun refreshAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticCommandResult = automaticUnavailable()

    override suspend fun saveAutomaticTemperatureRange(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double
    ): DeviceCoolingAutomaticCommandResult = automaticUnavailable()

    override suspend fun saveAutomaticSettings(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double,
        silentModeEnabled: Boolean?
    ): DeviceCoolingAutomaticCommandResult = automaticUnavailable()

    override suspend fun loadTemperatureHistory(
        deviceUid: String,
        range: DeviceCoolingTemperatureHistoryRange
    ): DeviceCoolingTemperatureHistoryLoadResult =
        DeviceCoolingTemperatureHistoryLoadResult.Unavailable

    override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> =
        flowOf(controlUnavailable)

    override fun currentControl(deviceUid: String): DeviceCoolingControlResult =
        controlUnavailable

    override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult =
        controlUnavailable

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult = controlUnavailable

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult = controlUnavailable

    override suspend fun readProgram(deviceUid: String): CoolingProgramReadResult =
        CoolingProgramReadResult.Unavailable

    override suspend fun saveProgram(
        deviceUid: String,
        slots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult = CoolingProgramSaveResult.Unavailable

    private fun automaticUnavailable(): DeviceCoolingAutomaticCommandResult =
        DeviceCoolingAutomaticCommandResult.Failed(DeviceCoolingAutomaticFailure.Unavailable)
}

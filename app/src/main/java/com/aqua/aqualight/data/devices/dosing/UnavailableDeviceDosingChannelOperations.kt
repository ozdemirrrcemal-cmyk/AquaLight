package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Fail-closed production channel boundary while Dosing v1 remains intentionally unbound. */
@Suppress("TooManyFunctions") // The boundary intentionally implements every fail-closed operation.
internal object UnavailableDeviceDosingChannelOperations : DeviceDosingChannelOperations {
    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingChannelSnapshot?> = flowOf(null)

    override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
        flowOf(emptyList())

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = unavailable()

    override suspend fun refreshAll(deviceUid: String): Boolean = false

    override suspend fun applyProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram
    ): DeviceDosingChannelOperationResult = unavailable()

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult = unavailable()

    override suspend fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult = unavailable()

    override suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = unavailable()

    override suspend fun doseNow(
        deviceUid: String,
        slotId: String,
        amountMicroliters: Long
    ): DeviceDosingChannelOperationResult = unavailable()

    override suspend fun doseStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = unavailable()

    override suspend fun reset(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = unavailable()

    private fun unavailable(): DeviceDosingChannelOperationResult =
        DeviceDosingChannelOperationResult.Unavailable
}

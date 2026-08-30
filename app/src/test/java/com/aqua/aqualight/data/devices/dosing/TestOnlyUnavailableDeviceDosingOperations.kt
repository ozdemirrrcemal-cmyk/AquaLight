package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Unit-test-only fail-closed doubles for tests that do not exercise Dosing mutations/navigation.
 *
 * These objects live exclusively in src/test, so they cannot participate in debug, releaseSmoke,
 * release APK composition, owner graphs, runtime state, or notification delivery. Production keeps
 * the single DeviceDosingV1 production path enforced by the Dosing architecture guard.
 */
internal object UnavailableDeviceDosingChannelOperations : DeviceDosingChannelOperations {
    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingChannelSnapshot?> = flowOf(null)

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Unavailable

    override suspend fun applyProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram
    ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Unavailable

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Unavailable

    override suspend fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Unavailable

    override suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Unavailable

    override suspend fun doseNow(
        deviceUid: String,
        slotId: String,
        amountMicroliters: Long
    ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Unavailable

    override suspend fun doseStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Unavailable

    override suspend fun reset(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Unavailable
}

internal object UnavailableDeviceDosingChannelNavigationOperations :
    DeviceDosingChannelNavigationOperations {
    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = null
}

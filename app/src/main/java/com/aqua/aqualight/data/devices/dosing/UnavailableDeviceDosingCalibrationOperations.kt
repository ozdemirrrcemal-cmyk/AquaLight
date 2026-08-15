package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Fail-closed production boundary while the replacement Dosing data layer is unbound. */
@Suppress("TooManyFunctions") // The boundary intentionally implements every fail-closed operation.
internal object UnavailableDeviceDosingCalibrationOperations :
    DeviceDosingCalibrationOperations {

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> = flowOf(null)

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun primeStart(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun primeStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun primeSafetyStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun awaitPrimeSafetyStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun start(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun finish(
        deviceUid: String,
        slotId: String,
        measuredMl: Double
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun startVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun stopVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun confirm(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun cancel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = unavailable()

    override suspend fun exitSafely(
        deviceUid: String,
        slotId: String,
        primeMayBeActive: Boolean,
        lastKnownSnapshot: DeviceDosingCalibrationSnapshot?
    ) = Unit

    private fun unavailable(): DeviceDosingCalibrationResult =
        DeviceDosingCalibrationResult.Rejected(DeviceDosingCalibrationFailure.INTERNAL)
}

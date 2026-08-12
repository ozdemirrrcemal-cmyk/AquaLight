package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeDosingCalibrationOperations(
    initial: DeviceDosingCalibrationSnapshot
) : DeviceDosingCalibrationOperations {
    private val state = MutableStateFlow<DeviceDosingCalibrationSnapshot?>(initial)

    var savedName = ""
    var primeStarts = 0
    var primeStops = 0
    var verificationStops = 0
    var confirms = 0
    var cancels = 0
    var confirmResult: DeviceDosingCalibrationResult = calibrationSuccess(initial)

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> = state

    override suspend fun refresh(deviceUid: String, slotId: String) = calibrationSuccess(current())

    override suspend fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult {
        savedName = displayName.trim()
        return calibrationSuccess(current().copy(channelTitle = savedName))
    }

    override suspend fun primeStart(deviceUid: String, slotId: String) =
        calibrationSuccess(current().copy(manualActive = true)).also { primeStarts += 1 }

    override suspend fun primeStop(deviceUid: String, slotId: String) =
        calibrationSuccess(current().copy(manualActive = false)).also { primeStops += 1 }

    override suspend fun start(deviceUid: String, slotId: String) = calibrationSuccess(current())

    override suspend fun finish(deviceUid: String, slotId: String, measuredMl: Double) =
        calibrationSuccess(current())

    override suspend fun startVerificationDose(deviceUid: String, slotId: String) =
        calibrationSuccess(current())

    override suspend fun stopVerificationDose(deviceUid: String, slotId: String) =
        calibrationSuccess(current().copy(manualActive = false)).also { verificationStops += 1 }

    override suspend fun confirm(deviceUid: String, slotId: String) =
        confirmResult.also { confirms += 1 }

    override suspend fun cancel(deviceUid: String, slotId: String) = calibrationSuccess(
        current().copy(
            sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
            manualActive = false
        )
    ).also { cancels += 1 }

    private fun current(): DeviceDosingCalibrationSnapshot = requireNotNull(state.value)
}

internal fun calibrationRoute(recalibration: Boolean = false) = DeviceDosingCalibrationRoute(
    deviceUid = "device-1",
    slotId = "channel-1",
    pumpCount = 2,
    channelNumber = 1,
    channelTitle = "Channel 1",
    recalibration = recalibration
)

internal fun calibrationSuccess(snapshot: DeviceDosingCalibrationSnapshot) =
    DeviceDosingCalibrationResult.Success(snapshot)

internal fun calibrationSnapshot() = baseCalibrationSnapshot()

internal fun calibratedCalibrationSnapshot() = baseCalibrationSnapshot().copy(
    calibrated = true,
    lastCalibratedAt = 100L
)

internal fun completedVerificationSnapshot() = baseCalibrationSnapshot().copy(
    sessionPhase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
    durationMs = 5_000L,
    measuredMl = 4.0,
    pendingDoseMsPerMl = 1_250L,
    verificationDoseStarted = true,
    verificationDoseComplete = true
)

internal fun activeVerificationSnapshot() = baseCalibrationSnapshot().copy(
    sessionPhase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
    durationMs = 5_000L,
    measuredMl = 4.0,
    verificationDoseStarted = true,
    manualActive = true
)

private fun baseCalibrationSnapshot() = DeviceDosingCalibrationSnapshot(
    deviceUid = "device-1",
    slotId = "channel-1",
    pumpCount = 2,
    channelNumber = 1,
    channelTitle = "Channel 1",
    deviceUptimeMs = 12_000L,
    calibrated = false,
    lastCalibratedAt = 0L,
    sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
    startedAtUptimeMs = 0L,
    durationMs = 0L,
    measuredMl = 0.0,
    pendingDoseMsPerMl = -1L,
    verificationDoseStarted = false,
    verificationDoseComplete = false,
    verificationDoseRemainingMs = 0L,
    manualActive = false
)

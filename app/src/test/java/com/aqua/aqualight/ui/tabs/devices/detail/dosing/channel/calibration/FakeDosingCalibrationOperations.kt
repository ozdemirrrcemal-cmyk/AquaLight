package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeDosingCalibrationOperations(
    initial: DeviceDosingCalibrationSnapshot
) : DeviceDosingCalibrationOperations {
    private val state = MutableStateFlow<DeviceDosingCalibrationSnapshot?>(initial)
    private val current: DeviceDosingCalibrationSnapshot
        get() = requireNotNull(state.value)

    var confirmedName = ""
    var refreshes = 0
    var primeStarts = 0
    var primeStops = 0
    var verificationStops = 0
    var confirms = 0
    var cancels = 0
    var primeStartResult: DeviceDosingCalibrationResult? = null
    var confirmResult: DeviceDosingCalibrationResult = calibrationSuccess(initial)
    var primeStartBlocker: CompletableDeferred<Unit>? = null

    fun publish(snapshot: DeviceDosingCalibrationSnapshot?) {
        state.value = snapshot
    }

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> = state

    override suspend fun refresh(deviceUid: String, slotId: String) =
        calibrationSuccess(current).also { refreshes += 1 }

    override suspend fun primeStart(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult {
        primeStarts += 1
        val baseline = current
        primeStartBlocker?.await()
        val result = primeStartResult ?: calibrationSuccess(baseline.copy(manualActive = true))
        if (result is DeviceDosingCalibrationResult.Success) state.value = result.snapshot
        return result
    }

    override suspend fun primeStop(deviceUid: String, slotId: String): DeviceDosingCalibrationResult {
        primeStops += 1
        val snapshot = current.copy(manualActive = false)
        state.value = snapshot
        return calibrationSuccess(snapshot)
    }

    override suspend fun start(deviceUid: String, slotId: String) = calibrationSuccess(current)

    override suspend fun finish(deviceUid: String, slotId: String, measuredMl: Double) =
        calibrationSuccess(current)

    override suspend fun startVerificationDose(deviceUid: String, slotId: String) =
        calibrationSuccess(current)

    override suspend fun stopVerificationDose(deviceUid: String, slotId: String) =
        calibrationSuccess(current.copy(manualActive = false)).also { verificationStops += 1 }

    override suspend fun confirm(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult = confirmResult.also { result ->
        confirms += 1
        confirmedName = displayName
        if (result is DeviceDosingCalibrationResult.Success) state.value = result.snapshot
    }

    override suspend fun cancel(deviceUid: String, slotId: String) = calibrationSuccess(
        current.copy(
            sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
            manualActive = false
        )
    ).also { cancels += 1 }
}

internal fun calibrationRoute(recalibration: Boolean = false) = DeviceDosingCalibrationRoute(
    deviceUid = "device-1",
    slotId = "channel-1",
    pumpCount = 2,
    channelNumber = 1,
    recalibration = recalibration
)

internal fun calibrationSuccess(snapshot: DeviceDosingCalibrationSnapshot) =
    DeviceDosingCalibrationResult.Success(snapshot)

internal fun calibratedCalibrationSnapshot() = baseCalibrationSnapshot().copy(
    calibrated = true,
    lastCalibratedAt = 100L
)

internal fun calibrationSnapshot() = baseCalibrationSnapshot()

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

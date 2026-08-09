package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationCandidate
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationChannelSnapshot
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationRun
import com.aqua.aqualight.application.devices.DeviceDosingVerificationRun
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingCalibrationFinishPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingCalibrationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingDoseNowPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus

/** Keeps the commercial Dosing firmware contract behind the application boundary. */
internal class DefaultDeviceDosingCalibrationOperations(
    private val devicesRepository: DevicesRepository
) : DeviceDosingCalibrationOperations {

    override suspend fun loadChannel(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = runCatching {
        val context = requireContext(deviceUid, channelKey)
        devicesRepository.connectRuntime(context.deviceUid).getOrThrow()
        requestChannelSnapshot(context)
    }

    override suspend fun updateDisplayName(
        deviceUid: String,
        channelKey: String,
        displayName: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = runCatching {
        val normalizedDisplayName = displayName.trim()
        require(normalizedDisplayName.isNotEmpty()) { "Dosing liquid name must not be blank." }
        val context = requireContext(deviceUid, channelKey)
        context.modules.dosing.setChannelDisplayName(
            deviceUid = context.deviceUid,
            channelKey = context.channelKey,
            displayName = normalizedDisplayName,
            save = true
        ).requireSuccessValue()
        requestChannelSnapshot(context)
    }

    override suspend fun startPrime(deviceUid: String, channelKey: String): Result<Unit> =
        runCatching {
            val context = requireContext(deviceUid, channelKey)
            val result = context.modules.dosing.primeStart(
                context.deviceUid,
                context.channelKey
            ).requireSuccessValue()
            check(result.manualActive) { "Firmware did not start Dosing prime." }
        }

    override suspend fun stopPrime(deviceUid: String, channelKey: String): Result<Unit> =
        runCatching {
            val context = requireContext(deviceUid, channelKey)
            val result = context.modules.dosing.primeStop(
                context.deviceUid,
                context.channelKey
            ).requireSuccessValue()
            check(!result.manualActive) { "Firmware did not stop Dosing prime." }
        }

    override suspend fun startCalibrationDose(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationRun> = runCatching {
        val context = requireContext(deviceUid, channelKey)
        val result = context.modules.dosing.calibrationStart(
            deviceUid = context.deviceUid,
            payload = DeviceDosingCalibrationStartPayload(
                channelKey = context.channelKey,
                durationMs = DeviceDosingRuntimeContract.Limit.DEFAULT_CALIBRATION_DURATION_MS
            )
        ).requireSuccessValue()
        check(result.manualActive) { "Firmware did not start the calibration dose." }
        DeviceDosingCalibrationRun(durationMs = result.durationMs)
    }

    override suspend fun finishCalibrationDose(
        deviceUid: String,
        channelKey: String,
        measuredMl: Double
    ): Result<DeviceDosingCalibrationCandidate> = runCatching {
        val context = requireContext(deviceUid, channelKey)
        val result = context.modules.dosing.calibrationFinish(
            deviceUid = context.deviceUid,
            payload = DeviceDosingCalibrationFinishPayload(
                channelKey = context.channelKey,
                measuredMl = measuredMl
            )
        ).requireSuccessValue()
        check(result.pending) { "Firmware did not stage a pending Dosing calibration." }
        DeviceDosingCalibrationCandidate(
            measuredMl = result.measuredMl,
            durationMs = result.durationMs,
            pendingDoseMsPerMl = result.pendingDoseMsPerMl
        )
    }

    override suspend fun startVerificationDose(
        deviceUid: String,
        channelKey: String,
        amountMl: Double
    ): Result<DeviceDosingVerificationRun> = runCatching {
        val context = requireContext(deviceUid, channelKey)
        val result = context.modules.dosing.doseNow(
            deviceUid = context.deviceUid,
            payload = DeviceDosingDoseNowPayload(
                channelKey = context.channelKey,
                amountMl = amountMl,
                usePendingCalibration = true
            )
        ).requireSuccessValue()
        check(result.manualActive) { "Firmware did not start the verification dose." }
        check(result.usePendingCalibration) {
            "Firmware verification dose did not use the pending calibration."
        }
        DeviceDosingVerificationRun(
            amountMl = result.amountMl,
            durationMs = result.durationMs
        )
    }

    override suspend fun stopVerificationDose(
        deviceUid: String,
        channelKey: String
    ): Result<Unit> = runCatching {
        val context = requireContext(deviceUid, channelKey)
        val result = context.modules.dosing.doseStop(
            context.deviceUid,
            context.channelKey
        ).requireSuccessValue()
        check(!result.manualActive) { "Firmware did not stop the verification dose." }
    }

    override suspend fun confirmCalibration(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = runCatching {
        val context = requireContext(deviceUid, channelKey)
        val result = context.modules.dosing.calibrationConfirm(
            context.deviceUid,
            context.channelKey
        ).requireSuccessValue()
        check(result.saved) { "Firmware did not persist the Dosing calibration." }
        requestChannelSnapshot(context)
    }

    override suspend fun cancelCalibration(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = runCatching {
        val context = requireContext(deviceUid, channelKey)
        context.modules.dosing.calibrationCancel(
            context.deviceUid,
            context.channelKey
        ).requireSuccessValue()
        requestChannelSnapshot(context)
    }

    private suspend fun requestChannelSnapshot(
        context: CalibrationRuntimeContext
    ): DeviceDosingCalibrationChannelSnapshot {
        val status = context.modules.dosing.requestStatus(context.deviceUid).requireSuccessValue()
        return status.toCalibrationChannelSnapshot(context.channelKey)
    }

    private fun requireContext(
        deviceUid: String,
        channelKey: String
    ): CalibrationRuntimeContext {
        val normalizedUid = deviceUid.trim()
        val normalizedChannelKey = channelKey.trim().lowercase()
        require(normalizedUid.isNotEmpty()) { "Device uid is missing." }
        require(normalizedChannelKey.isNotEmpty()) { "Dosing channel key is missing." }
        val uid = DeviceUid(normalizedUid)
        checkNotNull(devicesRepository.currentDevice(uid)) { "Device is not registered." }
        return CalibrationRuntimeContext(
            deviceUid = uid,
            channelKey = normalizedChannelKey,
            modules = checkNotNull(devicesRepository.runtimeModules()) {
                "Device runtime is not configured."
            }
        )
    }
}

private data class CalibrationRuntimeContext(
    val deviceUid: DeviceUid,
    val channelKey: String,
    val modules: DeviceRuntimeModuleProvider
)

private fun DeviceDosingStatus.toCalibrationChannelSnapshot(
    channelKey: String
): DeviceDosingCalibrationChannelSnapshot {
    val listIndex = channels.indexOfFirst { channel -> channel.key == channelKey }
    check(listIndex >= 0) { "Firmware omitted the selected Dosing channel." }
    val channel = channels[listIndex]
    check(runtime.supportsCalibrationWorkflow && channel.editable.dosingCalibration) {
        "Dosing calibration is not available for the selected channel."
    }
    return DeviceDosingCalibrationChannelSnapshot(
        pumpCount = channelCount,
        channelNumber = listIndex + 1,
        channelKey = channel.key,
        displayName = channel.displayName,
        calibrated = channel.dosing.calibrated,
        calibrationEditable = channel.editable.dosingCalibration,
        supportsPrime = runtime.supportsPrime,
        supportsManualDose = runtime.supportsManualDose,
        minimumMeasuredMl = DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML,
        maximumMeasuredMl = DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML,
        maximumVerificationDoseMl = DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML
    )
}

private fun <T> DeviceRuntimeCommandOutcome<T>.requireSuccessValue(): T = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> value
    is DeviceRuntimeCommandOutcome.FirmwareError -> error(
        "Firmware rejected ${module}.${action}: $code ${message}".trim()
    )
    is DeviceRuntimeCommandOutcome.ProtocolError -> error(
        "Protocol rejected ${module}.${action}: $reason"
    )
    else -> error("Device runtime request failed: ${javaClass.simpleName}")
}

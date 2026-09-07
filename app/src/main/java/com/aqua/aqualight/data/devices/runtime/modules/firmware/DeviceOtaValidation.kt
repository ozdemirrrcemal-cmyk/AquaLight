package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

internal object DeviceOtaValidator {

    fun snapshotAgainstPlan(
        snapshot: DeviceFirmwareOtaSnapshot,
        plan: DeviceFirmwareUpdatePlan?
    ): String? = if (plan == null || snapshot.phase == DeviceFirmwareOtaPhase.IDLE) {
        null
    } else {
        validateTransferIdentity(snapshot, plan) ?: validateCompletedTransfer(snapshot, plan)
    }

    fun planAgainstSnapshot(
        plan: DeviceFirmwareUpdatePlan,
        snapshot: DeviceSnapshot
    ): String? = when {
        !snapshot.hasValidatedRuntimeMetadata -> "Current runtime metadata is not validated."
        snapshot.runtimeMetadataGeneration != plan.runtimeMetadataGeneration ->
            "OTA plan expired because runtime metadata generation changed."
        snapshot.product.productKey != plan.productKey -> "OTA plan productKey changed."
        snapshot.product.productId != plan.productId -> "OTA plan productId changed."
        snapshot.product.model != plan.model -> "OTA plan model changed."
        snapshot.product.hardwareRevision != plan.hardwareRevision ->
            "OTA plan hardwareRevision changed."
        snapshot.firmwareVersion != plan.currentVersion ->
            "OTA plan expired because the current firmware version changed."
        else -> null
    }

    private fun validateTransferIdentity(
        snapshot: DeviceFirmwareOtaSnapshot,
        plan: DeviceFirmwareUpdatePlan
    ): String? = when {
        snapshot.targetVersion != plan.targetVersion ->
            "Firmware OTA targetVersion differs from the selected artifact."
        !snapshot.sha256Expected.equals(plan.firmware.sha256, ignoreCase = true) ->
            "Firmware OTA expected SHA256 differs from the selected artifact."
        snapshot.contentLength != plan.firmware.size.toLong() ->
            "Firmware OTA content length differs from the selected artifact."
        snapshot.allowInsecureHttp || snapshot.urlScheme != "https" ->
            "Firmware OTA transport differs from the secure selected artifact."
        else -> null
    }

    private fun validateCompletedTransfer(
        snapshot: DeviceFirmwareOtaSnapshot,
        plan: DeviceFirmwareUpdatePlan
    ): String? = if (snapshot.phase != DeviceFirmwareOtaPhase.SUCCEEDED) {
        null
    } else {
        when {
            !snapshot.sha256Actual.equals(plan.firmware.sha256, ignoreCase = true) ->
                "Firmware OTA actual SHA256 differs from the selected artifact."
            snapshot.bytesWritten != plan.firmware.size.toLong() ->
                "Firmware OTA written byte count differs from the selected artifact."
            snapshot.progressPermille != COMPLETE_PROGRESS_PERMILLE ->
                "Firmware OTA completed without full progress."
            !snapshot.restartRequired ->
                "Firmware OTA completed without requiring the new image to boot."
            else -> null
        }
    }

    private const val COMPLETE_PROGRESS_PERMILLE = 1_000
}

internal object DeviceOtaStateMapper {

    fun map(
        snapshot: DeviceFirmwareOtaSnapshot,
        deviceUid: DeviceUid,
        targetVersion: String,
        releaseContent: DeviceFirmwareReleaseContent
    ): DeviceOtaState = when (snapshot.phase) {
        DeviceFirmwareOtaPhase.IDLE -> DeviceOtaState.Idle(deviceUid.value)
        DeviceFirmwareOtaPhase.STARTING,
        DeviceFirmwareOtaPhase.SAFE_MODE,
        DeviceFirmwareOtaPhase.DOWNLOADING,
        DeviceFirmwareOtaPhase.WRITING,
        DeviceFirmwareOtaPhase.VERIFYING -> snapshot.inProgressState(
            deviceUid,
            targetVersion,
            releaseContent
        )
        DeviceFirmwareOtaPhase.SUCCEEDED -> if (
            snapshot.lastErrorField ==
            DeviceFirmwareRuntimeContract.ErrorField.SAFE_MODE_RESTORE
        ) {
            DeviceOtaState.Failed(
                deviceUid = deviceUid.value,
                failure = DeviceOtaFailureMapper.snapshot(snapshot)
            )
        } else {
            snapshot.successfulState(deviceUid, targetVersion, releaseContent)
        }
        DeviceFirmwareOtaPhase.FAILED -> DeviceOtaState.Failed(
            deviceUid = deviceUid.value,
            failure = DeviceOtaFailureMapper.snapshot(snapshot)
        )
        DeviceFirmwareOtaPhase.UNKNOWN -> DeviceOtaState.Failed(
            deviceUid = deviceUid.value,
            failure = DeviceOtaFailureMapper.protocol(
                "Firmware reported an unknown OTA phase."
            )
        )
    }

    private fun DeviceFirmwareOtaSnapshot.inProgressState(
        deviceUid: DeviceUid,
        targetVersion: String,
        releaseContent: DeviceFirmwareReleaseContent
    ): DeviceOtaState.InProgress = DeviceOtaState.InProgress(
        deviceUid = deviceUid.value,
        targetVersion = targetVersion,
        phase = phase.toApplicationPhase(),
        progressPermille = progressPermille,
        bytesWritten = bytesWritten,
        contentLength = contentLength,
        releaseContent = releaseContent
    )

    private fun DeviceFirmwareOtaSnapshot.successfulState(
        deviceUid: DeviceUid,
        targetVersion: String,
        releaseContent: DeviceFirmwareReleaseContent
    ): DeviceOtaState = if (restartRequired) {
        DeviceOtaState.RestartRequired(
            deviceUid = deviceUid.value,
            targetVersion = targetVersion,
            restartScheduled = restartScheduled,
            releaseContent = releaseContent
        )
    } else {
        DeviceOtaState.Succeeded(
            deviceUid = deviceUid.value,
            targetVersion = targetVersion,
            releaseContent = releaseContent
        )
    }

    private fun DeviceFirmwareOtaPhase.toApplicationPhase(): DeviceOtaProgressPhase = when (this) {
        DeviceFirmwareOtaPhase.STARTING -> DeviceOtaProgressPhase.STARTING
        DeviceFirmwareOtaPhase.SAFE_MODE -> DeviceOtaProgressPhase.SAFE_MODE
        DeviceFirmwareOtaPhase.DOWNLOADING -> DeviceOtaProgressPhase.DOWNLOADING
        DeviceFirmwareOtaPhase.WRITING -> DeviceOtaProgressPhase.WRITING
        DeviceFirmwareOtaPhase.VERIFYING -> DeviceOtaProgressPhase.VERIFYING
        else -> error("Terminal/unknown OTA phase cannot map to progress.")
    }
}

package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.io.IOException

internal object DeviceOtaValidator {

    fun snapshotAgainstPlan(
        snapshot: DeviceFirmwareOtaSnapshot,
        plan: DeviceFirmwareUpdatePlan?
    ): String? = if (plan == null || snapshot.phase == DeviceFirmwareOtaPhase.IDLE) {
        null
    } else {
        validateTransferIdentity(snapshot, plan) ?: validateCompletedTransfer(snapshot, plan)
    }

    fun installedFirmwareError(
        snapshot: DeviceSnapshot,
        plan: DeviceFirmwareUpdatePlan
    ): String? = when {
        snapshot.product.productKey != plan.productKey ->
            "Reconnected device productKey differs after OTA restart."
        snapshot.product.productId != plan.productId ->
            "Reconnected device productId differs after OTA restart."
        snapshot.product.model != plan.model ->
            "Reconnected device model differs after OTA restart."
        snapshot.product.hardwareRevision != plan.hardwareRevision ->
            "Reconnected device hardwareRevision differs after OTA restart."
        snapshot.firmwareVersion != plan.targetVersion ->
            "Reconnected firmware version does not match the installed OTA target."
        else -> null
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

internal object DeviceOtaFailureMapper {

    fun availability(error: Throwable): DeviceOtaFailure = if (error.hasIoCause()) {
        failure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true,
            diagnosticMessage = error.message.orEmpty()
        )
    } else {
        failure(
            reason = DeviceOtaFailureReason.CHECK_FAILED,
            recoverable = true,
            diagnosticMessage = error.message.orEmpty()
        )
    }

    fun command(outcome: DeviceRuntimeCommandOutcome<*>): DeviceOtaFailure = when (outcome) {
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Cancelled -> failure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true,
            diagnosticMessage = outcome.diagnosticMessage()
        )
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> failure(
            reason = DeviceOtaFailureReason.AUTHENTICATION,
            recoverable = true,
            diagnosticMessage = "Device runtime is not authenticated."
        )
        is DeviceRuntimeCommandOutcome.Timeout -> failure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true,
            diagnosticMessage = "OTA command timed out after ${outcome.timeoutMillis} ms."
        )
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> failure(
            reason = DeviceOtaFailureReason.UNSUPPORTED,
            recoverable = false,
            diagnosticMessage = "Device does not support this OTA command."
        )
        is DeviceRuntimeCommandOutcome.FirmwareError -> firmwareCommand(outcome)
        is DeviceRuntimeCommandOutcome.ProtocolError -> failure(
            reason = DeviceOtaFailureReason.PROTOCOL_MISMATCH,
            recoverable = false,
            diagnosticMessage = outcome.reason
        )
        is DeviceRuntimeCommandOutcome.Success<*> -> error(
            "A successful OTA command cannot be mapped to a failure."
        )
    }

    fun snapshot(snapshot: DeviceFirmwareOtaSnapshot): DeviceOtaFailure =
        when (snapshot.lastErrorField) {
            DeviceFirmwareRuntimeContract.ErrorField.SAFE_MODE -> failure(
                reason = DeviceOtaFailureReason.SAFE_MODE_FAILED,
                recoverable = true,
                field = snapshot.lastErrorField,
                diagnosticMessage = snapshot.lastError
            )
            DeviceFirmwareRuntimeContract.ErrorField.TLS -> failure(
                reason = DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED,
                recoverable = false,
                field = snapshot.lastErrorField,
                diagnosticMessage = snapshot.lastError
            )
            DeviceFirmwareRuntimeContract.ErrorField.URL,
            DeviceFirmwareRuntimeContract.ErrorField.STREAM -> failure(
                reason = DeviceOtaFailureReason.DOWNLOAD_FAILED,
                recoverable = true,
                field = snapshot.lastErrorField,
                httpStatus = snapshot.httpStatus,
                diagnosticMessage = snapshot.lastError
            )
            DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS -> httpFailure(
                httpStatus = snapshot.httpStatus,
                field = snapshot.lastErrorField,
                diagnosticMessage = snapshot.lastError
            )
            DeviceFirmwareRuntimeContract.ErrorField.SIZE,
            DeviceFirmwareRuntimeContract.ErrorField.EXPECTED_SIZE -> failure(
                reason = DeviceOtaFailureReason.INSUFFICIENT_SPACE,
                recoverable = false,
                field = snapshot.lastErrorField,
                diagnosticMessage = snapshot.lastError
            )
            DeviceFirmwareRuntimeContract.ErrorField.SHA256 -> failure(
                reason = DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED,
                recoverable = false,
                field = snapshot.lastErrorField,
                diagnosticMessage = snapshot.lastError
            )
            DeviceFirmwareRuntimeContract.ErrorField.FLASH -> failure(
                reason = DeviceOtaFailureReason.FLASH_WRITE_FAILED,
                recoverable = false,
                field = snapshot.lastErrorField,
                diagnosticMessage = snapshot.lastError
            )
            else -> failure(
                reason = DeviceOtaFailureReason.DEVICE_INTERNAL,
                recoverable = false,
                field = snapshot.lastErrorField,
                httpStatus = snapshot.httpStatus,
                diagnosticMessage = snapshot.lastError
            )
        }

    fun checkFailure(message: String): DeviceOtaFailure = failure(
        reason = DeviceOtaFailureReason.CHECK_FAILED,
        recoverable = true,
        diagnosticMessage = message
    )

    fun connection(message: String): DeviceOtaFailure = failure(
        reason = DeviceOtaFailureReason.CONNECTION,
        recoverable = true,
        diagnosticMessage = message
    )

    fun busy(message: String): DeviceOtaFailure = failure(
        reason = DeviceOtaFailureReason.DEVICE_BUSY,
        recoverable = true,
        diagnosticMessage = message
    )

    fun incompatible(message: String): DeviceOtaFailure = failure(
        reason = DeviceOtaFailureReason.INCOMPATIBLE_FIRMWARE,
        recoverable = false,
        diagnosticMessage = message
    )

    fun protocol(message: String): DeviceOtaFailure = failure(
        reason = DeviceOtaFailureReason.PROTOCOL_MISMATCH,
        recoverable = false,
        diagnosticMessage = message
    )

    fun internal(message: String): DeviceOtaFailure = failure(
        reason = DeviceOtaFailureReason.DEVICE_INTERNAL,
        recoverable = false,
        diagnosticMessage = message
    )

    private fun firmwareCommand(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceOtaFailure = when (error.code) {
        DeviceFirmwareRuntimeContract.ErrorCode.UNAUTHORIZED -> failure(
            reason = DeviceOtaFailureReason.AUTHENTICATION,
            recoverable = true,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorCode.MODULE_NOT_AVAILABLE,
        DeviceFirmwareRuntimeContract.ErrorCode.FEATURE_NOT_AVAILABLE,
        DeviceFirmwareRuntimeContract.ErrorCode.NOT_FOUND -> failure(
            reason = DeviceOtaFailureReason.UNSUPPORTED,
            recoverable = false,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorCode.DEVICE_BUSY -> failure(
            reason = DeviceOtaFailureReason.DEVICE_BUSY,
            recoverable = true,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorCode.STORAGE_ERROR -> failure(
            reason = DeviceOtaFailureReason.FLASH_WRITE_FAILED,
            recoverable = false,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorCode.HARDWARE_ERROR,
        DeviceFirmwareRuntimeContract.ErrorCode.INTERNAL_ERROR -> failure(
            reason = DeviceOtaFailureReason.DEVICE_INTERNAL,
            recoverable = false,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorCode.INVALID_VALUE -> invalidValue(error)
        DeviceFirmwareRuntimeContract.ErrorCode.BAD_REQUEST,
        DeviceFirmwareRuntimeContract.ErrorCode.MISSING_FIELD -> failure(
            reason = DeviceOtaFailureReason.PROTOCOL_MISMATCH,
            recoverable = false,
            error = error
        )
        else -> failure(
            reason = DeviceOtaFailureReason.DEVICE_INTERNAL,
            recoverable = false,
            error = error
        )
    }

    private fun invalidValue(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceOtaFailure = when (error.field) {
        DeviceFirmwareRuntimeContract.ErrorField.WIFI -> failure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.STATE -> failure(
            reason = DeviceOtaFailureReason.DEVICE_BUSY,
            recoverable = true,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.URL,
        DeviceFirmwareRuntimeContract.ErrorField.VERSION,
        DeviceFirmwareRuntimeContract.ErrorField.PRODUCT_KEY,
        DeviceFirmwareRuntimeContract.ErrorField.PRODUCT_ID,
        DeviceFirmwareRuntimeContract.ErrorField.MODEL,
        DeviceFirmwareRuntimeContract.ErrorField.HARDWARE_REVISION -> failure(
            reason = DeviceOtaFailureReason.INCOMPATIBLE_FIRMWARE,
            recoverable = false,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.SHA256 -> failure(
            reason = DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED,
            recoverable = false,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.EXPECTED_SIZE,
        DeviceFirmwareRuntimeContract.ErrorField.SIZE -> failure(
            reason = DeviceOtaFailureReason.INSUFFICIENT_SPACE,
            recoverable = false,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.SAFE_MODE -> failure(
            reason = DeviceOtaFailureReason.SAFE_MODE_FAILED,
            recoverable = true,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.TLS -> failure(
            reason = DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED,
            recoverable = false,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS -> httpFailure(
            httpStatus = error.statusCode,
            code = error.code,
            field = error.field,
            diagnosticMessage = error.message
        )
        DeviceFirmwareRuntimeContract.ErrorField.STREAM -> failure(
            reason = DeviceOtaFailureReason.DOWNLOAD_FAILED,
            recoverable = true,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.FLASH -> failure(
            reason = DeviceOtaFailureReason.FLASH_WRITE_FAILED,
            recoverable = false,
            error = error
        )
        DeviceFirmwareRuntimeContract.ErrorField.TASK -> failure(
            reason = DeviceOtaFailureReason.DEVICE_INTERNAL,
            recoverable = true,
            error = error
        )
        else -> failure(
            reason = DeviceOtaFailureReason.PROTOCOL_MISMATCH,
            recoverable = false,
            error = error
        )
    }

    private fun httpFailure(
        httpStatus: Int,
        code: String = "",
        field: String,
        diagnosticMessage: String
    ): DeviceOtaFailure {
        val releaseMissing = httpStatus == HTTP_NOT_FOUND
        val retryable = httpStatus == 0 ||
            httpStatus == HTTP_REQUEST_TIMEOUT ||
            httpStatus == HTTP_TOO_MANY_REQUESTS ||
            httpStatus >= HTTP_SERVER_ERROR_START
        return failure(
            reason = if (releaseMissing) {
                DeviceOtaFailureReason.RELEASE_UNAVAILABLE
            } else {
                DeviceOtaFailureReason.DOWNLOAD_FAILED
            },
            recoverable = retryable,
            code = code,
            field = field,
            httpStatus = httpStatus,
            diagnosticMessage = diagnosticMessage
        )
    }

    private fun failure(
        reason: DeviceOtaFailureReason,
        recoverable: Boolean,
        code: String = "",
        field: String = "",
        httpStatus: Int = 0,
        diagnosticMessage: String = ""
    ): DeviceOtaFailure = DeviceOtaFailure(
        reason = reason,
        recoverable = recoverable,
        code = code,
        field = field,
        httpStatus = httpStatus,
        diagnosticMessage = diagnosticMessage
    )

    private fun failure(
        reason: DeviceOtaFailureReason,
        recoverable: Boolean,
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceOtaFailure = failure(
        reason = reason,
        recoverable = recoverable,
        code = error.code,
        field = error.field,
        httpStatus = error.statusCode,
        diagnosticMessage = error.message
    )

    private fun Throwable.hasIoCause(): Boolean =
        generateSequence(this) { current -> current.cause }.any { cause -> cause is IOException }

    private fun DeviceRuntimeCommandOutcome<*>.diagnosticMessage(): String = when (this) {
        is DeviceRuntimeCommandOutcome.SendFailed -> "OTA command could not be sent."
        is DeviceRuntimeCommandOutcome.Cancelled -> reason
        else -> "Device runtime is not connected."
    }

    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR_START = 500
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
        DeviceFirmwareOtaPhase.SUCCEEDED -> snapshot.successfulState(
            deviceUid,
            targetVersion,
            releaseContent
        )
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

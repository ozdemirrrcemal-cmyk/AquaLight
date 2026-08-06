package com.aqua.aqualight.application.devices

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** UI-independent application boundary shared by every family-specific update screen. */
interface DeviceFirmwareUpdateOperations {
    fun observe(deviceUid: String): StateFlow<DeviceOtaState> =
        MutableStateFlow(DeviceOtaState.Idle(deviceUid))

    /**
     * Refreshes availability only when the application-owned freshness policy allows it.
     *
     * The default keeps test and alternative implementations source compatible. Production
     * implementations own throttling, concurrency and freshness decisions outside presentation.
     */
    suspend fun refreshAvailabilityIfStale(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean = true
    ): Result<DeviceOtaState> = Result.success(observe(deviceUid).value)

    suspend fun checkAvailability(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean = true
    ): Result<DeviceOtaState> = prepareUpdate(deviceUid, manifestUrl, applyNow).map { plan ->
        DeviceOtaState.UpdateAvailable(plan)
    }

    suspend fun prepareUpdate(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean = true
    ): Result<PreparedDeviceFirmwareUpdate>

    suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate): DeviceFirmwareCommandResult

    suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult

    suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult

    /**
     * Releases every per-device OTA/notification resource after a committed device deletion.
     * The default preserves source compatibility for test and alternative implementations.
     */
    suspend fun releaseDevice(deviceUid: String) = Unit

    fun close() = Unit
}

data class DeviceFirmwareReleaseContent(
    val localeTag: String,
    val title: String,
    val summary: String,
    val changes: List<String>,
    val warnings: List<String>,
    val mandatory: Boolean
) {
    val isPresent: Boolean
        get() = title.isNotBlank() || summary.isNotBlank() || changes.isNotEmpty() || warnings.isNotEmpty()

    companion object {
        val EMPTY = DeviceFirmwareReleaseContent(
            localeTag = "",
            title = "",
            summary = "",
            changes = emptyList(),
            warnings = emptyList(),
            mandatory = false
        )
    }
}

enum class DeviceOtaProgressPhase {
    STARTING,
    SAFE_MODE,
    DOWNLOADING,
    WRITING,
    VERIFYING
}

/** Stable user-facing OTA failure categories derived from the exact firmware contract. */
enum class DeviceOtaFailureReason {
    CHECK_FAILED,
    CONNECTION,
    AUTHENTICATION,
    DEVICE_BUSY,
    UNSUPPORTED,
    RELEASE_UNAVAILABLE,
    RELEASE_ACCESS_DENIED,
    RELEASE_RATE_LIMITED,
    RELEASE_REDIRECT_FAILED,
    RELEASE_REQUEST_REJECTED,
    RELEASE_SERVER_UNAVAILABLE,
    INCOMPATIBLE_FIRMWARE,
    INSUFFICIENT_SPACE,
    DOWNLOAD_CONNECTION_FAILED,
    DOWNLOAD_SEND_FAILED,
    DOWNLOAD_CONNECTION_LOST,
    DOWNLOAD_STREAM_UNAVAILABLE,
    DOWNLOAD_SERVER_NO_RESPONSE,
    DOWNLOAD_DEVICE_MEMORY_LOW,
    DOWNLOAD_ENCODING_UNSUPPORTED,
    DOWNLOAD_STREAM_WRITE_FAILED,
    DOWNLOAD_TIMEOUT,
    DOWNLOAD_URL_OPEN_FAILED,
    DOWNLOAD_STREAM_INTERRUPTED,
    DOWNLOAD_SIZE_MISMATCH,
    DOWNLOAD_FAILED,
    INTEGRITY_CHECK_FAILED,
    SAFE_MODE_FAILED,
    FLASH_WRITE_FAILED,
    SECURITY_VALIDATION_FAILED,
    PROTOCOL_MISMATCH,
    DEVICE_INTERNAL
}

/**
 * Typed OTA failure retained across application and presentation layers.
 *
 * Firmware diagnostics remain available for support and tests, while UI code renders only the
 * stable [reason] through localized resources.
 */
data class DeviceOtaFailure(
    val reason: DeviceOtaFailureReason,
    val recoverable: Boolean,
    val code: String = "",
    val field: String = "",
    val httpStatus: Int = 0,
    val diagnosticMessage: String = ""
)

sealed interface DeviceOtaState {
    val deviceUid: String

    data class Idle(
        override val deviceUid: String
    ) : DeviceOtaState

    data class Checking(
        override val deviceUid: String,
        val currentVersion: String
    ) : DeviceOtaState

    data class Unsupported(
        override val deviceUid: String
    ) : DeviceOtaState

    data class UpToDate(
        override val deviceUid: String,
        val currentVersion: String,
        val latestVersion: String,
        val releaseContent: DeviceFirmwareReleaseContent
    ) : DeviceOtaState

    data class UpdateAvailable(
        val plan: PreparedDeviceFirmwareUpdate
    ) : DeviceOtaState {
        override val deviceUid: String = plan.deviceUid
    }

    data class Starting(
        val plan: PreparedDeviceFirmwareUpdate,
        val requestId: String
    ) : DeviceOtaState {
        override val deviceUid: String = plan.deviceUid
    }

    data class InProgress(
        override val deviceUid: String,
        val targetVersion: String,
        val phase: DeviceOtaProgressPhase,
        val progressPermille: Int,
        val bytesWritten: Long,
        val contentLength: Long,
        val releaseContent: DeviceFirmwareReleaseContent
    ) : DeviceOtaState

    data class Recovering(
        override val deviceUid: String,
        val targetVersion: String,
        val progressPermille: Int
    ) : DeviceOtaState

    data class RestartRequired(
        override val deviceUid: String,
        val targetVersion: String,
        val restartScheduled: Boolean,
        val releaseContent: DeviceFirmwareReleaseContent
    ) : DeviceOtaState

    data class Succeeded(
        override val deviceUid: String,
        val targetVersion: String,
        val releaseContent: DeviceFirmwareReleaseContent
    ) : DeviceOtaState

    data class Failed(
        override val deviceUid: String,
        val failure: DeviceOtaFailure
    ) : DeviceOtaState
}

data class PreparedDeviceFirmwareUpdate(
    val deviceUid: String,
    val currentVersion: String,
    val targetVersion: String,
    val channel: String,
    val environment: String,
    val productKey: String,
    val productId: String,
    val model: String,
    val hardwareRevision: String,
    val displayName: String,
    val filename: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Int,
    val applyNow: Boolean,
    val runtimeMetadataGeneration: Long = 0L,
    val manifestTag: String = "",
    val releaseContent: DeviceFirmwareReleaseContent = DeviceFirmwareReleaseContent.EMPTY
)

data class DeviceFirmwareCommandResult(
    val sent: Boolean,
    val messageId: String = "",
    val failure: DeviceOtaFailure? = null
) {
    val isSuccess: Boolean
        get() = sent && failure == null
}

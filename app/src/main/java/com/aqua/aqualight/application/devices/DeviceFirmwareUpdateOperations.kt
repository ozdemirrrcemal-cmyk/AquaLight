package com.aqua.aqualight.application.devices

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** UI-independent application boundary shared by every family-specific update screen. */
interface DeviceFirmwareUpdateOperations {
    fun observe(deviceUid: String): StateFlow<DeviceOtaState> =
        MutableStateFlow(DeviceOtaState.Idle(deviceUid))

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
        override val deviceUid: String,
        val reason: String
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
        val message: String,
        val field: String = "",
        val recoverable: Boolean = false
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
    val errorMessage: String = ""
) {
    val isSuccess: Boolean
        get() = sent && errorMessage.isBlank()
}

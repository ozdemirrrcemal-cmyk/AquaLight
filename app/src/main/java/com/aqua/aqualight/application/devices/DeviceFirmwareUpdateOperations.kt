package com.aqua.aqualight.application.devices

/** Application boundary for preparing and sending firmware update commands. */
interface DeviceFirmwareUpdateOperations {
    suspend fun prepareUpdate(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean = true
    ): Result<PreparedDeviceFirmwareUpdate>

    fun startUpdate(plan: PreparedDeviceFirmwareUpdate): DeviceFirmwareCommandResult

    fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult

    fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult
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
    val applyNow: Boolean
)

data class DeviceFirmwareCommandResult(
    val sent: Boolean,
    val messageId: String = "",
    val errorMessage: String = ""
) {
    val isSuccess: Boolean
        get() = sent && errorMessage.isBlank()
}

package com.aqua.aqualight.application.devices

/** Aggregate outcome for one owner-scoped background firmware availability pass. */
data class DeviceFirmwareBackgroundRefreshResult(
    val inspectedDeviceCount: Int,
    val eligibleDeviceCount: Int,
    val updateAvailableCount: Int,
    val upToDateCount: Int,
    val skippedDeviceCount: Int,
    val failedDeviceCount: Int
)

/**
 * Owner-scoped firmware availability boundary shared by foreground and WorkManager entry points.
 * Implementations must use the same OTA coordinator and central notification dispatch path.
 */
interface DeviceFirmwareBackgroundOperations {
    suspend fun refreshRegisteredDevices(
        manifestUrl: String,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareBackgroundRefreshResult>

    /** Removes durable/visible firmware notification state for devices no longer owned locally. */
    suspend fun reconcileNotificationState()

    companion object {
        val NoOp: DeviceFirmwareBackgroundOperations = object : DeviceFirmwareBackgroundOperations {
            override suspend fun refreshRegisteredDevices(
                manifestUrl: String,
                applyNow: Boolean
            ): Result<DeviceFirmwareBackgroundRefreshResult> = Result.success(
                DeviceFirmwareBackgroundRefreshResult(
                    inspectedDeviceCount = 0,
                    eligibleDeviceCount = 0,
                    updateAvailableCount = 0,
                    upToDateCount = 0,
                    skippedDeviceCount = 0,
                    failedDeviceCount = 0
                )
            )

            override suspend fun reconcileNotificationState() = Unit
        }
    }
}

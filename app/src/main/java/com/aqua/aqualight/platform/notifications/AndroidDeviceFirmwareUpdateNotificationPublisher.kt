package com.aqua.aqualight.platform.notifications

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.notifications.DeviceUpdateNotification
import com.aqua.aqualight.application.notifications.NotificationDispatchResult
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger

/** Localized device-update notification projection for the owner-scoped OTA state machine. */
internal class AndroidDeviceFirmwareUpdateNotificationPublisher(
    context: Context,
    private val ownerUid: String,
    private val dispatchUseCase: NotificationDispatchUseCase,
    private val ledger: DeviceUpdateNotificationLedger =
        DeviceUpdateNotificationLedger.create(context)
) {
    private val appContext = context.applicationContext

    /**
     * Active OTA state notifications remain driven by the live coordinator. Availability alerts are
     * deliberately excluded here so manual and foreground checks never create a system alert.
     */
    suspend fun publish(state: DeviceOtaState, deviceName: String) {
        val normalizedName = normalizeDeviceName(deviceName)
        state.toNotification(normalizedName)?.let { notification ->
            dispatchUseCase.dispatchDeviceUpdate(notification)
        }
    }

    /** Posts a background-discovered update once for each owner/device/target version. */
    suspend fun publishAvailabilityHint(
        hint: DeviceFirmwareAvailabilityHint.UpdateAvailable
    ): Boolean {
        if (
            ledger.isAnnounced(
                ownerUid = ownerUid,
                deviceUid = hint.deviceUid,
                targetVersion = hint.targetVersion
            )
        ) {
            return false
        }

        val result = dispatchUseCase.dispatchDeviceUpdate(
            availableNotification(
                deviceUid = hint.deviceUid,
                deviceName = normalizeDeviceName(hint.deviceName),
                currentVersion = hint.currentVersion,
                targetVersion = hint.targetVersion
            )
        )
        if (result == NotificationDispatchResult.POSTED) {
            ledger.markAnnounced(
                ownerUid = ownerUid,
                deviceUid = hint.deviceUid,
                targetVersion = hint.targetVersion
            )
            return true
        }
        return false
    }

    private fun DeviceOtaState.toNotification(
        deviceName: String
    ): DeviceUpdateNotification? = when (this) {
        is DeviceOtaState.UpdateAvailable -> null
        is DeviceOtaState.Starting -> progressNotification(
            deviceName = deviceName,
            targetVersion = plan.targetVersion,
            phaseLabel = text(R.string.device_settings_update_status_preparing),
            progressPercent = 0
        )
        is DeviceOtaState.InProgress -> progressNotification(
            deviceName = deviceName,
            targetVersion = targetVersion,
            phaseLabel = text(phase.notificationTextRes()),
            progressPercent = progressPermille.toProgressPercent()
        )
        is DeviceOtaState.Recovering -> progressNotification(
            deviceName = deviceName,
            targetVersion = targetVersion,
            phaseLabel = text(R.string.device_settings_update_status_recovering),
            progressPercent = progressPermille.toProgressPercent()
        )
        is DeviceOtaState.RestartRequired -> restartNotification(deviceName)
        is DeviceOtaState.Succeeded -> successNotification(deviceName)
        is DeviceOtaState.Failed -> failureNotification(deviceName)
        is DeviceOtaState.Idle,
        is DeviceOtaState.Checking,
        is DeviceOtaState.Unsupported,
        is DeviceOtaState.UpToDate -> null
    }

    private fun availableNotification(
        deviceUid: String,
        deviceName: String,
        currentVersion: String,
        targetVersion: String
    ): DeviceUpdateNotification {
        require(currentVersion.isNotBlank()) {
            "Current OTA notification version is missing."
        }
        require(targetVersion.isNotBlank()) {
            "Target OTA notification version is missing."
        }
        return DeviceUpdateNotification(
            ownerUid = ownerUid,
            deviceUid = deviceUid,
            title = text(
                R.string.device_update_background_notification_title,
                deviceName
            ),
            message = text(
                R.string.device_update_background_notification_versions,
                currentVersion,
                targetVersion
            ),
            actionLabel = text(R.string.device_update_background_notification_action)
        )
    }

    private fun DeviceOtaState.RestartRequired.restartNotification(
        deviceName: String
    ): DeviceUpdateNotification = DeviceUpdateNotification(
        ownerUid = ownerUid,
        deviceUid = deviceUid,
        title = text(R.string.device_settings_update_notification_progress_title, deviceName),
        message = text(
            R.string.device_settings_update_notification_restart_message,
            targetVersion
        ),
        progressPercent = COMPLETE_PROGRESS_PERCENT,
        ongoing = true
    )

    private fun DeviceOtaState.Succeeded.successNotification(
        deviceName: String
    ): DeviceUpdateNotification = DeviceUpdateNotification(
        ownerUid = ownerUid,
        deviceUid = deviceUid,
        title = text(R.string.device_settings_update_notification_success_title),
        message = text(
            R.string.device_settings_update_notification_success_message,
            deviceName,
            targetVersion
        ),
        progressPercent = COMPLETE_PROGRESS_PERCENT
    )

    private fun DeviceOtaState.Failed.failureNotification(
        deviceName: String
    ): DeviceUpdateNotification = DeviceUpdateNotification(
        ownerUid = ownerUid,
        deviceUid = deviceUid,
        title = text(R.string.device_settings_update_notification_failure_title),
        message = text(
            R.string.device_settings_update_notification_failure_message,
            deviceName
        )
    )

    private fun DeviceOtaState.progressNotification(
        deviceName: String,
        targetVersion: String,
        phaseLabel: String,
        progressPercent: Int
    ): DeviceUpdateNotification = DeviceUpdateNotification(
        ownerUid = ownerUid,
        deviceUid = deviceUid,
        title = text(R.string.device_settings_update_notification_progress_title, deviceName),
        message = text(
            R.string.device_settings_update_notification_progress_message,
            phaseLabel,
            progressPercent
        ),
        progressPercent = progressPercent,
        ongoing = true
    ).also {
        require(targetVersion.isNotBlank()) {
            "OTA notification target version is missing."
        }
    }

    private fun normalizeDeviceName(deviceName: String): String {
        return deviceName.trim().ifBlank {
            appContext.getString(R.string.device_settings_update_device_fallback)
        }
    }

    private fun text(resourceId: Int, vararg args: Any): String =
        appContext.getString(resourceId, *args)
}

private fun DeviceOtaProgressPhase.notificationTextRes(): Int = when (this) {
    DeviceOtaProgressPhase.STARTING -> R.string.device_settings_update_status_preparing
    DeviceOtaProgressPhase.SAFE_MODE -> R.string.device_settings_update_phase_safe_mode
    DeviceOtaProgressPhase.DOWNLOADING -> R.string.device_settings_update_phase_downloading
    DeviceOtaProgressPhase.WRITING -> R.string.device_settings_update_phase_writing
    DeviceOtaProgressPhase.VERIFYING -> R.string.device_settings_update_phase_verifying
}

private fun Int.toProgressPercent(): Int =
    coerceIn(0, COMPLETE_PROGRESS_PERMILLE) / PERMILLE_PER_PERCENT

private const val COMPLETE_PROGRESS_PERMILLE = 1_000
private const val PERMILLE_PER_PERCENT = 10
private const val COMPLETE_PROGRESS_PERCENT = 100

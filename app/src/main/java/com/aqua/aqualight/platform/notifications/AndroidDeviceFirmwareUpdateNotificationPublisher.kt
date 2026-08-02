package com.aqua.aqualight.platform.notifications

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.notifications.DeviceUpdateNotification
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase

/** Localized device-update notification projection for the owner-scoped OTA state machine. */
internal class AndroidDeviceFirmwareUpdateNotificationPublisher(
    context: Context,
    private val ownerUid: String,
    private val dispatchUseCase: NotificationDispatchUseCase
) {
    private val appContext = context.applicationContext

    suspend fun publish(state: DeviceOtaState, deviceName: String) {
        val normalizedName = deviceName.trim().ifBlank {
            appContext.getString(R.string.device_settings_update_device_fallback)
        }
        state.toNotification(normalizedName)?.let { notification ->
            dispatchUseCase.dispatchDeviceUpdate(notification)
        }
    }

    private fun DeviceOtaState.toNotification(
        deviceName: String
    ): DeviceUpdateNotification? = when (this) {
        is DeviceOtaState.UpdateAvailable -> DeviceUpdateNotification(
            ownerUid = ownerUid,
            deviceUid = deviceUid,
            title = text(R.string.device_settings_update_notification_available_title),
            message = text(
                R.string.device_settings_update_notification_available_message,
                deviceName,
                plan.targetVersion
            )
        )
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
        require(targetVersion.isNotBlank()) { "OTA notification target version is missing." }
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

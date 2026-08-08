@file:Suppress("LongParameterList", "TooManyFunctions")

package com.aqua.aqualight.platform.notifications

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.notifications.DeviceUpdateNotification
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint

/** Converts device-update domain state into localized central notification models. */
internal class DeviceFirmwareUpdateNotificationFactory(context: Context) {

    private val appContext = context.applicationContext

    fun fromOtaState(
        ownerUid: String,
        state: DeviceOtaState,
        deviceName: String
    ): DeviceUpdateNotification? {
        val normalizedName = normalizeDeviceName(deviceName)
        return when (state) {
            is DeviceOtaState.UpdateAvailable -> null
            is DeviceOtaState.Starting -> progress(
                ownerUid,
                state,
                normalizedName,
                state.plan.targetVersion,
                text(R.string.device_settings_update_status_preparing),
                0
            )
            is DeviceOtaState.InProgress -> progress(
                ownerUid,
                state,
                normalizedName,
                state.targetVersion,
                text(state.phase.notificationTextRes()),
                state.progressPermille.toProgressPercent()
            )
            is DeviceOtaState.Recovering -> progress(
                ownerUid,
                state,
                normalizedName,
                state.targetVersion,
                text(R.string.device_settings_update_status_recovering),
                state.progressPermille.toProgressPercent()
            )
            is DeviceOtaState.RestartRequired -> restart(ownerUid, state, normalizedName)
            is DeviceOtaState.Succeeded -> success(ownerUid, state, normalizedName)
            is DeviceOtaState.Failed -> if (
                state.failure.stage == DeviceOtaFailureStage.UPDATE_EXECUTION
            ) {
                failure(ownerUid, state, normalizedName)
            } else {
                null
            }
            is DeviceOtaState.Idle,
            is DeviceOtaState.Checking,
            is DeviceOtaState.Unsupported,
            is DeviceOtaState.UpToDate -> null
        }
    }

    fun availability(
        ownerUid: String,
        hint: DeviceFirmwareAvailabilityHint.UpdateAvailable
    ): DeviceUpdateNotification {
        require(hint.currentVersion.isNotBlank()) {
            "Current OTA notification version is missing."
        }
        require(hint.targetVersion.isNotBlank()) {
            "Target OTA notification version is missing."
        }
        return DeviceUpdateNotification(
            ownerUid = ownerUid,
            deviceUid = hint.deviceUid,
            title = text(
                R.string.device_update_background_notification_title,
                normalizeDeviceName(hint.deviceName)
            ),
            message = text(
                R.string.device_update_background_notification_versions,
                hint.currentVersion,
                hint.targetVersion
            ),
            actionLabel = text(R.string.device_update_background_notification_action)
        )
    }

    private fun restart(
        ownerUid: String,
        state: DeviceOtaState.RestartRequired,
        deviceName: String
    ): DeviceUpdateNotification = DeviceUpdateNotification(
        ownerUid = ownerUid,
        deviceUid = state.deviceUid,
        title = text(R.string.device_settings_update_notification_progress_title, deviceName),
        message = text(
            R.string.device_settings_update_notification_restart_message,
            state.targetVersion
        ),
        progressPercent = COMPLETE_PROGRESS_PERCENT,
        ongoing = true
    )

    private fun success(
        ownerUid: String,
        state: DeviceOtaState.Succeeded,
        deviceName: String
    ): DeviceUpdateNotification = DeviceUpdateNotification(
        ownerUid = ownerUid,
        deviceUid = state.deviceUid,
        title = text(R.string.device_settings_update_notification_success_title),
        message = text(
            R.string.device_settings_update_notification_success_message,
            deviceName,
            state.targetVersion
        ),
        progressPercent = COMPLETE_PROGRESS_PERCENT
    )

    private fun failure(
        ownerUid: String,
        state: DeviceOtaState.Failed,
        deviceName: String
    ): DeviceUpdateNotification = DeviceUpdateNotification(
        ownerUid = ownerUid,
        deviceUid = state.deviceUid,
        title = text(R.string.device_settings_update_notification_failure_title),
        message = text(
            R.string.device_settings_update_notification_failure_message,
            deviceName
        )
    )

    private fun progress(
        ownerUid: String,
        state: DeviceOtaState,
        deviceName: String,
        targetVersion: String,
        phaseLabel: String,
        progressPercent: Int
    ): DeviceUpdateNotification {
        require(targetVersion.isNotBlank()) {
            "OTA notification target version is missing."
        }
        return DeviceUpdateNotification(
            ownerUid = ownerUid,
            deviceUid = state.deviceUid,
            title = text(
                R.string.device_settings_update_notification_progress_title,
                deviceName
            ),
            message = text(
                R.string.device_settings_update_notification_progress_message,
                phaseLabel,
                progressPercent
            ),
            progressPercent = progressPercent,
            ongoing = true
        )
    }

    private fun normalizeDeviceName(deviceName: String): String {
        return deviceName.trim().ifBlank {
            appContext.getString(R.string.device_settings_update_device_fallback)
        }
    }

    private fun text(resourceId: Int, vararg args: Any): String {
        return appContext.getString(resourceId, *args)
    }
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

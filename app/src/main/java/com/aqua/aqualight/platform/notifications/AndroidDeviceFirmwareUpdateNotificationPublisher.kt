package com.aqua.aqualight.platform.notifications

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.notifications.DeviceUpdateNotification
import com.aqua.aqualight.application.notifications.NotificationDispatchResult
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Localized device-update notification projection for the owner-scoped OTA state machine. */
@Suppress("TooManyFunctions", "LongParameterList")
internal class AndroidDeviceFirmwareUpdateNotificationPublisher(
    context: Context,
    private val ownerUid: String,
    private val dispatchUseCase: NotificationDispatchUseCase,
    private val ledger: DeviceUpdateNotificationLedger = DeviceUpdateNotificationLedger.noOp(),
    private val cancelDeviceUpdate: (String, String) -> Unit = { _, _ -> },
    private val ownerIsActive: () -> Boolean = { true },
    private val deviceIsOwned: (String) -> Boolean = { true }
) {
    private val appContext = context.applicationContext
    private val deviceLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun publish(state: DeviceOtaState, deviceName: String) {
        val deviceUid = requireDeviceUid(state.deviceUid)
        deviceLocks.computeIfAbsent(deviceUid) { Mutex() }.withLock {
            if (!ownerIsActive() || !deviceIsOwned(deviceUid)) return@withLock
            publishLocked(state, deviceName)
        }
    }

    suspend fun dismissDevice(deviceUid: String) {
        val normalizedDeviceUid = requireDeviceUid(deviceUid)
        deviceLocks.computeIfAbsent(normalizedDeviceUid) { Mutex() }.withLock {
            cancelDeviceUpdate(ownerUid, normalizedDeviceUid)
        }
    }

    suspend fun clearDevice(deviceUid: String) {
        val normalizedDeviceUid = requireDeviceUid(deviceUid)
        deviceLocks.computeIfAbsent(normalizedDeviceUid) { Mutex() }.withLock {
            cancelDeviceUpdate(ownerUid, normalizedDeviceUid)
            ledger.clearDevice(ownerUid, normalizedDeviceUid)
        }
    }

    private suspend fun publishLocked(state: DeviceOtaState, deviceName: String) {
        when (state) {
            is DeviceOtaState.Idle -> {
                cancelDeviceUpdate(ownerUid, state.deviceUid)
                ledger.clearDevice(ownerUid, state.deviceUid)
            }

            is DeviceOtaState.Checking -> Unit

            is DeviceOtaState.Unsupported -> {
                cancelDeviceUpdate(ownerUid, state.deviceUid)
                ledger.clearDevice(ownerUid, state.deviceUid)
            }

            is DeviceOtaState.UpToDate -> {
                cancelDeviceUpdate(ownerUid, state.deviceUid)
                ledger.markResolved(
                    ownerUid = ownerUid,
                    deviceUid = state.deviceUid,
                    resolvedVersion = state.currentVersion.ifBlank { state.latestVersion }
                )
            }

            is DeviceOtaState.UpdateAvailable -> publishAvailability(state, deviceName)

            else -> publishState(state, deviceName)
        }
    }

    private suspend fun publishAvailability(
        state: DeviceOtaState.UpdateAvailable,
        deviceName: String
    ) {
        val targetVersion = state.plan.targetVersion.trim()
        if (
            !ledger.shouldDeliverAvailability(
                ownerUid = ownerUid,
                deviceUid = state.deviceUid,
                targetVersion = targetVersion
            )
        ) {
            return
        }
        publishNotification(
            state = state,
            deviceName = deviceName,
            targetVersion = targetVersion,
            deliveryKey = "available:$targetVersion"
        )
    }

    private suspend fun publishState(state: DeviceOtaState, deviceName: String) {
        val targetVersion = state.targetVersionOrEmpty()
        val deliveryKey = state.deliveryKey()
        publishNotification(
            state = state,
            deviceName = deviceName,
            targetVersion = targetVersion,
            deliveryKey = deliveryKey
        )
    }

    private suspend fun publishNotification(
        state: DeviceOtaState,
        deviceName: String,
        targetVersion: String,
        deliveryKey: String
    ) {
        val normalizedName = deviceName.trim().ifBlank {
            appContext.getString(R.string.device_settings_update_device_fallback)
        }
        val notification = state.toNotification(normalizedName)
        val canDispatch = notification != null &&
            ownerIsActive() &&
            deviceIsOwned(state.deviceUid)

        if (canDispatch) {
            val result = dispatchUseCase.dispatchDeviceUpdate(requireNotNull(notification))
            val postedForCurrentOwner = result == NotificationDispatchResult.POSTED &&
                ownerIsActive() &&
                deviceIsOwned(state.deviceUid)
            if (postedForCurrentOwner) {
                persistDelivery(state, targetVersion, deliveryKey)
            }
        }
    }

    private suspend fun persistDelivery(
        state: DeviceOtaState,
        targetVersion: String,
        deliveryKey: String
    ) {
        if (state is DeviceOtaState.Succeeded) {
            ledger.markResolved(ownerUid, state.deviceUid, state.targetVersion)
        } else {
            ledger.markDelivered(
                ownerUid = ownerUid,
                deviceUid = state.deviceUid,
                targetVersion = targetVersion,
                deliveryKey = deliveryKey
            )
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

    private fun requireDeviceUid(deviceUid: String): String {
        return deviceUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "deviceUid must not be blank" }
        }
    }
}

private fun DeviceOtaState.deliveryKey(): String = when (this) {
    is DeviceOtaState.Starting -> "starting:${plan.targetVersion}"
    is DeviceOtaState.InProgress ->
        "progress:$targetVersion:$phase:${progressPermille.toProgressPercent()}"
    is DeviceOtaState.Recovering ->
        "recovering:$targetVersion:${progressPermille.toProgressPercent()}"
    is DeviceOtaState.RestartRequired -> "restart:$targetVersion:$restartScheduled"
    is DeviceOtaState.Succeeded -> "succeeded:$targetVersion"
    is DeviceOtaState.Failed -> with(failure) {
        "failed:$reason:$code:$field:$httpStatus:$recoverable"
    }
    is DeviceOtaState.UpdateAvailable -> "available:${plan.targetVersion}"
    is DeviceOtaState.Idle -> "idle"
    is DeviceOtaState.Checking -> "checking"
    is DeviceOtaState.Unsupported -> "unsupported"
    is DeviceOtaState.UpToDate -> "up-to-date:$currentVersion:$latestVersion"
}

private fun DeviceOtaState.targetVersionOrEmpty(): String = when (this) {
    is DeviceOtaState.UpdateAvailable -> plan.targetVersion
    is DeviceOtaState.Starting -> plan.targetVersion
    is DeviceOtaState.InProgress -> targetVersion
    is DeviceOtaState.Recovering -> targetVersion
    is DeviceOtaState.RestartRequired -> targetVersion
    is DeviceOtaState.Succeeded -> targetVersion
    is DeviceOtaState.UpToDate -> latestVersion
    is DeviceOtaState.Idle,
    is DeviceOtaState.Checking,
    is DeviceOtaState.Unsupported,
    is DeviceOtaState.Failed -> ""
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

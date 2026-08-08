package com.aqua.aqualight.application.notifications

import kotlinx.coroutines.flow.Flow

enum class NotificationCategory {
    CARE_REMINDERS,
    DEVICE_ALERTS,
    DEVICE_UPDATES
}

enum class CareReminderKind {
    WATER_CHANGE,
    FEEDING,
    FILTER_MAINTENANCE,
    FILTER_CHANGE,
    PRE_FILTER_CLEANING,
    PIPE_CLEANING,
    DIFFUSER_CLEANING,
    HOSE_CLEANING,
    GLASS_CLEANING,
    ALGAE_CLEANING,
    PLANT_TRIM,
    FERTILIZER_DOSING,
    PLANT_HEALTH_CHECK,
    CO2_CHECK,
    LIGHT_CHECK,
    WATER_TEST,
    TEMPERATURE_CHECK,
    SUBSTRATE_CLEANING,
    LIVESTOCK_CHECK,
    DEVICE_CHECK,
    CUSTOM
}

enum class NotificationChannelState {
    NOT_REQUIRED,
    MISSING,
    BLOCKED,
    ENABLED
}

data class NotificationDeliveryReadiness(
    val runtimePermissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val channelState: NotificationChannelState
) {
    val canDeliver: Boolean
        get() = runtimePermissionGranted &&
            appNotificationsEnabled &&
            channelState != NotificationChannelState.MISSING &&
            channelState != NotificationChannelState.BLOCKED
}

data class NotificationPreferenceSnapshot(
    val ownerPreferenceEnabled: Boolean,
    val delivery: Map<NotificationCategory, NotificationDeliveryReadiness>
) {
    fun readiness(category: NotificationCategory): NotificationDeliveryReadiness {
        return delivery.getValue(category)
    }

    val allCategoriesDeliverable: Boolean
        get() = delivery.values.all(NotificationDeliveryReadiness::canDeliver)
}

data class CareReminderNotification(
    val ownerUid: String,
    val taskId: Long,
    val kind: CareReminderKind,
    val title: String,
    val message: String
)

data class DeviceAlertNotification(
    val ownerUid: String,
    val deviceUid: String,
    val title: String,
    val message: String
)

enum class DeviceFirmwareNotificationKind {
    AVAILABILITY,
    OPERATION
}

data class DeviceFirmwareNotificationRoute(
    val kind: DeviceFirmwareNotificationKind,
    val targetVersion: String = ""
)

data class DeviceUpdateNotification(
    val ownerUid: String,
    val deviceUid: String,
    val title: String,
    val message: String,
    val progressPercent: Int? = null,
    val ongoing: Boolean = false,
    val actionLabel: String? = null,
    val route: DeviceFirmwareNotificationRoute = DeviceFirmwareNotificationRoute(
        DeviceFirmwareNotificationKind.OPERATION
    )
)

enum class NotificationDispatchResult {
    POSTED,
    OWNER_PREFERENCE_DISABLED,
    SYSTEM_BLOCKED
}

interface NotificationPreferenceRepository {
    fun enabledFlow(ownerUid: String): Flow<Boolean>
    suspend fun isEnabled(ownerUid: String): Boolean
    suspend fun setEnabled(ownerUid: String, enabled: Boolean)
}

interface NotificationPermissionPolicy {
    fun ensureChannels()
    fun evaluate(category: NotificationCategory): NotificationDeliveryReadiness
    fun channelId(category: NotificationCategory): String
}

interface NotificationScheduler {
    suspend fun scheduleCareTask(ownerUid: String, taskId: Long)
    suspend fun cancelCareTask(ownerUid: String, taskId: Long)
    suspend fun reconcileOwner(ownerUid: String)
    suspend fun cancelOwner(ownerUid: String)
}

interface DeviceUpdateNotificationWorkCoordinator {
    suspend fun reconcileOwner(ownerUid: String)
    fun cancelOwner(ownerUid: String)
}

interface NotificationRenderer {
    fun renderCareReminder(notification: CareReminderNotification)
    fun renderDeviceAlert(notification: DeviceAlertNotification)
    fun renderDeviceUpdate(notification: DeviceUpdateNotification)
    fun cancelCareReminder(ownerUid: String, taskId: Long)
    fun cancelOwner(ownerUid: String)
}

class NotificationDispatchUseCase(
    private val repository: NotificationPreferenceRepository,
    private val permissionPolicy: NotificationPermissionPolicy,
    private val renderer: NotificationRenderer
) {
    suspend fun dispatchCareReminder(
        notification: CareReminderNotification
    ): NotificationDispatchResult {
        return dispatch(
            ownerUid = notification.ownerUid,
            category = NotificationCategory.CARE_REMINDERS
        ) {
            renderer.renderCareReminder(notification)
        }
    }

    suspend fun dispatchDeviceAlert(
        notification: DeviceAlertNotification
    ): NotificationDispatchResult {
        return dispatch(
            ownerUid = notification.ownerUid,
            category = NotificationCategory.DEVICE_ALERTS
        ) {
            renderer.renderDeviceAlert(notification)
        }
    }

    suspend fun dispatchDeviceUpdate(
        notification: DeviceUpdateNotification
    ): NotificationDispatchResult {
        return dispatch(
            ownerUid = notification.ownerUid,
            category = NotificationCategory.DEVICE_UPDATES
        ) {
            renderer.renderDeviceUpdate(notification)
        }
    }

    private suspend fun dispatch(
        ownerUid: String,
        category: NotificationCategory,
        render: () -> Unit
    ): NotificationDispatchResult {
        if (!repository.isEnabled(ownerUid)) {
            return NotificationDispatchResult.OWNER_PREFERENCE_DISABLED
        }

        permissionPolicy.ensureChannels()
        return if (!permissionPolicy.evaluate(category).canDeliver) {
            NotificationDispatchResult.SYSTEM_BLOCKED
        } else {
            render()
            NotificationDispatchResult.POSTED
        }
    }
}

class NotificationPreferenceUseCase(
    private val repository: NotificationPreferenceRepository,
    private val permissionPolicy: NotificationPermissionPolicy,
    private val scheduler: NotificationScheduler,
    private val deviceUpdateWorkCoordinator: DeviceUpdateNotificationWorkCoordinator,
    private val renderer: NotificationRenderer
) {
    fun observe(ownerUid: String): Flow<Boolean> = repository.enabledFlow(ownerUid)

    fun channelId(category: NotificationCategory): String {
        return permissionPolicy.channelId(category)
    }

    suspend fun snapshot(ownerUid: String): NotificationPreferenceSnapshot {
        permissionPolicy.ensureChannels()
        val enabled = repository.isEnabled(ownerUid)
        val readiness = NotificationCategory.entries.associateWith(permissionPolicy::evaluate)
        return NotificationPreferenceSnapshot(
            ownerPreferenceEnabled = enabled,
            delivery = readiness
        )
    }

    suspend fun setEnabled(ownerUid: String, enabled: Boolean) {
        repository.setEnabled(ownerUid, enabled)
        if (enabled) {
            permissionPolicy.ensureChannels()
            deviceUpdateWorkCoordinator.reconcileOwner(ownerUid)
            scheduler.reconcileOwner(ownerUid)
        } else {
            deviceUpdateWorkCoordinator.cancelOwner(ownerUid)
            scheduler.cancelOwner(ownerUid)
            renderer.cancelOwner(ownerUid)
        }
    }

    suspend fun scheduleCareTask(ownerUid: String, taskId: Long) {
        if (!repository.isEnabled(ownerUid)) {
            scheduler.cancelCareTask(ownerUid, taskId)
            renderer.cancelCareReminder(ownerUid, taskId)
            return
        }
        scheduler.scheduleCareTask(ownerUid, taskId)
    }

    suspend fun cancelCareTask(ownerUid: String, taskId: Long) {
        scheduler.cancelCareTask(ownerUid, taskId)
        renderer.cancelCareReminder(ownerUid, taskId)
    }

    suspend fun reconcileOwner(ownerUid: String) {
        if (repository.isEnabled(ownerUid)) {
            permissionPolicy.ensureChannels()
            scheduler.reconcileOwner(ownerUid)
        } else {
            deviceUpdateWorkCoordinator.cancelOwner(ownerUid)
            scheduler.cancelOwner(ownerUid)
            renderer.cancelOwner(ownerUid)
        }
    }

    suspend fun cancelOwner(ownerUid: String) {
        deviceUpdateWorkCoordinator.cancelOwner(ownerUid)
        scheduler.cancelOwner(ownerUid)
        renderer.cancelOwner(ownerUid)
    }
}

package com.aqua.aqualight.data.devices.update

import android.content.Context
import com.aqua.aqualight.application.notifications.DeviceUpdateNotificationWorkCoordinator
import com.aqua.aqualight.application.notifications.NotificationPreferenceRepository
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import kotlinx.coroutines.flow.first

/** Central owner lifecycle and user-policy adapter for durable device-update discovery work. */
internal class DefaultDeviceUpdateNotificationWorkCoordinator(
    private val areNotificationsEnabled: suspend (String) -> Boolean,
    private val isAutomaticCheckEnabled: suspend () -> Boolean,
    private val scheduleWork: (String) -> Unit,
    private val cancelWork: (String) -> Unit
) : DeviceUpdateNotificationWorkCoordinator {

    override suspend fun reconcileOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        if (areNotificationsEnabled(owner) && isAutomaticCheckEnabled()) {
            scheduleWork(owner)
        } else {
            cancelWork(owner)
        }
    }

    override fun cancelOwner(ownerUid: String) {
        cancelWork(requireOwnerUid(ownerUid))
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
    }

    companion object {
        fun create(
            context: Context,
            notificationPreferences: NotificationPreferenceRepository,
            userPreferences: UserPreferencesManager
        ): DefaultDeviceUpdateNotificationWorkCoordinator {
            val appContext = context.applicationContext
            return DefaultDeviceUpdateNotificationWorkCoordinator(
                areNotificationsEnabled = notificationPreferences::isEnabled,
                isAutomaticCheckEnabled = {
                    userPreferences.autoUpdateEnabled.first()
                },
                scheduleWork = { ownerUid ->
                    DeviceFirmwareAvailabilityWorker.schedule(appContext, ownerUid)
                },
                cancelWork = { ownerUid ->
                    DeviceFirmwareAvailabilityWorker.cancel(appContext, ownerUid)
                }
            )
        }
    }
}

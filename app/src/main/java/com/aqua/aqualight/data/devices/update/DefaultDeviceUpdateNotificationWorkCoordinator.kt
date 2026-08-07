package com.aqua.aqualight.data.devices.update

import android.content.Context
import com.aqua.aqualight.application.notifications.DeviceUpdateNotificationWorkCoordinator
import com.aqua.aqualight.application.notifications.NotificationPreferenceRepository
import com.aqua.aqualight.data.user.UserDataScope

/** Central owner lifecycle adapter for durable device-update discovery work. */
internal class DefaultDeviceUpdateNotificationWorkCoordinator(
    private val isEnabled: suspend (String) -> Boolean,
    private val scheduleWork: (String) -> Unit,
    private val cancelWork: (String) -> Unit
) : DeviceUpdateNotificationWorkCoordinator {

    override suspend fun reconcileOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        if (isEnabled(owner)) {
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
            preferences: NotificationPreferenceRepository
        ): DefaultDeviceUpdateNotificationWorkCoordinator {
            val appContext = context.applicationContext
            return DefaultDeviceUpdateNotificationWorkCoordinator(
                isEnabled = preferences::isEnabled,
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

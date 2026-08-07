package com.aqua.aqualight.data.devices.notification

import android.content.Context
import com.aqua.aqualight.application.notifications.NotificationBackgroundWorkController

/** Central notification-preference lifecycle adapter for owner-scoped firmware availability work. */
internal class DeviceFirmwareNotificationBackgroundWorkController(
    context: Context
) : NotificationBackgroundWorkController {
    private val appContext = context.applicationContext

    override fun scheduleOwner(ownerUid: String, enqueueImmediate: Boolean) {
        DeviceFirmwareAvailabilityWorker.schedule(
            context = appContext,
            ownerUid = ownerUid,
            enqueueImmediate = enqueueImmediate
        )
    }

    override fun cancelOwner(ownerUid: String) {
        DeviceFirmwareAvailabilityWorker.cancel(appContext, ownerUid)
    }
}

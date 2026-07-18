package com.aqua.aqualight.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.aqua.aqualight.R
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationChannelState

/**
 * Permanent central registry for every AquaLight notification category.
 *
 * IDs are intentionally semantic and unversioned because AquaLight has not shipped
 * a legacy channel contract. IDs must remain stable after release unless a genuinely
 * incompatible channel migration is required.
 */
object NotificationChannelRegistry {

    const val CARE_REMINDERS = "care_reminders"
    const val DEVICE_ALERTS = "device_alerts"
    const val DEVICE_UPDATES = "device_updates"

    fun channelId(category: NotificationCategory): String = when (category) {
        NotificationCategory.CARE_REMINDERS -> CARE_REMINDERS
        NotificationCategory.DEVICE_ALERTS -> DEVICE_ALERTS
        NotificationCategory.DEVICE_UPDATES -> DEVICE_UPDATES
    }

    fun ensureAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CARE_REMINDERS,
                    appContext.getString(R.string.notification_channel_care_reminders_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = appContext.getString(
                        R.string.notification_channel_care_reminders_description
                    )
                },
                NotificationChannel(
                    DEVICE_ALERTS,
                    appContext.getString(R.string.notification_channel_device_alerts_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = appContext.getString(
                        R.string.notification_channel_device_alerts_description
                    )
                },
                NotificationChannel(
                    DEVICE_UPDATES,
                    appContext.getString(R.string.notification_channel_device_updates_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = appContext.getString(
                        R.string.notification_channel_device_updates_description
                    )
                }
            )
        )
    }

    fun readState(
        context: Context,
        category: NotificationCategory
    ): NotificationChannelState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return NotificationChannelState.NOT_REQUIRED
        }

        val manager = context.applicationContext
            .getSystemService(NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(channelId(category))

        return when {
            channel == null -> NotificationChannelState.MISSING
            channel.importance == NotificationManager.IMPORTANCE_NONE -> {
                NotificationChannelState.BLOCKED
            }
            else -> NotificationChannelState.ENABLED
        }
    }
}

package com.aqua.aqualight.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.aqua.aqualight.R

/**
 * Central registry and state reader for AquaLight notification channels.
 *
 * Channel creation is intentionally idempotent. Android preserves user-selected
 * sound, vibration and importance after the first creation, while allowing the app
 * to refresh the localized channel name and description.
 */
object NotificationChannelRegistry {

    const val CARE_REMINDERS_CHANNEL_ID = "care_reminders_v1"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
            ?: return

        val careReminders = NotificationChannel(
            CARE_REMINDERS_CHANNEL_ID,
            appContext.getString(R.string.notification_channel_care_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(
                R.string.notification_channel_care_reminders_description
            )
        }

        manager.createNotificationChannel(careReminders)
    }

    fun readState(context: Context): NotificationSystemState {
        val appContext = context.applicationContext
        val appEnabled = NotificationManagerCompat.from(appContext)
            .areNotificationsEnabled()

        val channelState = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            NotificationChannelState.NOT_REQUIRED
        } else {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            val channel = manager?.getNotificationChannel(CARE_REMINDERS_CHANNEL_ID)

            when {
                channel == null -> NotificationChannelState.MISSING
                channel.importance == NotificationManager.IMPORTANCE_NONE -> {
                    NotificationChannelState.BLOCKED
                }
                else -> NotificationChannelState.ENABLED
            }
        }

        return NotificationSystemState(
            appNotificationsEnabled = appEnabled,
            careReminderChannelState = channelState
        )
    }
}

data class NotificationSystemState(
    val appNotificationsEnabled: Boolean,
    val careReminderChannelState: NotificationChannelState
) {
    val canDeliverCareReminders: Boolean
        get() = appNotificationsEnabled &&
            careReminderChannelState != NotificationChannelState.MISSING &&
            careReminderChannelState != NotificationChannelState.BLOCKED
}

enum class NotificationChannelState {
    NOT_REQUIRED,
    MISSING,
    BLOCKED,
    ENABLED
}

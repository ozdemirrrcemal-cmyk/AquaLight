package com.aqua.aqualight.data.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationDeliveryReadiness
import com.aqua.aqualight.application.notifications.NotificationPermissionPolicy

/** Android-backed delivery policy; permission prompting remains owned by Stage 6. */
class AndroidNotificationPermissionPolicy(
    context: Context
) : NotificationPermissionPolicy {

    private val appContext = context.applicationContext

    override fun ensureChannels() {
        NotificationChannelRegistry.ensureAll(appContext)
    }

    override fun evaluate(category: NotificationCategory): NotificationDeliveryReadiness {
        return NotificationDeliveryReadiness(
            runtimePermissionGranted = hasRuntimePermission(),
            appNotificationsEnabled = NotificationManagerCompat.from(appContext)
                .areNotificationsEnabled(),
            channelState = NotificationChannelRegistry.readState(appContext, category)
        )
    }

    override fun channelId(category: NotificationCategory): String {
        return NotificationChannelRegistry.channelId(category)
    }

    private fun hasRuntimePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
}

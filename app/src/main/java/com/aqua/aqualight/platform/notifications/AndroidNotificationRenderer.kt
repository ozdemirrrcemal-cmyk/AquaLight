package com.aqua.aqualight.platform.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.application.notifications.CareReminderNotification
import com.aqua.aqualight.application.notifications.DeviceAlertNotification
import com.aqua.aqualight.application.notifications.DeviceUpdateNotification
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationRenderer
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.care.reminder.CareReminderIdentity
import com.aqua.aqualight.data.notifications.NotificationChannelRegistry
import com.aqua.aqualight.data.notifications.NotificationIdentity
import com.aqua.aqualight.ui.main.MainActivity

/** The only Android platform adapter allowed to build, post, update or cancel notifications. */
class AndroidNotificationRenderer(
    context: Context
) : NotificationRenderer {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    @SuppressLint("MissingPermission")
    override fun renderCareReminder(notification: CareReminderNotification) {
        require(notification.taskId > 0L) { "taskId must be positive" }
        val category = NotificationCategory.CARE_REMINDERS
        val entityId = notification.taskId.toString()
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = CareReminderIdentity.contentData(
                notification.ownerUid,
                notification.taskId
            )
            putExtra(MainActivity.EXTRA_START_IN_APP, true)
            putExtra(MainActivity.EXTRA_OPEN_CARE_TASK_ID, notification.taskId)
            putExtra(MainActivity.EXTRA_OWNER_UID, notification.ownerUid)
        }

        val builder = baseBuilder(
            category = category,
            title = notification.title,
            message = notification.message,
            contentIntent = contentIntent(
                category,
                notification.ownerUid,
                entityId,
                launchIntent
            )
        )

        val typeUi = CareTaskTypeCatalog.get(
            CareTaskType.valueOf(notification.kind.name)
        )
        createLargeIconBitmap(typeUi.iconRes, typeUi.accentColor)
            ?.let(builder::setLargeIcon)

        notify(
            category = category,
            ownerUid = notification.ownerUid,
            entityId = entityId,
            id = CARE_NOTIFICATION_ID,
            notification = builder.build()
        )
    }

    @SuppressLint("MissingPermission")
    override fun renderDeviceAlert(notification: DeviceAlertNotification) {
        val category = NotificationCategory.DEVICE_ALERTS
        val entityId = requireDeviceUid(notification.deviceUid)
        val launchIntent = genericLaunchIntent(
            category,
            notification.ownerUid,
            entityId
        )
        val rendered = baseBuilder(
            category = category,
            title = notification.title,
            message = notification.message,
            contentIntent = contentIntent(
                category,
                notification.ownerUid,
                entityId,
                launchIntent
            )
        ).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        notify(
            category,
            notification.ownerUid,
            entityId,
            DEVICE_ALERT_NOTIFICATION_ID,
            rendered
        )
    }

    @SuppressLint("MissingPermission")
    override fun renderDeviceUpdate(notification: DeviceUpdateNotification) {
        val category = NotificationCategory.DEVICE_UPDATES
        val entityId = requireDeviceUid(notification.deviceUid)
        notification.progressPercent?.let { progress ->
            require(progress in 0..100) { "progressPercent must be 0..100" }
        }

        val builder = baseBuilder(
            category = category,
            title = notification.title,
            message = notification.message,
            contentIntent = contentIntent(
                category,
                notification.ownerUid,
                entityId,
                genericLaunchIntent(category, notification.ownerUid, entityId)
            )
        ).setOnlyAlertOnce(true)
            .setOngoing(notification.ongoing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        notification.progressPercent?.let { progress ->
            builder.setProgress(100, progress, false)
        }

        notify(
            category,
            notification.ownerUid,
            entityId,
            DEVICE_UPDATE_NOTIFICATION_ID,
            builder.build()
        )
    }

    override fun cancelCareReminder(ownerUid: String, taskId: Long) {
        require(taskId > 0L) { "taskId must be positive" }
        manager?.cancel(
            NotificationIdentity.tag(
                NotificationCategory.CARE_REMINDERS,
                ownerUid,
                taskId.toString()
            ),
            CARE_NOTIFICATION_ID
        )
    }

    override fun cancelOwner(ownerUid: String) {
        val prefix = NotificationIdentity.ownerTagPrefix(ownerUid)
        manager?.activeNotifications
            ?.filter { notification -> notification.tag?.startsWith(prefix) == true }
            ?.forEach { notification ->
                manager.cancel(notification.tag, notification.id)
            }
    }

    private fun baseBuilder(
        category: NotificationCategory,
        title: String,
        message: String,
        contentIntent: PendingIntent
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(
            appContext,
            NotificationChannelRegistry.channelId(category)
        )
            .setSmallIcon(R.drawable.ic_stat_aqualight_soft)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
    }

    private fun genericLaunchIntent(
        category: NotificationCategory,
        ownerUid: String,
        entityId: String
    ): Intent {
        return Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = NotificationIdentity.contentData(category, ownerUid, entityId)
            putExtra(MainActivity.EXTRA_START_IN_APP, true)
            putExtra(MainActivity.EXTRA_OWNER_UID, ownerUid)
        }
    }

    private fun contentIntent(
        category: NotificationCategory,
        ownerUid: String,
        entityId: String,
        intent: Intent
    ): PendingIntent {
        return PendingIntent.getActivity(
            appContext,
            NotificationIdentity.requestCode(category, ownerUid, entityId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notify(
        category: NotificationCategory,
        ownerUid: String,
        entityId: String,
        id: Int,
        notification: Notification
    ) {
        manager?.notify(
            NotificationIdentity.tag(category, ownerUid, entityId),
            id,
            notification
        )
    }

    private fun createLargeIconBitmap(
        @DrawableRes iconRes: Int,
        color: String
    ): Bitmap? {
        if (iconRes <= 0) return null

        val size = 48.dp()
        val iconSize = 25.dp()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = runCatching { Color.parseColor(color) }
                .getOrDefault(Color.parseColor(DEFAULT_LARGE_ICON_COLOR))
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val drawable = ContextCompat.getDrawable(appContext, iconRes) ?: return bitmap
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrapped, Color.WHITE)
        val left = (size - iconSize) / 2
        val top = (size - iconSize) / 2
        wrapped.setBounds(left, top, left + iconSize, top + iconSize)
        wrapped.draw(canvas)
        return bitmap
    }

    private fun requireDeviceUid(deviceUid: String): String {
        return deviceUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "deviceUid must not be blank" }
        }
    }

    private fun Int.dp(): Int =
        (this * appContext.resources.displayMetrics.density).toInt()

    companion object {
        private const val CARE_NOTIFICATION_ID = 1
        private const val DEVICE_ALERT_NOTIFICATION_ID = 2
        private const val DEVICE_UPDATE_NOTIFICATION_ID = 3
        private const val DEFAULT_LARGE_ICON_COLOR = "#2196F3"
    }
}

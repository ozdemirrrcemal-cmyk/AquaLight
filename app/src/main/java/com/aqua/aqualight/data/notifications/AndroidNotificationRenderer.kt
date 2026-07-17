package com.aqua.aqualight.data.notifications

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
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationRenderer
import com.aqua.aqualight.data.care.reminder.CareReminderIdentity
import com.aqua.aqualight.ui.main.MainActivity

/** The only component allowed to build, post, update or cancel visible notifications. */
class AndroidNotificationRenderer(
    context: Context,
    private val permissionPolicy: AndroidNotificationPermissionPolicy =
        AndroidNotificationPermissionPolicy(context)
) : NotificationRenderer {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    @SuppressLint("MissingPermission")
    fun renderCareReminder(
        ownerUid: String,
        taskId: Long,
        title: String,
        message: String,
        @DrawableRes largeIconRes: Int? = null,
        largeIconColor: String? = null
    ) {
        require(taskId > 0L) { "taskId must be positive" }
        val category = NotificationCategory.CARE_REMINDERS
        if (!permissionPolicy.evaluate(category).canDeliver) return

        val entityId = taskId.toString()
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = CareReminderIdentity.contentData(ownerUid, taskId)
            putExtra(MainActivity.EXTRA_START_IN_APP, true)
            putExtra(MainActivity.EXTRA_OPEN_CARE_TASK_ID, taskId)
            putExtra(MainActivity.EXTRA_OWNER_UID, ownerUid)
        }

        val builder = baseBuilder(
            category = category,
            title = title,
            message = message,
            contentIntent = contentIntent(category, ownerUid, entityId, launchIntent)
        )

        createLargeIconBitmap(largeIconRes, largeIconColor)?.let(builder::setLargeIcon)
        notify(category, ownerUid, entityId, CARE_NOTIFICATION_ID, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun renderDeviceAlert(
        ownerUid: String,
        deviceUid: String,
        title: String,
        message: String
    ) {
        val category = NotificationCategory.DEVICE_ALERTS
        if (!permissionPolicy.evaluate(category).canDeliver) return

        val entityId = deviceUid.trim().also {
            require(it.isNotBlank()) { "deviceUid must not be blank" }
        }
        val launchIntent = genericLaunchIntent(category, ownerUid, entityId)
        val notification = baseBuilder(
            category = category,
            title = title,
            message = message,
            contentIntent = contentIntent(category, ownerUid, entityId, launchIntent)
        ).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        notify(category, ownerUid, entityId, DEVICE_ALERT_NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    fun renderDeviceUpdate(
        ownerUid: String,
        deviceUid: String,
        title: String,
        message: String,
        progressPercent: Int? = null,
        ongoing: Boolean = false
    ) {
        val category = NotificationCategory.DEVICE_UPDATES
        if (!permissionPolicy.evaluate(category).canDeliver) return

        val entityId = deviceUid.trim().also {
            require(it.isNotBlank()) { "deviceUid must not be blank" }
        }
        progressPercent?.let { require(it in 0..100) { "progressPercent must be 0..100" } }

        val builder = baseBuilder(
            category = category,
            title = title,
            message = message,
            contentIntent = contentIntent(
                category,
                ownerUid,
                entityId,
                genericLaunchIntent(category, ownerUid, entityId)
            )
        ).setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (progressPercent != null) {
            builder.setProgress(100, progressPercent, false)
        }

        notify(category, ownerUid, entityId, DEVICE_UPDATE_NOTIFICATION_ID, builder.build())
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
            ?.forEach { notification -> manager.cancel(notification.tag, notification.id) }
    }

    private fun baseBuilder(
        category: NotificationCategory,
        title: String,
        message: String,
        contentIntent: PendingIntent
    ): NotificationCompat.Builder {
        permissionPolicy.ensureChannels()
        return NotificationCompat.Builder(appContext, permissionPolicy.channelId(category))
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
        manager?.notify(NotificationIdentity.tag(category, ownerUid, entityId), id, notification)
    }

    private fun createLargeIconBitmap(
        @DrawableRes iconRes: Int?,
        color: String?
    ): Bitmap? {
        if (iconRes == null || iconRes <= 0) return null

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

    private fun Int.dp(): Int =
        (this * appContext.resources.displayMetrics.density).toInt()

    companion object {
        private const val CARE_NOTIFICATION_ID = 1
        private const val DEVICE_ALERT_NOTIFICATION_ID = 2
        private const val DEVICE_UPDATE_NOTIFICATION_ID = 3
        private const val DEFAULT_LARGE_ICON_COLOR = "#2196F3"
    }
}

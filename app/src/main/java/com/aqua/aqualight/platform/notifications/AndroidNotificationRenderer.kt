package com.aqua.aqualight.platform.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.ColorRes
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
import java.util.concurrent.ConcurrentHashMap

/** The only Android platform adapter allowed to build, post, update or cancel notifications. */
class AndroidNotificationRenderer(
    context: Context
) : NotificationRenderer {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    /**
     * Binder delivery to NotificationManager is asynchronous. Keeping same-process
     * identities lets logout/account switching issue deterministic tagged cancels even
     * when Android has not exposed a just-posted notification through activeNotifications yet.
     * After process recreation, Android's active list remains the recovery authority.
     */
    private val postedNotifications = ConcurrentHashMap.newKeySet<PostedNotification>()

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
        createLargeIconBitmap(typeUi.iconRes, typeUi.accentColorRes)
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

        val pendingIntent = contentIntent(
            category = category,
            ownerUid = notification.ownerUid,
            entityId = entityId,
            intent = deviceUpdateLaunchIntent(
                category = category,
                ownerUid = notification.ownerUid,
                deviceUid = entityId
            )
        )
        val builder = baseBuilder(
            category = category,
            title = notification.title,
            message = notification.message,
            contentIntent = pendingIntent
        ).setOnlyAlertOnce(true)
            .setOngoing(notification.ongoing)
            .setCategory(
                if (notification.progressPercent != null || notification.ongoing) {
                    NotificationCompat.CATEGORY_PROGRESS
                } else {
                    NotificationCompat.CATEGORY_RECOMMENDATION
                }
            )

        notification.progressPercent?.let { progress ->
            builder.setProgress(100, progress, false)
        }
        notification.actionLabel
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { actionLabel ->
                builder.addAction(0, actionLabel, pendingIntent)
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
        cancelNotification(
            category = NotificationCategory.CARE_REMINDERS,
            ownerUid = ownerUid,
            entityId = taskId.toString(),
            id = CARE_NOTIFICATION_ID
        )
    }

    fun cancelDeviceUpdate(ownerUid: String, deviceUid: String) {
        cancelNotification(
            category = NotificationCategory.DEVICE_UPDATES,
            ownerUid = ownerUid,
            entityId = requireDeviceUid(deviceUid),
            id = DEVICE_UPDATE_NOTIFICATION_ID
        )
    }

    fun isDeviceUpdateNotificationActive(ownerUid: String, deviceUid: String): Boolean {
        val normalizedOwner = requireOwnerUid(ownerUid)
        val entityId = requireDeviceUid(deviceUid)
        val tag = NotificationIdentity.tag(
            NotificationCategory.DEVICE_UPDATES,
            normalizedOwner,
            entityId
        )
        val identity = PostedNotification(
            ownerUid = normalizedOwner,
            tag = tag,
            id = DEVICE_UPDATE_NOTIFICATION_ID
        )
        if (identity in postedNotifications) {
            return true
        }
        return activeDeviceUpdateNotification(tag) != null
    }

    fun isDeviceUpdateOperationNotificationActive(
        ownerUid: String,
        deviceUid: String
    ): Boolean {
        val tag = NotificationIdentity.tag(
            NotificationCategory.DEVICE_UPDATES,
            requireOwnerUid(ownerUid),
            requireDeviceUid(deviceUid)
        )
        val active = activeDeviceUpdateNotification(tag)?.notification ?: return false
        return active.flags and Notification.FLAG_ONGOING_EVENT != 0
    }

    override fun cancelOwner(ownerUid: String) {
        val normalizedOwner = requireOwnerUid(ownerUid)
        val notificationManager = manager ?: return

        postedNotifications
            .filter { posted -> posted.ownerUid == normalizedOwner }
            .forEach { posted ->
                postedNotifications.remove(posted)
                notificationManager.cancel(posted.tag, posted.id)
            }

        val prefix = NotificationIdentity.ownerTagPrefix(normalizedOwner)
        notificationManager.activeNotifications
            .filter { notification -> notification.tag?.startsWith(prefix) == true }
            .forEach { notification ->
                notificationManager.cancel(notification.tag, notification.id)
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

    private fun deviceUpdateLaunchIntent(
        category: NotificationCategory,
        ownerUid: String,
        deviceUid: String
    ): Intent {
        return genericLaunchIntent(category, ownerUid, deviceUid).apply {
            putExtra(MainActivity.EXTRA_OPEN_DEVICE_FIRMWARE_UID, deviceUid)
        }
    }

    private fun contentIntent(
        category: NotificationCategory,
        ownerUid: String,
        entityId: String,
        intent: Intent
    ): PendingIntent {
        intent.setClass(appContext, MainActivity::class.java)
        intent.setPackage(appContext.packageName)
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
        val normalizedOwner = requireOwnerUid(ownerUid)
        val notificationManager = manager ?: return
        val tag = NotificationIdentity.tag(category, normalizedOwner, entityId)
        postedNotifications += PostedNotification(normalizedOwner, tag, id)
        notificationManager.notify(tag, id, notification)
    }

    private fun activeDeviceUpdateNotification(tag: String) =
        manager?.activeNotifications?.firstOrNull { notification ->
            notification.tag == tag && notification.id == DEVICE_UPDATE_NOTIFICATION_ID
        }

    private fun cancelNotification(
        category: NotificationCategory,
        ownerUid: String,
        entityId: String,
        id: Int
    ) {
        val normalizedOwner = requireOwnerUid(ownerUid)
        val tag = NotificationIdentity.tag(category, normalizedOwner, entityId)
        postedNotifications.remove(PostedNotification(normalizedOwner, tag, id))
        manager?.cancel(tag, id)
    }

    private fun createLargeIconBitmap(
        @DrawableRes iconRes: Int,
        @ColorRes colorRes: Int
    ): Bitmap? {
        if (iconRes == 0) return null

        val size = appContext.resources.getDimensionPixelOffset(R.dimen.aqua_size_48)
        val iconSize = appContext.resources.getDimensionPixelOffset(R.dimen.aqua_size_25)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(appContext, colorRes)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val drawable = ContextCompat.getDrawable(appContext, iconRes) ?: return bitmap
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(
            wrapped,
            ContextCompat.getColor(appContext, R.color.aqua_content_on_dark)
        )
        val left = (size - iconSize) / 2
        val top = (size - iconSize) / 2
        wrapped.setBounds(left, top, left + iconSize, top + iconSize)
        wrapped.draw(canvas)
        return bitmap
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return ownerUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
    }

    private fun requireDeviceUid(deviceUid: String): String {
        return deviceUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "deviceUid must not be blank" }
        }
    }

    private data class PostedNotification(
        val ownerUid: String,
        val tag: String,
        val id: Int
    )

    companion object {
        private const val CARE_NOTIFICATION_ID = 1
        private const val DEVICE_ALERT_NOTIFICATION_ID = 2
        private const val DEVICE_UPDATE_NOTIFICATION_ID = 3
    }
}

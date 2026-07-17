package com.aqua.aqualight.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.data.notifications.NotificationChannelRegistry
import com.aqua.aqualight.data.notifications.NotificationSystemState
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.ui.main.MainActivity

/** Notification rendering only; permission UI remains owned by the Stage 6 coordinator. */
object NotificationHelper {

  private const val NOTIFICATION_ID_MOD = 10000

  fun createNotificationChannel(context: Context) {
    NotificationChannelRegistry.ensureChannels(context)
  }

  fun hasSystemPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun notificationSystemState(context: Context): NotificationSystemState {
    return NotificationChannelRegistry.readState(context)
  }

  fun areSystemNotificationsEnabled(context: Context): Boolean {
    return notificationSystemState(context).appNotificationsEnabled
  }

  @SuppressLint("MissingPermission")
  fun showLocalNotification(
    context: Context,
    title: String,
    message: String
  ) {
    if (!canPostCareNotification(context)) {
      return
    }

    val notificationId = (
      System.currentTimeMillis() % NOTIFICATION_ID_MOD
    ).toInt()

    showNotificationInternal(
      context = context,
      notificationId = notificationId,
      requestCode = notificationId,
      title = title,
      message = message
    )
  }

  @SuppressLint("MissingPermission")
  fun showCareTaskReminderNotification(
    context: Context,
    taskId: Long,
    title: String,
    message: String,
    largeIconRes: Int? = null,
    largeIconColor: String? = null,
    ownerUid: String
  ) {
    val normalizedOwnerUid = requireOwnerUid(ownerUid)
    require(taskId > 0L) {
      "taskId must be positive"
    }

    if (!canPostCareNotification(context)) {
      return
    }

    val notificationId = getTaskNotificationId(
      taskId = taskId,
      ownerUid = normalizedOwnerUid
    )

    showNotificationInternal(
      context = context,
      notificationId = notificationId,
      requestCode = notificationId,
      title = title,
      message = message,
      taskId = taskId,
      ownerUid = normalizedOwnerUid,
      largeIconRes = largeIconRes,
      largeIconColor = largeIconColor
    )
  }

  fun cancelCareTaskNotification(
    context: Context,
    taskId: Long,
    ownerUid: String
  ) {
    val normalizedOwnerUid = requireOwnerUid(ownerUid)
    require(taskId > 0L) {
      "taskId must be positive"
    }

    NotificationManagerCompat.from(context).cancel(
      getTaskNotificationId(
        taskId = taskId,
        ownerUid = normalizedOwnerUid
      )
    )
  }

  fun cancelAllAppNotifications(context: Context) {
    NotificationManagerCompat.from(
      context.applicationContext
    ).cancelAll()
  }

  private fun canPostCareNotification(context: Context): Boolean {
    if (!hasSystemPermission(context)) {
      return false
    }

    NotificationChannelRegistry.ensureChannels(context)
    return NotificationChannelRegistry.readState(context)
      .canDeliverCareReminders
  }

  @SuppressLint("MissingPermission")
  private fun showNotificationInternal(
    context: Context,
    notificationId: Int,
    requestCode: Int,
    title: String,
    message: String,
    taskId: Long? = null,
    ownerUid: String? = null,
    largeIconRes: Int? = null,
    largeIconColor: String? = null
  ) {
    val launchIntent = if (taskId != null && taskId > 0L) {
      val normalizedOwnerUid = requireOwnerUid(ownerUid.orEmpty())

      Intent(
        context,
        MainActivity::class.java
      ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP

        putExtra(MainActivity.EXTRA_START_IN_APP, true)
        putExtra(MainActivity.EXTRA_OPEN_CARE_TASK_ID, taskId)
        putExtra(MainActivity.EXTRA_OWNER_UID, normalizedOwnerUid)
      }
    } else {
      context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }

    val pendingIntent = launchIntent?.let { intent ->
      PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
          PendingIntent.FLAG_IMMUTABLE
      )
    }

    val builder = NotificationCompat.Builder(
      context,
      NotificationChannelRegistry.CARE_REMINDERS_CHANNEL_ID
    )
      .setSmallIcon(R.drawable.ic_stat_aqualight_soft)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(
        NotificationCompat.BigTextStyle().bigText(message)
      )
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setAutoCancel(true)

    val largeIconBitmap = createLargeIconBitmap(
      context = context,
      iconRes = largeIconRes,
      color = largeIconColor
    )

    if (largeIconBitmap != null) {
      builder.setLargeIcon(largeIconBitmap)
    }

    if (pendingIntent != null) {
      builder.setContentIntent(pendingIntent)
    }

    NotificationManagerCompat.from(context).notify(
      notificationId,
      builder.build()
    )
  }

  private fun createLargeIconBitmap(
    context: Context,
    iconRes: Int?,
    color: String?
  ): Bitmap? {
    if (iconRes == null || iconRes <= 0) {
      return null
    }

    val size = 48.dp(context)
    val iconSize = 25.dp(context)

    val bitmap = Bitmap.createBitmap(
      size,
      size,
      Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    val backgroundColor = parseColorOrDefault(
      color = color,
      fallback = "#2196F3"
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      this.color = backgroundColor
    }

    canvas.drawCircle(
      size / 2f,
      size / 2f,
      size / 2f,
      paint
    )

    val drawable = ContextCompat.getDrawable(
      context,
      iconRes
    ) ?: return bitmap

    val wrappedDrawable = DrawableCompat.wrap(
      drawable.mutate()
    )
    DrawableCompat.setTint(wrappedDrawable, Color.WHITE)

    val left = (size - iconSize) / 2
    val top = (size - iconSize) / 2
    wrappedDrawable.setBounds(
      left,
      top,
      left + iconSize,
      top + iconSize
    )
    wrappedDrawable.draw(canvas)

    return bitmap
  }

  private fun parseColorOrDefault(
    color: String?,
    fallback: String
  ): Int {
    return runCatching {
      Color.parseColor(color)
    }.getOrDefault(
      Color.parseColor(fallback)
    )
  }

  private fun Int.dp(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
  }

  private fun getTaskNotificationId(
    taskId: Long,
    ownerUid: String
  ): Int {
    return UserDataScope.notificationRequestCode(
      taskId = taskId,
      ownerUid = ownerUid
    )
  }

  private fun requireOwnerUid(ownerUid: String): String {
    return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
      require(normalized.isNotBlank()) {
        "ownerUid must not be blank"
      }
    }
  }
}

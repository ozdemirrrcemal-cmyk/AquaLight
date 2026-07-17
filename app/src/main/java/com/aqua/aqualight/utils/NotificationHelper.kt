package com.aqua.aqualight.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.ui.main.MainActivity

object NotificationHelper {

  const val REQUEST_CODE_NOTIFICATIONS = 1001

  private const val CHANNEL_ID = "aqualight_channel"
  private const val NOTIFICATION_ID_MOD = 10000

  private var channelCreated = false

  fun createNotificationChannel(
    context: Context
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    if (channelCreated) {
      return
    }

    val channel = NotificationChannel(
      CHANNEL_ID,
      context.getString(R.string.notification_channel_name),
      NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
      description = context.getString(
        R.string.notification_channel_description
      )
    }

    val manager = context.getSystemService(
      NotificationManager::class.java
    )

    manager?.createNotificationChannel(channel)

    channelCreated = true
  }

  fun hasSystemPermission(
    context: Context
  ): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun areSystemNotificationsEnabled(
    context: Context
  ): Boolean {
    return NotificationManagerCompat.from(context)
      .areNotificationsEnabled()
  }

  fun openNotificationSettings(
    context: Context
  ) {
    val intent = Intent(
      Settings.ACTION_APP_NOTIFICATION_SETTINGS
    ).apply {
      putExtra(
        Settings.EXTRA_APP_PACKAGE,
        context.packageName
      )
      putExtra(
        "app_package",
        context.packageName
      )
      putExtra(
        "app_uid",
        context.applicationInfo.uid
      )
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(intent)
  }

  fun openAppSettings(
    context: Context
  ) {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts(
        "package",
        context.packageName,
        null
      )
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(intent)
  }

  fun requestPermission(
    activity: Activity
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        REQUEST_CODE_NOTIFICATIONS
      )
    }
  }

  @SuppressLint("MissingPermission")
  fun showLocalNotification(
    context: Context,
    title: String,
    message: String
  ) {
    if (!hasSystemPermission(context)) {
      return
    }

    if (!areSystemNotificationsEnabled(context)) {
      return
    }

    createNotificationChannel(context)

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
    ownerUid: String = UserDataScope.currentUid()
  ) {
    if (!hasSystemPermission(context)) {
      return
    }

    if (!areSystemNotificationsEnabled(context)) {
      return
    }

    createNotificationChannel(context)

    val notificationId = getTaskNotificationId(
      taskId = taskId,
      ownerUid = ownerUid
    )

    showNotificationInternal(
      context = context,
      notificationId = notificationId,
      requestCode = notificationId,
      title = title,
      message = message,
      taskId = taskId,
      ownerUid = ownerUid,
      largeIconRes = largeIconRes,
      largeIconColor = largeIconColor
    )
  }

  fun cancelCareTaskNotification(
    context: Context,
    taskId: Long,
    ownerUid: String = UserDataScope.currentUid()
  ) {
    val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)
    require(taskId > 0L) {
      "taskId must be positive"
    }
    require(normalizedOwnerUid.isNotBlank()) {
      "ownerUid must not be blank"
    }

    NotificationManagerCompat.from(context).cancel(
      getTaskNotificationId(
        taskId = taskId,
        ownerUid = normalizedOwnerUid
      )
    )
  }

  fun cancelAllAppNotifications(
    context: Context
  ) {
    NotificationManagerCompat.from(
      context.applicationContext
    ).cancelAll()
  }

  @SuppressLint("MissingPermission")
  private fun showNotificationInternal(
    context: Context,
    notificationId: Int,
    requestCode: Int,
    title: String,
    message: String,
    taskId: Long? = null,
    ownerUid: String = UserDataScope.currentUid(),
    largeIconRes: Int? = null,
    largeIconColor: String? = null
  ) {
    val launchIntent = if (taskId != null && taskId > 0L) {
      val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)
      require(normalizedOwnerUid.isNotBlank()) {
        "ownerUid must not be blank"
      }

      Intent(
        context,
        MainActivity::class.java
      ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP

        putExtra(
          MainActivity.EXTRA_START_IN_APP,
          true
        )

        putExtra(
          MainActivity.EXTRA_OPEN_CARE_TASK_ID,
          taskId
        )

        putExtra(
          MainActivity.EXTRA_OWNER_UID,
          normalizedOwnerUid
        )
      }
    } else {
      context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }

    val pendingIntent = launchIntent?.let {
      intent ->
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
      CHANNEL_ID
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

    val paint = Paint(
      Paint.ANTI_ALIAS_FLAG
    ).apply {
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

    DrawableCompat.setTint(
      wrappedDrawable,
      Color.WHITE
    )

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

  private fun Int.dp(
    context: Context
  ): Int {
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
}

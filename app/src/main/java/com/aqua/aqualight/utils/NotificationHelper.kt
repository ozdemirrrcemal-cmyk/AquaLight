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
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aqua.aqualight.ui.main.MainActivity
import com.aqua.aqualight.R

object NotificationHelper {

  const val REQUEST_CODE_NOTIFICATIONS = 1001

  private const val CHANNEL_ID = "aqualight_channel"
  private const val NOTIFICATION_ID_MOD = 10000

  private var channelCreated = false

  fun createNotificationChannel(
    context: Context
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (channelCreated) return

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
    if (!hasSystemPermission(context)) return
    if (!areSystemNotificationsEnabled(context)) return

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
    message: String
  ) {
    if (!hasSystemPermission(context)) return
    if (!areSystemNotificationsEnabled(context)) return

    createNotificationChannel(context)

    val notificationId = getTaskNotificationId(taskId)

    showNotificationInternal(
      context = context,
      notificationId = notificationId,
      requestCode = notificationId,
      title = title,
      message = message,
      taskId = taskId
    )
  }

  fun cancelCareTaskNotification(
    context: Context,
    taskId: Long
  ) {
    NotificationManagerCompat.from(context).cancel(
      getTaskNotificationId(taskId)
    )
  }

  @SuppressLint("MissingPermission")
  private fun showNotificationInternal(
    context: Context,
    notificationId: Int,
    requestCode: Int,
    title: String,
    message: String,
    taskId: Long? = null
  ) {
    val launchIntent = if (taskId != null && taskId > 0L) {
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

    if (pendingIntent != null) {
      builder.setContentIntent(pendingIntent)
    }

    NotificationManagerCompat.from(context).notify(
      notificationId,
      builder.build()
    )
  }

  private fun getTaskNotificationId(
    taskId: Long
  ): Int {
    val value = (taskId % Int.MAX_VALUE).toInt()

    return if (value == 0) {
      1
    } else {
      value
    }
  }
}
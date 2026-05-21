package com.aqua.aqualight.ui.tabs.maintenance.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTask
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskStatus
import com.aqua.aqualight.utils.NotificationHelper
import java.util.concurrent.TimeUnit

object CareTaskReminderScheduler {

  const val ACTION_CARE_TASK_REMINDER =
  "com.aqua.aqualight.action.CARE_TASK_REMINDER"

  const val EXTRA_TASK_ID = "extra_task_id"

  fun schedule(
    context: Context,
    task: CareTask
  ) {
    if (
      task.status != CareTaskStatus.PENDING ||
      !task.reminderEnabled
    ) {
      cancel(
        context = context,
        taskId = task.id
      )
      return
    }

    val triggerAtMillis = maxOf(
      task.dueAtMillis,
      System.currentTimeMillis() + 5_000L
    )

    scheduleAt(
      context = context,
      taskId = task.id,
      triggerAtMillis = triggerAtMillis
    )
  }

  fun scheduleMissedReminder(
    context: Context,
    task: CareTask
  ) {
    if (
      task.status != CareTaskStatus.PENDING ||
      !task.reminderEnabled ||
      !task.missedReminderEnabled
    ) {
      return
    }

    val intervalDays = task.missedReminderDays.coerceAtLeast(1)

    val triggerAtMillis = System.currentTimeMillis() +
    TimeUnit.DAYS.toMillis(intervalDays.toLong())

    scheduleAt(
      context = context,
      taskId = task.id,
      triggerAtMillis = triggerAtMillis
    )
  }

  fun cancel(
    context: Context,
    taskId: Long
  ) {
    val alarmManager = context.getSystemService(
      Context.ALARM_SERVICE
    ) as AlarmManager

    val pendingIntent = createPendingIntent(
      context = context,
      taskId = taskId
    )

    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()

    NotificationHelper.cancelCareTaskNotification(
      context = context,
      taskId = taskId
    )
  }

  private fun scheduleAt(
    context: Context,
    taskId: Long,
    triggerAtMillis: Long
  ) {
    val alarmManager = context.getSystemService(
      Context.ALARM_SERVICE
    ) as AlarmManager

    val pendingIntent = createPendingIntent(
      context = context,
      taskId = taskId
    )

    alarmManager.cancel(pendingIntent)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        pendingIntent
      )
    } else {
      alarmManager.set(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        pendingIntent
      )
    }
  }

  private fun createPendingIntent(
    context: Context,
    taskId: Long
  ): PendingIntent {
    val intent = Intent(
      context,
      CareTaskReminderReceiver::class.java
    ).apply {
      action = ACTION_CARE_TASK_REMINDER
      putExtra(
        EXTRA_TASK_ID,
        taskId
      )
    }

    return PendingIntent.getBroadcast(
      context,
      getRequestCode(taskId),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or
      PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun getRequestCode(
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
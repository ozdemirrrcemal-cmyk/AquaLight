package com.aqua.aqualight.data.care.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.utils.NotificationHelper
import java.util.concurrent.TimeUnit

object CareTaskReminderScheduler {

  const val ACTION_CARE_TASK_REMINDER =
  "com.aqua.aqualight.action.CARE_TASK_REMINDER"

  const val EXTRA_TASK_ID = "extra_task_id"
  const val EXTRA_OWNER_UID = "extra_owner_uid"

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
        taskId = task.id,
        ownerUid = task.ownerUid
      )
      return
    }

    val now = System.currentTimeMillis()

    if (task.dueAtMillis <= now) {
      return
    }

    scheduleAt(
      context = context,
      taskId = task.id,
      ownerUid = task.ownerUid,
      triggerAtMillis = task.dueAtMillis
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
      ownerUid = task.ownerUid,
      triggerAtMillis = triggerAtMillis
    )
  }

  fun cancel(
    context: Context,
    taskId: Long,
    ownerUid: String = UserDataScope.currentUid()
  ) {
    val alarmManager = context.getSystemService(
      Context.ALARM_SERVICE
    ) as AlarmManager

    val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

    listOf(
      UserDataScope.LEGACY_OWNER_UID,
      normalizedOwnerUid
    )
      .distinct()
      .forEach { candidateOwnerUid ->
        val pendingIntent = createPendingIntent(
          context = context,
          taskId = taskId,
          ownerUid = candidateOwnerUid
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        NotificationHelper.cancelCareTaskNotification(
          context = context,
          taskId = taskId,
          ownerUid = candidateOwnerUid
        )
      }
  }

  private fun scheduleAt(
    context: Context,
    taskId: Long,
    ownerUid: String,
    triggerAtMillis: Long
  ) {
    val alarmManager = context.getSystemService(
      Context.ALARM_SERVICE
    ) as AlarmManager

    val pendingIntent = createPendingIntent(
      context = context,
      taskId = taskId,
      ownerUid = ownerUid
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
    taskId: Long,
    ownerUid: String
  ): PendingIntent {
    val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

    val intent = Intent(
      context,
      CareTaskReminderReceiver::class.java
    ).apply {
      action = ACTION_CARE_TASK_REMINDER
      putExtra(
        EXTRA_TASK_ID,
        taskId
      )
      putExtra(
        EXTRA_OWNER_UID,
        normalizedOwnerUid
      )
    }

    return PendingIntent.getBroadcast(
      context,
      getRequestCode(
        taskId = taskId,
        ownerUid = normalizedOwnerUid
      ),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or
      PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun getRequestCode(
    taskId: Long,
    ownerUid: String
  ): Int {
    return UserDataScope.notificationRequestCode(
      taskId = taskId,
      ownerUid = ownerUid
    )
  }
}

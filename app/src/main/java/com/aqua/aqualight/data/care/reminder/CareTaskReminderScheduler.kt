package com.aqua.aqualight.data.care.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.user.UserDataScope

/** Low-level AlarmManager backend. Visible delivery belongs to the central platform adapter. */
object CareTaskReminderScheduler {

  const val ACTION_CARE_TASK_REMINDER =
    "com.aqua.aqualight.action.CARE_TASK_REMINDER"

  const val EXTRA_TASK_ID = "extra_task_id"
  const val EXTRA_OWNER_UID = "extra_owner_uid"
  const val EXTRA_OCCURRENCE = "extra_occurrence"

  /**
   * Replaces the task alarm with the deterministic next occurrence.
   *
   * Returns true only when a future alarm was actually installed. A false result
   * means the persisted task currently has no schedulable due or missed occurrence.
   *
   * This intentionally uses an inexact alarm. Aquarium care reminders are
   * user-facing but do not require alarm-clock precision, so AquaLight avoids
   * exact-alarm special access and its additional policy surface.
   */
  fun schedule(
    context: Context,
    task: CareTask,
    nowMillis: Long = System.currentTimeMillis()
  ): Boolean {
    val ownerUid = requireOwnerUid(task.ownerUid)
    require(task.id > 0L) {
      "taskId must be positive"
    }

    cancelAlarm(
      context = context,
      taskId = task.id,
      ownerUid = ownerUid
    )

    val plan = CareReminderSchedulePolicy.plan(
      task = task.copy(ownerUid = ownerUid),
      nowMillis = nowMillis
    ) ?: return false

    scheduleAt(
      context = context,
      taskId = task.id,
      ownerUid = ownerUid,
      occurrence = plan.occurrence,
      triggerAtMillis = plan.triggerAtMillis
    )
    return true
  }

  fun cancel(
    context: Context,
    taskId: Long,
    ownerUid: String
  ) {
    val normalizedOwnerUid = requireOwnerUid(ownerUid)
    require(taskId > 0L) {
      "taskId must be positive"
    }

    cancelAlarm(
      context = context,
      taskId = taskId,
      ownerUid = normalizedOwnerUid
    )
  }

  private fun cancelAlarm(
    context: Context,
    taskId: Long,
    ownerUid: String
  ) {
    val alarmManager = context.getSystemService(
      Context.ALARM_SERVICE
    ) as AlarmManager

    val pendingIntent = createPendingIntent(
      context = context,
      taskId = taskId,
      ownerUid = ownerUid,
      occurrence = null
    )

    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
  }

  private fun scheduleAt(
    context: Context,
    taskId: Long,
    ownerUid: String,
    occurrence: CareReminderOccurrence,
    triggerAtMillis: Long
  ) {
    require(triggerAtMillis > 0L) {
      "triggerAtMillis must be positive"
    }

    val alarmManager = context.getSystemService(
      Context.ALARM_SERVICE
    ) as AlarmManager

    val pendingIntent = createPendingIntent(
      context = context,
      taskId = taskId,
      ownerUid = ownerUid,
      occurrence = occurrence
    )

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
    ownerUid: String,
    occurrence: CareReminderOccurrence?
  ): PendingIntent {
    val normalizedOwnerUid = requireOwnerUid(ownerUid)
    require(taskId > 0L) {
      "taskId must be positive"
    }

    val intent = Intent(
      context,
      CareTaskReminderReceiver::class.java
    ).apply {
      action = ACTION_CARE_TASK_REMINDER
      data = CareReminderIdentity.alarmData(
        ownerUid = normalizedOwnerUid,
        taskId = taskId
      )
      putExtra(EXTRA_TASK_ID, taskId)
      putExtra(EXTRA_OWNER_UID, normalizedOwnerUid)
      occurrence?.let { value ->
        putExtra(EXTRA_OCCURRENCE, value.name)
      }
    }

    return PendingIntent.getBroadcast(
      context,
      UserDataScope.notificationRequestCode(
        taskId = taskId,
        ownerUid = normalizedOwnerUid
      ),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or
        PendingIntent.FLAG_IMMUTABLE
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

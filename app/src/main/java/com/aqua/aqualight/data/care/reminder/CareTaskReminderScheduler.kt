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
   * Returns true only when a future alarm was actually installed. User-selected
   * reminder times use an exact idle alarm whenever Android permits it. Android 12+
   * devices without Alarms & reminders access receive an inexact fallback until the
   * user grants the centrally requested special access.
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

    val exactAccessGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
      alarmManager.canScheduleExactAlarms()

    if (shouldUseExactAlarm(Build.VERSION.SDK_INT, exactAccessGranted)) {
      try {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
        return
      } catch (_: SecurityException) {
        // Access can be revoked between the grant check and platform call. Preserve
        // the reminder with an inexact alarm; the grant broadcast will reconcile it.
      }
    }

    alarmManager.setAndAllowWhileIdle(
      AlarmManager.RTC_WAKEUP,
      triggerAtMillis,
      pendingIntent
    )
  }

  internal fun shouldUseExactAlarm(
    sdkInt: Int,
    exactAccessGranted: Boolean
  ): Boolean {
    return sdkInt < Build.VERSION_CODES.S || exactAccessGranted
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

    val intent = Intent()
    intent.setClass(context, CareTaskReminderReceiver::class.java)
    intent.setPackage(context.packageName)
    intent.action = ACTION_CARE_TASK_REMINDER
    intent.data = CareReminderIdentity.alarmData(
      ownerUid = normalizedOwnerUid,
      taskId = taskId
    )
    intent.putExtra(EXTRA_TASK_ID, taskId)
    intent.putExtra(EXTRA_OWNER_UID, normalizedOwnerUid)
    occurrence?.let { value ->
      intent.putExtra(EXTRA_OCCURRENCE, value.name)
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

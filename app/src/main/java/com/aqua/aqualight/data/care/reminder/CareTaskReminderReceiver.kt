package com.aqua.aqualight.data.care.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Lightweight alarm boundary; durable validation and delivery run in WorkManager. */
class CareTaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != CareTaskReminderScheduler.ACTION_CARE_TASK_REMINDER) {
            return
        }

        val taskId = intent.getLongExtra(
            CareTaskReminderScheduler.EXTRA_TASK_ID,
            -1L
        )
        val ownerUid = intent.getStringExtra(
            CareTaskReminderScheduler.EXTRA_OWNER_UID
        ).orEmpty().trim()
        val occurrence = intent.getStringExtra(
            CareTaskReminderScheduler.EXTRA_OCCURRENCE
        )?.let { raw ->
            runCatching { CareReminderOccurrence.valueOf(raw) }.getOrNull()
        }

        if (taskId <= 0L || ownerUid.isBlank() || occurrence == null) {
            return
        }

        CareReminderDeliveryWorker.enqueue(
            context = context.applicationContext,
            taskId = taskId,
            ownerUid = ownerUid,
            occurrence = occurrence
        )
    }
}

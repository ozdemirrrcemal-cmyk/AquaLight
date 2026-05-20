package com.aqua.aqualight.ui.tabs.maintenance.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.data.CareTaskDataStoreManager
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskStatus
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskTypeCatalog
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

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

    if (taskId <= 0L) {
      return
    }

    val pendingResult = goAsync()

    CoroutineScope(Dispatchers.IO).launch {
      try {
        val manager = CareTaskDataStoreManager.create(context)

        val task = manager.taskFlow(taskId).firstOrNull()
        ?: return@launch

        if (
          task.status != CareTaskStatus.PENDING ||
          !task.reminderEnabled
        ) {
          CareTaskReminderScheduler.cancel(
            context = context,
            taskId = task.id
          )
          return@launch
        }

        val typeUi = CareTaskTypeCatalog.get(task.type)

        val baseTitle = task.title.ifBlank {
          typeUi.title
        }

        val title = if (
          task.waterChangePercent != null &&
          task.waterChangePercent > 0 &&
          !baseTitle.contains("%")
        ) {
          "$baseTitle (${task.waterChangePercent}%)"
        } else {
          baseTitle
        }

        val message = when {
          task.note.isNotBlank() -> {
            task.note
          }

          task.description.isNotBlank() -> {
            task.description
          }

          typeUi.defaultDescription.isNotBlank() -> {
            typeUi.defaultDescription
          } else -> {
            "This care task is due now."
          }
        }

        NotificationHelper.showCareTaskReminderNotification(
          context = context,
          taskId = task.id,
          title = title,
          message = message
        )

        if (task.missedReminderEnabled) {
          CareTaskReminderScheduler.scheduleMissedReminder(
            context = context,
            task = task
          )
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
package com.aqua.aqualight.ui.tabs.maintenance.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.data.CareTaskDataStoreManager
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
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
        val appContext = context.applicationContext

        val userPrefs = UserPreferencesManager.create(
          appContext
        )

        val appNotificationsEnabled = userPrefs.notificationsEnabled
          .firstOrNull() ?: false

        if (!appNotificationsEnabled) {
          return@launch
        }

        val careTaskManager = CareTaskDataStoreManager.create(
          appContext
        )

        val tankManager = AquariumTankDataStoreManager(
          appContext
        )

        val task = careTaskManager.taskFlow(taskId).firstOrNull()
          ?: return@launch

        if (
          task.status != CareTaskStatus.PENDING ||
          !task.reminderEnabled
        ) {
          CareTaskReminderScheduler.cancel(
            context = appContext,
            taskId = task.id
          )
          return@launch
        }

        val tanks = tankManager.tanksFlow.firstOrNull()
          .orEmpty()

        val aquariumName = tanks.firstOrNull { tank ->
          tank.id == task.tankId
        }?.name.orEmpty()

        val typeUi = CareTaskTypeCatalog.get(
          task.type
        )

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

        val bodyText = when {
          task.note.isNotBlank() -> {
            task.note
          }

          task.description.isNotBlank() -> {
            task.description
          }

          typeUi.defaultDescription.isNotBlank() -> {
            typeUi.defaultDescription
          }

          else -> {
            "This care task is due now."
          }
        }

        val message = if (aquariumName.isNotBlank()) {
          "$aquariumName\n$bodyText"
        } else {
          bodyText
        }

        NotificationHelper.showCareTaskReminderNotification(
          context = appContext,
          taskId = task.id,
          title = title,
          message = message
        )

        if (task.missedReminderEnabled) {
          CareTaskReminderScheduler.scheduleMissedReminder(
            context = appContext,
            task = task
          )
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
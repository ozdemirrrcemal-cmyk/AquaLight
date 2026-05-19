package com.aqua.aqualight.ui.tabs.maintenance.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.data.CareTaskDataStoreManager
import com.aqua.aqualight.ui.tabs.maintenance.smartcare.SmartCareDailyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class CareTaskBootReceiver : BroadcastReceiver() {

  override fun onReceive(
    context: Context,
    intent: Intent
  ) {
    val action = intent.action

    if (
      action != Intent.ACTION_BOOT_COMPLETED &&
      action != Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      return
    }

    val pendingResult = goAsync()

    CoroutineScope(Dispatchers.IO).launch {
      try {
        // 🧠 Restore SmartCare daily worker after boot/update
        SmartCareDailyWorker.schedule(context)

        // 🔔 Restore pending task reminders
        val manager = CareTaskDataStoreManager.create(context)

        val pendingTasks = manager.pendingTasksFlow
          .firstOrNull()
          .orEmpty()

        pendingTasks.forEach { task ->
          CareTaskReminderScheduler.schedule(
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
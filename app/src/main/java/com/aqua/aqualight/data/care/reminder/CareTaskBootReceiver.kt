package com.aqua.aqualight.data.care.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.auth.AuthSessionManager
import com.aqua.aqualight.data.auth.SessionBoundServiceManager
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
        val sessionManager = AuthSessionManager.create(context)

        if (!sessionManager.isAuthenticated()) {
          return@launch
        }

        // 🧠 Restore session-bound services after boot/update
        SessionBoundServiceManager.start(context)

        // 🔔 Restore pending task reminders for the active session
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
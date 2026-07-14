package com.aqua.aqualight.data.care.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.auth.SessionBoundServiceManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ownerProvider = FirebaseAuthenticatedOwnerProvider.create(
                    appContext
                )
                val ownerUid = ownerProvider.currentOwnerUid()
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: return@launch

                // Background maintenance scheduling is owner-scoped and does not
                // open DevicesRepositoryProvider, UDP discovery or WebSocket.
                SessionBoundServiceManager.start(
                    context = appContext,
                    ownerUid = ownerUid
                )

                val manager = CareTaskDataStoreManager.create(appContext)
                val pendingTasks = UserDataScope.withOwnerUid(ownerUid) {
                    manager.pendingTasksFlow.first()
                }

                if (ownerProvider.currentOwnerUid() != ownerUid) {
                    return@launch
                }

                pendingTasks.forEach { task ->
                    CareTaskReminderScheduler.schedule(
                        context = appContext,
                        task = task.copy(
                            ownerUid = task.ownerUid.ifBlank { ownerUid }
                        )
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

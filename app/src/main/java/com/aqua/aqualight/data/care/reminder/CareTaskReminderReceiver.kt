package com.aqua.aqualight.data.care.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.notifications.OwnerNotificationPreferences
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.utils.NotificationHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        val intentOwnerUid = intent.getStringExtra(
            CareTaskReminderScheduler.EXTRA_OWNER_UID
        ).orEmpty().trim()
        val occurrence = intent.getStringExtra(
            CareTaskReminderScheduler.EXTRA_OCCURRENCE
        )?.let { raw ->
            runCatching { CareReminderOccurrence.valueOf(raw) }.getOrNull()
        }

        if (taskId <= 0L || intentOwnerUid.isBlank() || occurrence == null) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ownerProvider = FirebaseAuthenticatedOwnerProvider.create(
                    appContext
                )
                val activeUid = ownerProvider.currentOwnerUid().orEmpty().trim()

                if (activeUid.isBlank() || intentOwnerUid != activeUid) {
                    return@launch
                }

                val notificationPreferences = OwnerNotificationPreferences.create(
                    appContext
                )
                if (!notificationPreferences.isEnabled(activeUid)) {
                    return@launch
                }

                val careTaskManager = CareTaskDataStoreManager.create(appContext)
                val tankManager = AquariumTankDataStoreManager(appContext)

                val task = UserDataScope.withOwnerUid(activeUid) {
                    careTaskManager.taskFlow(taskId).firstOrNull()
                } ?: return@launch

                if (task.ownerUid != activeUid) {
                    return@launch
                }

                val ownerTask = task.copy(ownerUid = activeUid)
                val nowMillis = System.currentTimeMillis()

                if (!matchesScheduledOccurrence(ownerTask, occurrence, nowMillis)) {
                    CareTaskReminderScheduler.schedule(
                        context = appContext,
                        task = ownerTask,
                        nowMillis = nowMillis
                    )
                    return@launch
                }

                val tanks = UserDataScope.withOwnerUid(activeUid) {
                    tankManager.tanksFlow.firstOrNull().orEmpty()
                }
                val tank = tanks.firstOrNull { candidate ->
                    candidate.id == ownerTask.tankId
                }

                if (!CareReminderDeliveryPolicy.shouldDeliver(task, tank)) {
                    CareTaskReminderScheduler.cancel(
                        context = appContext,
                        taskId = ownerTask.id,
                        ownerUid = activeUid
                    )
                    return@launch
                }

                // Owner can change while stores are being read. Never render an
                // old owner's reminder into the new owner's foreground session.
                if (ownerProvider.currentOwnerUid() != activeUid) {
                    return@launch
                }

                val typeUi = CareTaskTypeCatalog.get(ownerTask.type)
                val baseTitle = ownerTask.title.ifBlank {
                    typeUi.title(appContext)
                }
                val title = if (
                    ownerTask.waterChangePercent != null &&
                    ownerTask.waterChangePercent > 0 &&
                    !baseTitle.contains("%")
                ) {
                    appContext.getString(
                        R.string.maintenance_task_title_with_percent,
                        baseTitle,
                        ownerTask.waterChangePercent
                    )
                } else {
                    baseTitle
                }

                val bodyText = when {
                    ownerTask.note.isNotBlank() -> ownerTask.note
                    ownerTask.description.isNotBlank() -> ownerTask.description
                    typeUi.defaultDescription(appContext).isNotBlank() -> {
                        typeUi.defaultDescription(appContext)
                    }
                    else -> appContext.getString(
                        R.string.maintenance_notification_due_now
                    )
                }

                val message = if (tank?.name?.isNotBlank() == true) {
                    appContext.getString(
                        R.string.maintenance_notification_message_with_aquarium,
                        tank.name,
                        bodyText
                    )
                } else {
                    bodyText
                }

                NotificationHelper.showCareTaskReminderNotification(
                    context = appContext,
                    taskId = ownerTask.id,
                    title = title,
                    message = message,
                    largeIconRes = typeUi.iconRes,
                    largeIconColor = typeUi.accentColor,
                    ownerUid = activeUid
                )

                // The same deterministic policy decides whether a missed
                // occurrence remains. No receiver-local relative timer is used.
                CareTaskReminderScheduler.schedule(
                    context = appContext,
                    task = ownerTask,
                    nowMillis = nowMillis
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun matchesScheduledOccurrence(
        task: CareTask,
        occurrence: CareReminderOccurrence,
        nowMillis: Long
    ): Boolean {
        return when (occurrence) {
            CareReminderOccurrence.DUE -> task.dueAtMillis <= nowMillis
            CareReminderOccurrence.MISSED -> {
                if (!task.missedReminderEnabled) {
                    false
                } else {
                    val missedAt = runCatching {
                        Math.addExact(
                            task.dueAtMillis,
                            TimeUnit.DAYS.toMillis(
                                task.missedReminderDays.toLong()
                            )
                        )
                    }.getOrNull()
                    missedAt != null && missedAt <= nowMillis
                }
            }
        }
    }
}

package com.aqua.aqualight.data.care.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.utils.NotificationHelper
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
        ).orEmpty()

        if (taskId <= 0L) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ownerProvider = FirebaseAuthenticatedOwnerProvider.create(
                    appContext
                )
                val activeUid = ownerProvider.currentOwnerUid().orEmpty()

                if (activeUid.isBlank()) {
                    return@launch
                }

                if (
                    intentOwnerUid.isNotBlank() &&
                    intentOwnerUid != activeUid
                ) {
                    return@launch
                }

                val userPrefs = UserPreferencesManager.create(appContext)
                val appNotificationsEnabled = userPrefs.notificationsEnabled
                    .firstOrNull() ?: false

                if (!appNotificationsEnabled) {
                    return@launch
                }

                val careTaskManager = CareTaskDataStoreManager.create(appContext)
                val tankManager = AquariumTankDataStoreManager(appContext)

                val task = UserDataScope.withOwnerUid(activeUid) {
                    careTaskManager.taskFlow(taskId).firstOrNull()
                } ?: return@launch

                if (
                    task.ownerUid.isNotBlank() &&
                    !UserDataScope.belongsToOwner(
                        recordOwnerUid = task.ownerUid,
                        ownerUid = activeUid,
                        includeLegacy = false
                    )
                ) {
                    return@launch
                }

                if (
                    task.status != CareTaskStatus.PENDING ||
                    !task.reminderEnabled
                ) {
                    CareTaskReminderScheduler.cancel(
                        context = appContext,
                        taskId = task.id,
                        ownerUid = task.ownerUid.ifBlank { activeUid }
                    )
                    return@launch
                }

                val tanks = UserDataScope.withOwnerUid(activeUid) {
                    tankManager.tanksFlow.firstOrNull().orEmpty()
                }
                val aquariumName = tanks.firstOrNull { tank ->
                    tank.id == task.tankId
                }?.name.orEmpty()

                // Owner can change while stores are being read. Never render an
                // old owner's reminder into the new owner's foreground session.
                if (ownerProvider.currentOwnerUid() != activeUid) {
                    return@launch
                }

                val typeUi = CareTaskTypeCatalog.get(task.type)
                val baseTitle = task.title.ifBlank {
                    typeUi.title(appContext)
                }
                val title = if (
                    task.waterChangePercent != null &&
                    task.waterChangePercent > 0 &&
                    !baseTitle.contains("%")
                ) {
                    appContext.getString(
                        R.string.maintenance_task_title_with_percent,
                        baseTitle,
                        task.waterChangePercent
                    )
                } else {
                    baseTitle
                }

                val bodyText = when {
                    task.note.isNotBlank() -> task.note
                    task.description.isNotBlank() -> task.description
                    typeUi.defaultDescription(appContext).isNotBlank() -> {
                        typeUi.defaultDescription(appContext)
                    }
                    else -> appContext.getString(
                        R.string.maintenance_notification_due_now
                    )
                }

                val message = if (aquariumName.isNotBlank()) {
                    appContext.getString(
                        R.string.maintenance_notification_message_with_aquarium,
                        aquariumName,
                        bodyText
                    )
                } else {
                    bodyText
                }

                NotificationHelper.showCareTaskReminderNotification(
                    context = appContext,
                    taskId = task.id,
                    title = title,
                    message = message,
                    largeIconRes = typeUi.iconRes,
                    largeIconColor = typeUi.accentColor,
                    ownerUid = task.ownerUid.ifBlank { activeUid }
                )

                if (task.missedReminderEnabled) {
                    CareTaskReminderScheduler.scheduleMissedReminder(
                        context = appContext,
                        task = task.copy(
                            ownerUid = task.ownerUid.ifBlank { activeUid }
                        )
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

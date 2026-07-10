package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentStartupRepair
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler
import com.aqua.aqualight.data.care.smartcare.SmartCareDailyWorker
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.flow.first

/**
 * Starts/stops services that must only run while a user session is active.
 */
object SessionBoundServiceManager {

    fun start(
        context: Context
    ) {
        val appContext = context.applicationContext

        DevicesRepositoryProvider.restartForCurrentOwner(
            context = appContext
        )

        TankDeviceAssignmentStartupRepair.schedule(
            context = appContext
        )

        SmartCareDailyWorker.schedule(
            context = appContext
        )
    }

    suspend fun stop(
        context: Context,
        cancelNotifications: Boolean = true
    ) {
        val appContext = context.applicationContext

        TankDeviceAssignmentStartupRepair.reset()
        DevicesRepositoryProvider.stopSession()

        SmartCareDailyWorker.cancel(
            context = appContext
        )

        cancelPendingCareTaskReminders(
            context = appContext
        )

        if (cancelNotifications) {
            NotificationHelper.cancelAllAppNotifications(
                context = appContext
            )
        }
    }

    private suspend fun cancelPendingCareTaskReminders(
        context: Context
    ) {
        runCatching {
            val careTaskDataStoreManager = CareTaskDataStoreManager.create(
                context
            )

            val pendingTasks = careTaskDataStoreManager.pendingTasksFlow
                .first()

            pendingTasks.forEach { task ->
                CareTaskReminderScheduler.cancel(
                    context = context,
                    taskId = task.id,
                    ownerUid = task.ownerUid
                )
            }
        }
    }
}

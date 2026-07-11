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
        val failures = mutableListOf<Throwable>()

        suspend fun attempt(
            label: String,
            block: suspend () -> Unit
        ) {
            runCatching {
                block()
            }.onFailure { error ->
                failures += IllegalStateException(
                    "Session shutdown step '$label' failed.",
                    error
                )
            }
        }

        TankDeviceAssignmentStartupRepair.reset()

        attempt("device-runtime") {
            DevicesRepositoryProvider.stopSession()
        }
        attempt("smart-care-worker") {
            SmartCareDailyWorker.cancel(
                context = appContext
            )
        }
        attempt("care-reminders") {
            cancelPendingCareTaskReminders(
                context = appContext
            )
        }

        if (cancelNotifications) {
            attempt("notifications") {
                NotificationHelper.cancelAllAppNotifications(
                    context = appContext
                )
            }
        }

        if (failures.isNotEmpty()) {
            throw IllegalStateException(
                "One or more session-bound services could not be stopped."
            ).also { aggregateError ->
                failures.forEach(aggregateError::addSuppressed)
            }
        }
    }

    private suspend fun cancelPendingCareTaskReminders(
        context: Context
    ) {
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

package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler
import com.aqua.aqualight.data.care.smartcare.SmartCareDailyWorker
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.flow.first

/**
 * Starts and stops services that are valid only for one authenticated owner.
 */
object SessionBoundServiceManager {

    enum class StopStep {
        ASSIGNMENT_REPOSITORY,
        DEVICES_REPOSITORY,
        SMART_CARE,
        CARE_REMINDERS,
        NOTIFICATIONS
    }

    data class StopIssue(
        val step: StopStep,
        val error: Throwable
    )

    data class StopResult(
        val issues: List<StopIssue>
    ) {
        val hasErrors: Boolean
            get() = issues.isNotEmpty()

        fun exceptionOrNull(): Throwable? {
            return if (issues.isEmpty()) {
                null
            } else {
                SessionBoundStopException(issues)
            }
        }
    }

    class SessionBoundStopException(
        val issues: List<StopIssue>
    ) : IllegalStateException(
        issues.joinToString(
            prefix = "Session shutdown failed in ",
            separator = ", "
        ) { issue ->
            issue.step.name
        }
    )

    fun start(
        context: Context
    ) {
        val appContext = context.applicationContext

        SmartCareDailyWorker.schedule(
            context = appContext
        )
    }

    suspend fun stop(
        context: Context,
        cancelNotifications: Boolean = true,
        expectedOwnerUid: String? = null
    ): StopResult {
        val appContext = context.applicationContext
        val issues = mutableListOf<StopIssue>()

        suspend fun runStep(
            step: StopStep,
            block: suspend () -> Unit
        ) {
            runCatching {
                block()
            }.onFailure { error ->
                issues += StopIssue(
                    step = step,
                    error = error
                )
            }
        }

        runStep(StopStep.ASSIGNMENT_REPOSITORY) {
            TankDeviceAssignmentRepositoryProvider.clear(
                expectedOwnerUid = expectedOwnerUid
            )
        }

        runStep(StopStep.DEVICES_REPOSITORY) {
            DevicesRepositoryProvider.clear(
                expectedOwnerUid = expectedOwnerUid
            )
        }

        runStep(StopStep.SMART_CARE) {
            SmartCareDailyWorker.cancel(
                context = appContext
            )
        }

        runStep(StopStep.CARE_REMINDERS) {
            cancelPendingCareTaskReminders(
                context = appContext
            )
        }

        if (cancelNotifications) {
            runStep(StopStep.NOTIFICATIONS) {
                NotificationHelper.cancelAllAppNotifications(
                    context = appContext
                )
            }
        }

        return StopResult(
            issues = issues.toList()
        )
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

package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler
import com.aqua.aqualight.data.care.smartcare.SmartCareDailyWorker
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.utils.NotificationHelper
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Starts and stops services that are valid only for one authenticated owner.
 */
object SessionBoundServiceManager {

    enum class StopStep {
        PROVISIONING_TRANSACTIONS,
        DEVICES_REPOSITORY,
        ASSIGNMENT_REPOSITORY,
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
        context: Context,
        ownerUid: String
    ) {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)
        if (normalizedOwnerUid.isBlank()) {
            return
        }

        SmartCareDailyWorker.schedule(
            context = context.applicationContext,
            ownerUid = normalizedOwnerUid
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
                if (error is CancellationException) {
                    throw error
                }
                issues += StopIssue(
                    step = step,
                    error = error
                )
            }
        }

        val ownerUid = expectedOwnerUid
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: DevicesRepositoryProvider.currentOwnerUid()

        if (ownerUid != null) {
            runStep(StopStep.PROVISIONING_TRANSACTIONS) {
                AqlProvisioningHandoffSaver(appContext)
                    .rollbackPendingRegistrationsForOwner(ownerUid)
                    .getOrThrow()
            }
        }

        // Stop runtime collectors, sockets and owner token access before clearing
        // other owner-bound repositories or scheduling state.
        runStep(StopStep.DEVICES_REPOSITORY) {
            DevicesRepositoryProvider.clear(
                expectedOwnerUid = expectedOwnerUid
            )
        }

        runStep(StopStep.ASSIGNMENT_REPOSITORY) {
            TankDeviceAssignmentRepositoryProvider.clear(
                expectedOwnerUid = expectedOwnerUid
            )
        }

        runStep(StopStep.SMART_CARE) {
            SmartCareDailyWorker.cancel(
                context = appContext,
                ownerUid = ownerUid
            )
        }

        if (ownerUid != null) {
            runStep(StopStep.CARE_REMINDERS) {
                cancelPendingCareTaskReminders(
                    context = appContext,
                    ownerUid = ownerUid
                )
            }
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
        context: Context,
        ownerUid: String
    ) {
        val careTaskDataStoreManager = CareTaskDataStoreManager.create(
            context
        )

        val pendingTasks = UserDataScope.withOwnerUid(ownerUid) {
            careTaskDataStoreManager.pendingTasksFlow.first()
        }

        pendingTasks.forEach { task ->
            CareTaskReminderScheduler.cancel(
                context = context,
                taskId = task.id,
                ownerUid = task.ownerUid.ifBlank { ownerUid }
            )
        }
    }
}

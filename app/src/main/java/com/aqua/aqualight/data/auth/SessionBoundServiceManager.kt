package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.care.reminder.CareReminderReconcileWorker
import com.aqua.aqualight.data.care.smartcare.SmartCareDailyWorker
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.CancellationException

/** Starts and stops services that are valid only for one authenticated owner. */
object SessionBoundServiceManager {

    enum class StopStep {
        PROVISIONING_TRANSACTIONS,
        DEVICE_UPDATE_CHECKS,
        DEVICES_REPOSITORY,
        ASSIGNMENT_REPOSITORY,
        SMART_CARE,
        NOTIFICATION_SCHEDULES,
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
            return if (issues.isEmpty()) null else SessionBoundStopException(issues)
        }
    }

    class SessionBoundStopException(
        val issues: List<StopIssue>
    ) : IllegalStateException(
        issues.joinToString(
            prefix = "Session shutdown failed in ",
            separator = ", "
        ) { issue -> issue.step.name }
    )

    suspend fun start(context: Context, ownerUid: String) {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)
        if (normalizedOwnerUid.isBlank()) return

        val appContext = context.applicationContext
        val notificationPlatform = NotificationPlatform.get(appContext)
        SmartCareDailyWorker.schedule(appContext, normalizedOwnerUid)
        notificationPlatform.deviceUpdateWorkCoordinator
            .reconcileOwner(normalizedOwnerUid)
        CareReminderReconcileWorker.enqueue(appContext, normalizedOwnerUid)
    }

    suspend fun stop(
        context: Context,
        cancelNotifications: Boolean = true,
        expectedOwnerUid: String? = null
    ): StopResult {
        val appContext = context.applicationContext
        val issues = mutableListOf<StopIssue>()

        suspend fun runStep(step: StopStep, block: suspend () -> Unit) {
            runCatching { block() }.onFailure { error ->
                if (error is CancellationException) throw error
                issues += StopIssue(step, error)
            }
        }

        val ownerUid = expectedOwnerUid
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: DevicesRepositoryProvider.currentOwnerUid()
        val notificationPlatform = NotificationPlatform.get(appContext)

        if (ownerUid != null) {
            runStep(StopStep.PROVISIONING_TRANSACTIONS) {
                AqlProvisioningHandoffSaver(appContext)
                    .rollbackPendingRegistrationsForOwner(ownerUid)
                    .getOrThrow()
            }
            runStep(StopStep.DEVICE_UPDATE_CHECKS) {
                notificationPlatform.deviceUpdateWorkCoordinator.cancelOwner(ownerUid)
            }
        }

        runStep(StopStep.DEVICES_REPOSITORY) {
            DevicesRepositoryProvider.clear(expectedOwnerUid = expectedOwnerUid)
        }
        runStep(StopStep.ASSIGNMENT_REPOSITORY) {
            TankDeviceAssignmentRepositoryProvider.clear(expectedOwnerUid = expectedOwnerUid)
        }
        runStep(StopStep.SMART_CARE) {
            SmartCareDailyWorker.cancel(appContext, ownerUid)
        }

        if (ownerUid != null) {
            runStep(StopStep.NOTIFICATION_SCHEDULES) {
                notificationPlatform.scheduler.cancelOwner(ownerUid)
            }
            if (cancelNotifications) {
                runStep(StopStep.NOTIFICATIONS) {
                    notificationPlatform.deviceFirmwareUpdates.clearOwner(ownerUid)
                }
            }
        }

        return StopResult(issues.toList())
    }
}

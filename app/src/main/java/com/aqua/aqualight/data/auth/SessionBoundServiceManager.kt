package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.care.reminder.CareReminderReconcileWorker
import com.aqua.aqualight.data.care.smartcare.SmartCareDailyWorker
import com.aqua.aqualight.data.devices.notification.DeviceFirmwareAvailabilityWorker
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.CancellationException

/** Starts and stops services that are valid only for one authenticated owner. */
object SessionBoundServiceManager {

    enum class StopStep {
        PROVISIONING_TRANSACTIONS,
        FIRMWARE_AVAILABILITY,
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

    fun start(context: Context, ownerUid: String) {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)
        if (normalizedOwnerUid.isBlank()) return

        val appContext = context.applicationContext
        SmartCareDailyWorker.schedule(appContext, normalizedOwnerUid)
        CareReminderReconcileWorker.enqueue(appContext, normalizedOwnerUid)
        DeviceFirmwareAvailabilityWorker.schedule(appContext, normalizedOwnerUid)
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

        if (ownerUid != null) {
            runStep(StopStep.PROVISIONING_TRANSACTIONS) {
                AqlProvisioningHandoffSaver(appContext)
                    .rollbackPendingRegistrationsForOwner(ownerUid)
                    .getOrThrow()
            }
        }

        // Prevent future owner work before retiring the repository and its shared OTA coordinator.
        runStep(StopStep.FIRMWARE_AVAILABILITY) {
            DeviceFirmwareAvailabilityWorker.cancel(appContext, ownerUid)
        }

        // Stop runtime collectors, sockets and owner token access before clearing
        // other owner-bound repositories or scheduling state.
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
            val platform = NotificationPlatform.get(appContext)
            runStep(StopStep.NOTIFICATION_SCHEDULES) {
                platform.scheduler.cancelOwner(ownerUid)
            }
            if (cancelNotifications) {
                runStep(StopStep.NOTIFICATIONS) {
                    platform.renderer.cancelOwner(ownerUid)
                }
            }
        }

        return StopResult(issues.toList())
    }
}

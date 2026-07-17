package com.aqua.aqualight.application.notifications

import kotlinx.coroutines.flow.Flow

enum class NotificationCategory {
    CARE_REMINDERS,
    DEVICE_ALERTS,
    DEVICE_UPDATES
}

enum class NotificationChannelState {
    NOT_REQUIRED,
    MISSING,
    BLOCKED,
    ENABLED
}

data class NotificationDeliveryReadiness(
    val runtimePermissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val channelState: NotificationChannelState
) {
    val canDeliver: Boolean
        get() = runtimePermissionGranted &&
            appNotificationsEnabled &&
            channelState != NotificationChannelState.MISSING &&
            channelState != NotificationChannelState.BLOCKED
}

data class NotificationPreferenceSnapshot(
    val ownerPreferenceEnabled: Boolean,
    val delivery: Map<NotificationCategory, NotificationDeliveryReadiness>
) {
    fun readiness(category: NotificationCategory): NotificationDeliveryReadiness {
        return delivery.getValue(category)
    }
}

interface NotificationPreferenceRepository {
    fun enabledFlow(ownerUid: String): Flow<Boolean>
    suspend fun isEnabled(ownerUid: String): Boolean
    suspend fun setEnabled(ownerUid: String, enabled: Boolean)
}

interface NotificationPermissionPolicy {
    fun ensureChannels()
    fun evaluate(category: NotificationCategory): NotificationDeliveryReadiness
    fun channelId(category: NotificationCategory): String
}

interface NotificationScheduler {
    suspend fun scheduleCareTask(ownerUid: String, taskId: Long)
    suspend fun cancelCareTask(ownerUid: String, taskId: Long)
    suspend fun reconcileOwner(ownerUid: String)
    suspend fun cancelOwner(ownerUid: String)
}

interface NotificationRenderer {
    fun cancelCareReminder(ownerUid: String, taskId: Long)
    fun cancelOwner(ownerUid: String)
}

class NotificationPreferenceUseCase(
    private val repository: NotificationPreferenceRepository,
    private val permissionPolicy: NotificationPermissionPolicy,
    private val scheduler: NotificationScheduler,
    private val renderer: NotificationRenderer
) {
    fun observe(ownerUid: String): Flow<Boolean> = repository.enabledFlow(ownerUid)

    suspend fun snapshot(ownerUid: String): NotificationPreferenceSnapshot {
        val enabled = repository.isEnabled(ownerUid)
        val readiness = NotificationCategory.entries.associateWith(permissionPolicy::evaluate)
        return NotificationPreferenceSnapshot(
            ownerPreferenceEnabled = enabled,
            delivery = readiness
        )
    }

    suspend fun setEnabled(ownerUid: String, enabled: Boolean) {
        repository.setEnabled(ownerUid, enabled)
        if (enabled) {
            permissionPolicy.ensureChannels()
            scheduler.reconcileOwner(ownerUid)
        } else {
            scheduler.cancelOwner(ownerUid)
            renderer.cancelOwner(ownerUid)
        }
    }

    suspend fun scheduleCareTask(ownerUid: String, taskId: Long) {
        if (!repository.isEnabled(ownerUid)) {
            scheduler.cancelCareTask(ownerUid, taskId)
            renderer.cancelCareReminder(ownerUid, taskId)
            return
        }
        scheduler.scheduleCareTask(ownerUid, taskId)
    }

    suspend fun cancelCareTask(ownerUid: String, taskId: Long) {
        scheduler.cancelCareTask(ownerUid, taskId)
        renderer.cancelCareReminder(ownerUid, taskId)
    }

    suspend fun reconcileOwner(ownerUid: String) {
        if (repository.isEnabled(ownerUid)) {
            permissionPolicy.ensureChannels()
            scheduler.reconcileOwner(ownerUid)
        } else {
            scheduler.cancelOwner(ownerUid)
            renderer.cancelOwner(ownerUid)
        }
    }

    suspend fun cancelOwner(ownerUid: String) {
        scheduler.cancelOwner(ownerUid)
        renderer.cancelOwner(ownerUid)
    }
}

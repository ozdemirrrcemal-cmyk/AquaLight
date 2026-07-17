package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.first

private val Context.notificationScheduleStateDataStore:
    DataStore<NotificationScheduleStateStore> by dataStore(
        fileName = "notification_schedule_state.pb",
        serializer = NotificationScheduleStateSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler {
            LocalDataRecoveryTracker.markRecovered(
                LocalDataRecoveryTracker.Area.NOTIFICATION_PREFERENCES
            )
            NotificationScheduleStateRules.defaultStore()
        }
    )

/**
 * Persistent owner/task ledger for AlarmManager identities.
 *
 * Android does not expose an API to enumerate application alarms. Keeping this
 * small canonical ledger allows reconciliation to cancel alarms whose task was
 * removed by crash recovery or another destructive transaction.
 */
class CareReminderScheduleLedger private constructor(
    private val context: Context
) {

    suspend fun taskIds(ownerUid: String): Set<Long> {
        val owner = requireOwnerUid(ownerUid)
        return context.notificationScheduleStateDataStore.data.first()
            .let(NotificationScheduleStateRules::validateStore)
            .ownerSchedulesList
            .firstOrNull { schedule -> schedule.ownerUid == owner }
            ?.taskIdsList
            ?.toSet()
            .orEmpty()
    }

    suspend fun markScheduled(ownerUid: String, taskId: Long) {
        updateOwner(ownerUid, taskId, add = true)
    }

    suspend fun markCancelled(ownerUid: String, taskId: Long) {
        updateOwner(ownerUid, taskId, add = false)
    }

    suspend fun clearOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        context.notificationScheduleStateDataStore.updateData { current ->
            val validated = NotificationScheduleStateRules.validateStore(current)
            NotificationScheduleStateRules.validateStore(
                validated.toBuilder()
                    .clearOwnerSchedules()
                    .addAllOwnerSchedules(
                        validated.ownerSchedulesList.filterNot { schedule ->
                            schedule.ownerUid == owner
                        }
                    )
                    .build()
            )
        }
    }

    private suspend fun updateOwner(
        ownerUid: String,
        taskId: Long,
        add: Boolean
    ) {
        val owner = requireOwnerUid(ownerUid)
        require(taskId > 0L) { "taskId must be positive" }

        context.notificationScheduleStateDataStore.updateData { current ->
            val validated = NotificationScheduleStateRules.validateStore(current)
            val existing = validated.ownerSchedulesList.firstOrNull { schedule ->
                schedule.ownerUid == owner
            }
            val taskIds = existing?.taskIdsList.orEmpty().toMutableSet().apply {
                if (add) add(taskId) else remove(taskId)
            }.toList().sorted()

            val retained = validated.ownerSchedulesList
                .filterNot { schedule -> schedule.ownerUid == owner }
                .toMutableList()

            if (taskIds.isNotEmpty()) {
                retained += OwnerCareReminderSchedule.newBuilder()
                    .setOwnerUid(owner)
                    .addAllTaskIds(taskIds)
                    .build()
            }

            NotificationScheduleStateRules.validateStore(
                validated.toBuilder()
                    .clearOwnerSchedules()
                    .addAllOwnerSchedules(retained.sortedBy { it.ownerUid })
                    .build()
            )
        }
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank() && normalized == ownerUid) {
                "ownerUid must be canonical and non-blank"
            }
        }
    }

    companion object {
        fun create(context: Context): CareReminderScheduleLedger {
            return CareReminderScheduleLedger(context.applicationContext)
        }
    }
}

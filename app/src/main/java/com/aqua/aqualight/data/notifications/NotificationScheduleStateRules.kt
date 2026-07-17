package com.aqua.aqualight.data.notifications

import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope

internal object NotificationScheduleStateRules {
    private const val SCHEMA_VERSION = 1
    private const val MAX_OWNERS = 32
    private const val MAX_TASKS_PER_OWNER = 10_000

    fun defaultStore(): NotificationScheduleStateStore {
        return NotificationScheduleStateStore.newBuilder()
            .setSchemaVersion(SCHEMA_VERSION)
            .build()
    }

    fun validateStore(
        store: NotificationScheduleStateStore
    ): NotificationScheduleStateStore {
        if (store.schemaVersion != SCHEMA_VERSION) {
            violation("Notification schedule state schema is unsupported.")
        }
        if (store.ownerSchedulesCount > MAX_OWNERS) {
            violation("Notification schedule state exceeds the owner limit.")
        }

        val owners = mutableSetOf<String>()
        var previousOwner = ""
        store.ownerSchedulesList.forEach { schedule ->
            val owner = canonicalOwner(schedule.ownerUid)
            if (!owners.add(owner)) {
                violation("Duplicate notification schedule owner: $owner")
            }
            if (previousOwner.isNotEmpty() && owner <= previousOwner) {
                violation("Notification schedule owners must be sorted.")
            }
            previousOwner = owner

            if (schedule.taskIdsCount > MAX_TASKS_PER_OWNER) {
                violation("Notification schedule task count exceeds the owner limit.")
            }
            var previousTaskId = 0L
            schedule.taskIdsList.forEach { taskId ->
                if (taskId <= 0L) {
                    violation("Notification schedule task IDs must be positive.")
                }
                if (taskId <= previousTaskId) {
                    violation("Notification schedule task IDs must be unique and sorted.")
                }
                previousTaskId = taskId
            }
        }

        return store
    }

    fun canonicalOwner(ownerUid: String): String {
        val canonical = UserDataScope.normalizeOwnerUid(ownerUid)
        if (canonical.isBlank() || canonical != ownerUid) {
            violation("Notification schedule owner UID must be canonical and non-blank.")
        }
        return canonical
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }
}

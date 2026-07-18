package com.aqua.aqualight.data.notifications

import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NotificationScheduleStateRulesTest {

    @Test
    fun canonicalSortedOwnerAndTaskEntriesAreAccepted() {
        val store = NotificationScheduleStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerSchedules(schedule("owner-a", 1L, 2L))
            .addOwnerSchedules(schedule("owner-b", 5L, 9L))
            .build()

        assertEquals(store, NotificationScheduleStateRules.validateStore(store))
    }

    @Test
    fun duplicateOrUnsortedTaskIdsAreRejected() {
        val duplicate = NotificationScheduleStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerSchedules(schedule("owner-a", 2L, 2L))
            .build()
        val unsorted = NotificationScheduleStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerSchedules(schedule("owner-a", 9L, 3L))
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            NotificationScheduleStateRules.validateStore(duplicate)
        }
        assertThrows(StoreInvariantViolation::class.java) {
            NotificationScheduleStateRules.validateStore(unsorted)
        }
    }

    @Test
    fun duplicateOrUnsortedOwnersAreRejected() {
        val duplicate = NotificationScheduleStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerSchedules(schedule("owner-a", 1L))
            .addOwnerSchedules(schedule("owner-a", 2L))
            .build()
        val unsorted = NotificationScheduleStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerSchedules(schedule("owner-b", 1L))
            .addOwnerSchedules(schedule("owner-a", 2L))
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            NotificationScheduleStateRules.validateStore(duplicate)
        }
        assertThrows(StoreInvariantViolation::class.java) {
            NotificationScheduleStateRules.validateStore(unsorted)
        }
    }

    @Test
    fun unsupportedSchemaAndInvalidOwnerAreRejected() {
        val unsupported = NotificationScheduleStateStore.newBuilder()
            .setSchemaVersion(2)
            .build()
        val invalidOwner = NotificationScheduleStateStore.newBuilder()
            .setSchemaVersion(1)
            .addOwnerSchedules(schedule(" owner-a", 1L))
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            NotificationScheduleStateRules.validateStore(unsupported)
        }
        assertThrows(StoreInvariantViolation::class.java) {
            NotificationScheduleStateRules.validateStore(invalidOwner)
        }
    }

    private fun schedule(
        ownerUid: String,
        vararg taskIds: Long
    ): OwnerCareReminderSchedule {
        return OwnerCareReminderSchedule.newBuilder()
            .setOwnerUid(ownerUid)
            .addAllTaskIds(taskIds.toList())
            .build()
    }
}

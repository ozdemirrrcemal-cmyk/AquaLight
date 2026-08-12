package com.aqua.aqualight.data.care

import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CareTaskStoreRulesTest {

    @Test
    fun ownerScopedIdsAllowDifferentOwnersButRejectSameOwnerDuplicates() {
        val validStore = CareTaskStoreRules.defaultStore().toBuilder()
            .addTasks(validStoredTask(id = 10L, ownerUid = "owner-a"))
            .addTasks(validStoredTask(id = 10L, ownerUid = "owner-b"))
            .build()

        assertEquals(validStore, CareTaskStoreRules.validateStore(validStore))

        val duplicateStore = CareTaskStoreRules.defaultStore().toBuilder()
            .addTasks(validStoredTask(id = 20L, ownerUid = "owner-a"))
            .addTasks(validStoredTask(id = 20L, ownerUid = "owner-a"))
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStore(duplicateStore)
        }
    }

    @Test
    fun invalidEnumIsRejectedInsteadOfSilentlyDefaulted() {
        val invalid = validStoredTask(id = 30L, ownerUid = "owner-a")
            .toBuilder()
            .setStatus("BROKEN_STATUS")
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStoredTask(invalid)
        }
    }

    @Test
    fun waterChangePercentAndReminderRelationshipsAreStrict() {
        val invalidPercent = validStoredTask(id = 40L, ownerUid = "owner-a")
            .toBuilder()
            .setType(CareTaskType.WATER_CHANGE.name)
            .setWaterChangePercent(101)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStoredTask(invalidPercent)
        }

        val invalidReminder = validStoredTask(id = 41L, ownerUid = "owner-a")
            .toBuilder()
            .setReminderEnabled(false)
            .setMissedReminderEnabled(true)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStoredTask(invalidReminder)
        }
    }

    @Test
    fun completedTimestampCannotBeLaterThanTheLastUpdate() {
        val invalidStored = validStoredTask(id = 42L, ownerUid = "owner-a")
            .toBuilder()
            .setStatus(CareTaskStatus.COMPLETED.name)
            .setCompletedAtMillis(UPDATED_MILLIS + 1L)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateStoredTask(invalidStored)
        }

        val invalidDomain = validDomainTask(id = 43L, ownerUid = "owner-a").copy(
            status = CareTaskStatus.COMPLETED,
            completedAtMillis = UPDATED_MILLIS + 1L
        )

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateTask(invalidDomain)
        }
    }

    @Test
    fun activeOwnerMismatchIsRejected() {
        val task = validDomainTask(id = 50L, ownerUid = "owner-a")

        assertThrows(StoreInvariantViolation::class.java) {
            CareTaskStoreRules.validateTask(
                task = task,
                expectedOwnerUid = "owner-b"
            )
        }
    }

    @Test
    fun nextIdIsMonotonicInsideTheCurrentStoreSnapshot() {
        val tasks = listOf(
            validStoredTask(id = 100L, ownerUid = "owner-a"),
            validStoredTask(id = 250L, ownerUid = "owner-a")
        )

        assertEquals(
            251L,
            CareTaskStoreRules.nextUniqueId(
                currentTasks = tasks,
                nowMillis = 200L
            )
        )
    }

    private fun validStoredTask(
        id: Long,
        ownerUid: String
    ): StoredCareTask = StoredCareTask.newBuilder()
        .setId(id)
        .setOwnerUid(ownerUid)
        .setTankId(77L)
        .setTitle("Inspect filter")
        .setDescription("Routine filter inspection")
        .setType(CareTaskType.FILTER_MAINTENANCE.name)
        .setSource(CareTaskSource.MANUAL.name)
        .setStatus(CareTaskStatus.PENDING.name)
        .setDueAtMillis(1_767_312_000_000L)
        .setCompletedAtMillis(0L)
        .setRepeatEnabled(false)
        .setRepeatIntervalDays(1)
        .setReminderEnabled(false)
        .setMissedReminderEnabled(false)
        .setMissedReminderDays(1)
        .setWaterChangePercent(0)
        .setNote("")
        .setGeneratedRuleKey("")
        .setCreatedAtMillis(UPDATED_MILLIS)
        .setUpdatedAtMillis(UPDATED_MILLIS)
        .build()

    private fun validDomainTask(
        id: Long,
        ownerUid: String
    ): CareTask = CareTask(
        id = id,
        ownerUid = ownerUid,
        tankId = 77L,
        title = "Inspect filter",
        description = "Routine filter inspection",
        type = CareTaskType.FILTER_MAINTENANCE,
        source = CareTaskSource.MANUAL,
        status = CareTaskStatus.PENDING,
        dueAtMillis = 1_767_312_000_000L,
        completedAtMillis = null,
        repeatEnabled = false,
        repeatIntervalDays = 1,
        reminderEnabled = false,
        missedReminderEnabled = false,
        missedReminderDays = 1,
        waterChangePercent = null,
        note = "",
        generatedRuleKey = "",
        createdAtMillis = UPDATED_MILLIS,
        updatedAtMillis = UPDATED_MILLIS
    )

    private companion object {
        const val UPDATED_MILLIS = 1_767_225_600_000L
    }
}

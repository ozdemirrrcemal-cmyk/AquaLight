package com.aqua.aqualight.data.care.integrity

import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.store.StoreInvariantViolation

/**
 * Validates care-store writes while one or more tank deletions are in flight.
 *
 * A blocked tank may be represented only by its exact captured snapshot or by no tasks at all.
 * The first state is the untouched/rolled-back side of the transaction; the second state is the
 * deleted side. Any partial, added, or modified task set is rejected. Process tombstones have no
 * pending snapshot and therefore reject every stale reference.
 */
internal object TankCareIntegrityWriteGuard {

    fun requireValidWrite(tasks: Iterable<StoredCareTask>) {
        val tasksByTank = tasks
            .groupBy { task -> TankKey(task.ownerUid, task.tankId) }

        tasksByTank.forEach { (key, actualTasks) ->
            if (!TankCareIntegrityJournal.isWriteBlocked(key.ownerUid, key.tankId)) {
                return@forEach
            }

            val pending = TankCareIntegrityJournal
                .pendingForOwner(key.ownerUid)
                .firstOrNull { entry -> entry.tankId == key.tankId }
                ?: violation(
                    "Care-task write targets a tank deleted in the current process."
                )

            if (pending.state != TankCareIntegrityJournal.State.SNAPSHOTS_CAPTURED) {
                violation(
                    "Care-task write targets a tank before deletion snapshots are captured."
                )
            }

            val expectedById = pending.taskSnapshots
                .associate { snapshot -> snapshot.id to snapshot.toStoredTask() }
            val actualById = actualTasks.associateBy { task -> task.id }

            if (actualById != expectedById) {
                violation(
                    "Care-task write partially mutates a tank with an active deletion transaction."
                )
            }
        }
    }

    private data class TankKey(
        val ownerUid: String,
        val tankId: Long
    )

    private fun CareTask.toStoredTask(): StoredCareTask {
        CareTaskStoreRules.validateTask(this)
        return StoredCareTask.newBuilder()
            .setId(id)
            .setOwnerUid(ownerUid)
            .setTankId(tankId)
            .setTitle(title)
            .setDescription(description)
            .setType(type.name)
            .setSource(source.name)
            .setStatus(status.name)
            .setDueAtMillis(dueAtMillis)
            .setCompletedAtMillis(completedAtMillis ?: 0L)
            .setRepeatEnabled(repeatEnabled)
            .setRepeatIntervalDays(repeatIntervalDays)
            .setReminderEnabled(reminderEnabled)
            .setMissedReminderEnabled(missedReminderEnabled)
            .setMissedReminderDays(missedReminderDays)
            .setWaterChangePercent(waterChangePercent ?: 0)
            .setNote(note)
            .setGeneratedRuleKey(generatedRuleKey)
            .setCreatedAtMillis(createdAtMillis)
            .setUpdatedAtMillis(updatedAtMillis)
            .build()
            .also(CareTaskStoreRules::validateStoredTask)
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }
}

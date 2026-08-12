package com.aqua.aqualight.data.care.integrity

import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.store.StoreInvariantViolation
import kotlinx.coroutines.flow.first

internal suspend fun CareTaskDataStoreManager.snapshotTasksForIntegrity(
    tankId: Long
): List<CareTask> = tasksForTankFlow(tankId).first()

internal suspend fun CareTaskDataStoreManager.restoreTaskSnapshotsForIntegrity(
    tankId: Long,
    snapshots: List<CareTask>
) {
    val currentById = tasksForTankFlow(tankId)
        .first()
        .associateBy { task -> task.id }

    snapshots.forEach { snapshot ->
        if (snapshot.tankId != tankId) {
            throw StoreInvariantViolation(
                "A rollback snapshot belongs to another tank."
            )
        }

        val current = currentById[snapshot.id]
        when {
            current == null -> addTask(snapshot)
            current != snapshot -> throw StoreInvariantViolation(
                "A rollback snapshot conflicts with an existing care task."
            )
        }
    }
}

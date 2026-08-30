package com.aqua.aqualight.data.care.integrity

import android.content.Context
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope

internal class TankCareIntegrityRecovery private constructor(
    private val tankStore: AquariumTankDataStoreManager,
    private val careTaskStore: CareTaskDataStoreManager
) {

    data class Result(
        val restoredTaskCount: Int,
        val removedTaskCount: Int,
        val recoveredTransactionCount: Int
    )

    suspend fun recover(ownerUid: String): Result {
        val owner = ownerUid.trim()
        require(owner.isNotBlank() && owner == ownerUid) {
            "ownerUid must be canonical and non-blank"
        }
        if (UserDataScope.requireCurrentUid() != owner) {
            throw StoreInvariantViolation(
                "Tank-care recovery owner does not match the active owner."
            )
        }

        val existingTankIds = tankStore
            .tanksSnapshotForOwner(owner)
            .mapTo(mutableSetOf()) { tank -> tank.id }

        var restoredTaskCount = 0
        var removedTaskCount = 0
        var recoveredTransactionCount = 0

        TankCareIntegrityJournal.pendingForOwner(owner).forEach { pending ->
            if (pending.tankId in existingTankIds) {
                when (pending.state) {
                    TankCareIntegrityJournal.State.BLOCKED -> {
                        // The process stopped before any care-task mutation.
                        TankCareIntegrityJournal.abort(owner, pending.tankId)
                    }

                    TankCareIntegrityJournal.State.SNAPSHOTS_CAPTURED -> {
                        val beforeIds = careTaskStore
                            .snapshotTasksForIntegrity(pending.tankId)
                            .mapTo(mutableSetOf()) { task -> task.id }

                        TankCareIntegrityJournal.withRollbackWritesAllowed(
                            ownerUid = owner,
                            tankId = pending.tankId
                        ) {
                            careTaskStore.restoreTaskSnapshotsForIntegrity(
                                tankId = pending.tankId,
                                snapshots = pending.taskSnapshots
                            )
                        }
                        TankCareIntegrityJournal.abort(owner, pending.tankId)

                        restoredTaskCount += pending.taskSnapshots.count { task ->
                            task.id !in beforeIds
                        }
                    }
                }
            } else {
                val existingTasks = careTaskStore
                    .snapshotTasksForIntegrity(pending.tankId)
                careTaskStore.deleteTasksForTank(pending.tankId)
                TankCareIntegrityJournal.complete(owner, pending.tankId)
                removedTaskCount += existingTasks.size
            }

            recoveredTransactionCount += 1
        }

        return Result(
            restoredTaskCount = restoredTaskCount,
            removedTaskCount = removedTaskCount,
            recoveredTransactionCount = recoveredTransactionCount
        )
    }

    companion object {
        fun create(context: Context): TankCareIntegrityRecovery {
            val appContext = context.applicationContext
            TankCareIntegrityJournal.initialize(appContext)
            return TankCareIntegrityRecovery(
                tankStore = AquariumTankDataStoreManager(appContext),
                careTaskStore = CareTaskDataStoreManager.create(appContext)
            )
        }
    }
}

package com.aqua.aqualight.data.care.integrity

import android.content.Context
import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.aquarium.health.WaterAnalysisDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope

internal class TankCareIntegrityRecovery private constructor(
    private val tankStore: AquariumTankDataStoreManager,
    private val careTaskStore: CareTaskDataStoreManager,
    private val waterAnalysisStore: WaterAnalysisDataStoreManager
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

        var restoredTaskCount = 0
        var removedTaskCount = 0
        var recoveredTransactionCount = 0

        TankCareIntegrityJournal.pendingForOwner(owner).forEach { candidate ->
            OwnerTankMutationGate.shared.withTanks(owner, listOf(candidate.tankId)) {
                // Both journal and tank state may have changed while awaiting a live deletion.
                val pending = TankCareIntegrityJournal.pendingForOwner(owner)
                    .firstOrNull { it.tankId == candidate.tankId } ?: return@withTanks
                val tankExists = tankStore.tanksSnapshotForOwner(owner)
                    .any { it.id == pending.tankId }
                val result = recoverTank(owner, pending, tankExists)
                restoredTaskCount += result.restoredTaskCount
                removedTaskCount += result.removedTaskCount
                recoveredTransactionCount += result.recoveredTransactionCount
            }
        }

        waterAnalysisStore.deletionIntegrity.reconcileResolvedStages(owner)
        return Result(
            restoredTaskCount = restoredTaskCount,
            removedTaskCount = removedTaskCount,
            recoveredTransactionCount = recoveredTransactionCount
        )
    }

    private suspend fun recoverTank(
        owner: String,
        pending: TankCareIntegrityJournal.PendingDeletion,
        tankExists: Boolean
    ): Result {
        var restoredTaskCount = 0
        var removedTaskCount = 0
        if (tankExists) {
            when (pending.state) {
                TankCareIntegrityJournal.State.BLOCKED -> {
                    // The process stopped before any care-task mutation.
                    TankCareIntegrityJournal.abort(owner, pending.tankId)
                    waterAnalysisStore.deletionIntegrity.finish(pending.tankId)
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
                        pending.waterTransactionId?.let { transaction ->
                            waterAnalysisStore.deletionIntegrity.restore(pending.tankId, transaction)
                        }
                    }
                    TankCareIntegrityJournal.abort(owner, pending.tankId)
                    waterAnalysisStore.deletionIntegrity.finish(pending.tankId, pending.waterTransactionId)

                    restoredTaskCount += pending.taskSnapshots.count { task ->
                        task.id !in beforeIds
                    }
                }
            }
        } else {
            val existingTasks = careTaskStore
                .snapshotTasksForIntegrity(pending.tankId)
            careTaskStore.deleteTasksForTank(pending.tankId)
            waterAnalysisStore.deleteAnalysesForTank(pending.tankId)
            TankCareIntegrityJournal.complete(owner, pending.tankId)
            waterAnalysisStore.deletionIntegrity.finish(pending.tankId, pending.waterTransactionId)
            removedTaskCount += existingTasks.size
        }

        return Result(
            restoredTaskCount = restoredTaskCount,
            removedTaskCount = removedTaskCount,
            recoveredTransactionCount = 1
        )
    }

    companion object {
        fun create(context: Context): TankCareIntegrityRecovery {
            val appContext = context.applicationContext
            TankCareIntegrityJournal.initialize(appContext)
            return TankCareIntegrityRecovery(
                tankStore = AquariumTankDataStoreManager(appContext),
                careTaskStore = CareTaskDataStoreManager.create(appContext),
                waterAnalysisStore = WaterAnalysisDataStoreManager(appContext)
            )
        }
    }
}

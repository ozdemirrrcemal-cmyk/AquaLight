package com.aqua.aqualight.data.aquarium.health.integrity

import android.content.Context
import com.aqua.aqualight.data.aquarium.health.AquariumHealthDataStoreManager
import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope

internal class TankHealthIntegrityRecovery private constructor(
    private val tankStore: AquariumTankDataStoreManager,
    private val healthStore: AquariumHealthDataStoreManager
) {

    data class Result(
        val restoredRecordCount: Int,
        val removedRecordCount: Int,
        val recoveredTransactionCount: Int,
        val removedOrphanRecordCount: Int
    )

    private data class RecoveryDelta(
        val restoredRecordCount: Int = 0,
        val removedRecordCount: Int = 0
    )

    suspend fun recover(ownerUid: String): Result {
        val owner = requireActiveOwner(ownerUid)
        val existingTankIds = tankStore
            .tanksSnapshotForOwner(owner)
            .mapTo(mutableSetOf()) { tank -> tank.id }

        var restoredRecordCount = 0
        var removedRecordCount = 0
        val pending =
            TankHealthIntegrityJournal.pendingForOwner(owner)

        pending.forEach { deletion ->
            val delta = recoverPendingDeletion(
                ownerUid = owner,
                existingTankIds = existingTankIds,
                pending = deletion
            )
            restoredRecordCount += delta.restoredRecordCount
            removedRecordCount += delta.removedRecordCount
        }

        return Result(
            restoredRecordCount = restoredRecordCount,
            removedRecordCount = removedRecordCount,
            recoveredTransactionCount = pending.size,
            removedOrphanRecordCount =
                healthStore.integrity.repairOrphanedRecords(owner)
        )
    }

    private suspend fun recoverPendingDeletion(
        ownerUid: String,
        existingTankIds: Set<Long>,
        pending: TankHealthIntegrityJournal.PendingDeletion
    ): RecoveryDelta {
        return if (pending.tankId in existingTankIds) {
            recoverExistingTank(ownerUid, pending)
        } else {
            finishDeletedTank(ownerUid, pending)
        }
    }

    private suspend fun recoverExistingTank(
        ownerUid: String,
        pending: TankHealthIntegrityJournal.PendingDeletion
    ): RecoveryDelta {
        return when (pending.state) {
            TankHealthIntegrityJournal.State.BLOCKED -> {
                TankHealthIntegrityJournal.abort(
                    ownerUid,
                    pending.tankId
                )
                RecoveryDelta()
            }

            TankHealthIntegrityJournal.State.SNAPSHOTS_CAPTURED -> {
                RecoveryDelta(
                    restoredRecordCount = restoreCapturedSnapshot(
                        ownerUid = ownerUid,
                        pending = pending
                    )
                )
            }
        }
    }

    private suspend fun restoreCapturedSnapshot(
        ownerUid: String,
        pending: TankHealthIntegrityJournal.PendingDeletion
    ): Int {
        val before = healthStore.integrity.snapshotForTank(
            ownerUid = ownerUid,
            tankId = pending.tankId
        )
        val restoredCount = countMissingRecords(
            snapshot = pending.snapshot,
            before = before
        )

        TankHealthIntegrityJournal.withRollbackWritesAllowed(
            ownerUid = ownerUid,
            tankId = pending.tankId
        ) {
            healthStore.integrity.restoreSnapshotForIntegrity(
                ownerUid = ownerUid,
                tankId = pending.tankId,
                snapshot = pending.snapshot
            )
        }
        TankHealthIntegrityJournal.abort(
            ownerUid,
            pending.tankId
        )
        return restoredCount
    }

    private suspend fun finishDeletedTank(
        ownerUid: String,
        pending: TankHealthIntegrityJournal.PendingDeletion
    ): RecoveryDelta {
        val existing = healthStore.integrity.snapshotForTank(
            ownerUid = ownerUid,
            tankId = pending.tankId
        )
        healthStore.integrity.deleteRecordsForTank(
            ownerUid = ownerUid,
            tankId = pending.tankId
        )
        TankHealthIntegrityJournal.complete(
            ownerUid,
            pending.tankId
        )
        return RecoveryDelta(
            removedRecordCount = existing.recordCount
        )
    }

    private fun countMissingRecords(
        snapshot: TankHealthIntegritySnapshot,
        before: TankHealthIntegritySnapshot
    ): Int {
        val beforeWaterIds =
            before.waterTests.mapTo(mutableSetOf()) { it.id }
        val beforeLivestockIds =
            before.livestockObservations
                .mapTo(mutableSetOf()) { it.id }
        val beforePlantIds =
            before.plantObservations
                .mapTo(mutableSetOf()) { it.id }

        return snapshot.waterTests.count { test ->
            test.id !in beforeWaterIds
        } + snapshot.livestockObservations.count { observation ->
            observation.id !in beforeLivestockIds
        } + snapshot.plantObservations.count { observation ->
            observation.id !in beforePlantIds
        }
    }

    private fun requireActiveOwner(ownerUid: String): String {
        val owner = ownerUid.trim()
        require(owner.isNotBlank() && owner == ownerUid) {
            "ownerUid must be canonical and non-blank"
        }
        if (UserDataScope.requireCurrentUid() != owner) {
            throw StoreInvariantViolation(
                "Tank-health recovery owner does not match the active owner."
            )
        }
        return owner
    }

    companion object {
        fun create(context: Context): TankHealthIntegrityRecovery {
            val appContext = context.applicationContext
            TankHealthIntegrityJournal.initialize(appContext)
            return TankHealthIntegrityRecovery(
                tankStore = AquariumTankDataStoreManager(appContext),
                healthStore =
                    AquariumHealthDataStoreManager.create(appContext)
            )
        }
    }
}

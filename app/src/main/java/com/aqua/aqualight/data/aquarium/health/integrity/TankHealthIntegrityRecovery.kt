package com.aqua.aqualight.data.aquarium.health.integrity

import android.content.Context
import com.aqua.aqualight.data.aquarium.health.AquariumHealthDataStoreManager
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

    suspend fun recover(ownerUid: String): Result {
        val owner = ownerUid.trim()
        require(owner.isNotBlank() && owner == ownerUid) {
            "ownerUid must be canonical and non-blank"
        }
        if (UserDataScope.requireCurrentUid() != owner) {
            throw StoreInvariantViolation(
                "Tank-health recovery owner does not match the active owner."
            )
        }

        val existingTankIds = tankStore
            .tanksSnapshotForOwner(owner)
            .mapTo(mutableSetOf()) { tank -> tank.id }

        var restoredRecordCount = 0
        var removedRecordCount = 0
        var recoveredTransactionCount = 0

        TankHealthIntegrityJournal.pendingForOwner(owner).forEach { pending ->
            if (pending.tankId in existingTankIds) {
                when (pending.state) {
                    TankHealthIntegrityJournal.State.BLOCKED -> {
                        TankHealthIntegrityJournal.abort(owner, pending.tankId)
                    }

                    TankHealthIntegrityJournal.State.SNAPSHOTS_CAPTURED -> {
                        val before = healthStore.snapshotForTank(
                            ownerUid = owner,
                            tankId = pending.tankId
                        )
                        val beforeWaterIds =
                            before.waterTests.mapTo(mutableSetOf()) { it.id }
                        val beforeObservationIds =
                            before.observations.mapTo(mutableSetOf()) { it.id }

                        TankHealthIntegrityJournal.withRollbackWritesAllowed(
                            ownerUid = owner,
                            tankId = pending.tankId
                        ) {
                            healthStore.restoreSnapshotForIntegrity(
                                ownerUid = owner,
                                tankId = pending.tankId,
                                snapshot = pending.snapshot
                            )
                        }
                        TankHealthIntegrityJournal.abort(owner, pending.tankId)

                        restoredRecordCount +=
                            pending.snapshot.waterTests.count { test ->
                                test.id !in beforeWaterIds
                            } +
                            pending.snapshot.observations.count { observation ->
                                observation.id !in beforeObservationIds
                            }
                    }
                }
            } else {
                val existing = healthStore.snapshotForTank(
                    ownerUid = owner,
                    tankId = pending.tankId
                )
                healthStore.deleteRecordsForTank(
                    ownerUid = owner,
                    tankId = pending.tankId
                )
                TankHealthIntegrityJournal.complete(owner, pending.tankId)
                removedRecordCount += existing.recordCount
            }
            recoveredTransactionCount += 1
        }

        val removedOrphanRecordCount =
            healthStore.repairOrphanedRecords(owner)

        return Result(
            restoredRecordCount = restoredRecordCount,
            removedRecordCount = removedRecordCount,
            recoveredTransactionCount = recoveredTransactionCount,
            removedOrphanRecordCount = removedOrphanRecordCount
        )
    }

    companion object {
        fun create(context: Context): TankHealthIntegrityRecovery {
            val appContext = context.applicationContext
            TankHealthIntegrityJournal.initialize(appContext)
            return TankHealthIntegrityRecovery(
                tankStore = AquariumTankDataStoreManager(appContext),
                healthStore = AquariumHealthDataStoreManager.create(appContext)
            )
        }
    }
}

package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.care.model.CareTask
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class TankDeletionSnapshots(
    val careByTank: Map<Long, List<CareTask>>,
    val healthByTank: Map<Long, TankHealthIntegritySnapshot>
)

private sealed interface TankDeletionPreparation {
    data class Ready(
        val snapshots: TankDeletionSnapshots
    ) : TankDeletionPreparation

    data class Failed(
        val error: Throwable
    ) : TankDeletionPreparation
}

internal class OwnerTankDeletionCoordinator(
    private val dependencies: OwnerTankDeletionDependencies,
    private val ownerUid: String,
    private val tankIds: List<Long>
) {

    suspend fun delete(): OwnerTankDataCleaner.Result {
        return when (val preparation = prepare()) {
            is TankDeletionPreparation.Failed ->
                OwnerTankDataCleaner.Result.DeleteFailed(
                    preparation.error
                )

            is TankDeletionPreparation.Ready ->
                commit(preparation.snapshots)
        }
    }

    private suspend fun prepare(): TankDeletionPreparation {
        val beginError = beginTransactions()
        val snapshotResult = if (beginError == null) {
            runCatching(::captureSnapshots)
        } else {
            Result.failure(beginError)
        }
        val error = snapshotResult.exceptionOrNull()

        return if (error == null) {
            TankDeletionPreparation.Ready(
                snapshotResult.getOrThrow()
            )
        } else {
            withContext(NonCancellable) {
                abortTransactions()
            }?.let(error::addSuppressed)
            error.throwIfTankDeletionCancellation()
            TankDeletionPreparation.Failed(error)
        }
    }

    private fun beginTransactions(): Throwable? {
        val careError = runCatching {
            dependencies.care.integrity.begin(
                ownerUid,
                tankIds
            )
        }.exceptionOrNull()
        careError?.throwIfTankDeletionCancellation()

        if (careError != null) {
            return careError
        }

        val healthError = runCatching {
            dependencies.health.integrity.begin(
                ownerUid,
                tankIds
            )
        }.exceptionOrNull()
        healthError?.throwIfTankDeletionCancellation()

        if (healthError != null) {
            abortCareTransactions()
                ?.let(healthError::addSuppressed)
        }
        return healthError
    }

    private suspend fun captureSnapshots():
        TankDeletionSnapshots {
        val careByTank =
            linkedMapOf<Long, List<CareTask>>()
        val healthByTank =
            linkedMapOf<Long, TankHealthIntegritySnapshot>()

        tankIds.forEach { tankId ->
            careByTank[tankId] =
                dependencies.care.snapshotForTank(tankId)
            healthByTank[tankId] =
                dependencies.health.snapshotForTank(
                    ownerUid,
                    tankId
                )
        }

        dependencies.care.integrity.captureSnapshots(
            ownerUid = ownerUid,
            snapshotsByTank = careByTank
        )
        dependencies.health.integrity.captureSnapshots(
            ownerUid = ownerUid,
            snapshotsByTank = healthByTank
        )

        return TankDeletionSnapshots(
            careByTank = careByTank,
            healthByTank = healthByTank
        )
    }

    private suspend fun commit(
        snapshots: TankDeletionSnapshots
    ): OwnerTankDataCleaner.Result {
        val deleteError = runCatching {
            tankIds.forEach { tankId ->
                dependencies.care.deleteForTank(tankId)
                dependencies.health.deleteForTank(
                    ownerUid,
                    tankId
                )
            }
            dependencies.deleteTankRecords(tankIds)
        }.exceptionOrNull()

        return if (deleteError == null) {
            OwnerTankDeletionCompletion(
                dependencies = dependencies,
                ownerUid = ownerUid,
                tankIds = tankIds,
                snapshots = snapshots
            ).finish()
        } else {
            withContext(NonCancellable) {
                rollback(snapshots)
            }?.let(deleteError::addSuppressed)
            deleteError.throwIfTankDeletionCancellation()
            OwnerTankDataCleaner.Result.DeleteFailed(
                deleteError
            )
        }
    }

    private suspend fun rollback(
        snapshots: TankDeletionSnapshots
    ): Throwable? {
        var failure: Throwable? = null

        snapshots.careByTank.forEach { (tankId, tasks) ->
            val error = runCatching {
                dependencies.care.integrity
                    .withRollbackWritesAllowed(
                        ownerUid = ownerUid,
                        tankId = tankId
                    ) {
                        dependencies.care.restoreForTank(
                            tankId,
                            tasks
                        )
                    }
            }.exceptionOrNull()
            failure = failure.combineTankDeletion(error)
        }

        snapshots.healthByTank.forEach {
                (tankId, snapshot) ->
            val error = runCatching {
                dependencies.health.integrity
                    .withRollbackWritesAllowed(
                        ownerUid = ownerUid,
                        tankId = tankId
                    ) {
                        dependencies.health.restoreForTank(
                            ownerUid,
                            tankId,
                            snapshot
                        )
                    }
            }.exceptionOrNull()
            failure = failure.combineTankDeletion(error)
        }

        failure = failure.combineTankDeletion(
            abortTransactions()
        )
        val reconcileError = runCatching {
            dependencies.care.reconcileReminders(ownerUid)
        }.exceptionOrNull()
        return failure.combineTankDeletion(
            reconcileError
        )
    }

    private fun abortTransactions(): Throwable? {
        var failure: Throwable? = null
        tankIds.forEach { tankId ->
            failure = failure.combineTankDeletion(
                runCatching {
                    dependencies.care.integrity.abort(
                        ownerUid,
                        tankId
                    )
                }.exceptionOrNull()
            )
            failure = failure.combineTankDeletion(
                runCatching {
                    dependencies.health.integrity.abort(
                        ownerUid,
                        tankId
                    )
                }.exceptionOrNull()
            )
        }
        return failure
    }

    private fun abortCareTransactions(): Throwable? {
        var failure: Throwable? = null
        tankIds.forEach { tankId ->
            failure = failure.combineTankDeletion(
                runCatching {
                    dependencies.care.integrity.abort(
                        ownerUid,
                        tankId
                    )
                }.exceptionOrNull()
            )
        }
        return failure
    }
}

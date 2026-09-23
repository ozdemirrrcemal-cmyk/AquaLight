package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityJournal
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityTransactions
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityTransactions
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class TankCareDeletionDependencies(
    val snapshotForTank: suspend (Long) -> List<CareTask>,
    val deleteForTank: suspend (Long) -> Unit,
    val restoreForTank: suspend (Long, List<CareTask>) -> Unit,
    val cancelReminder: suspend (String, Long) -> Unit,
    val reconcileReminders: suspend (String) -> Unit,
    val integrity: TankCareIntegrityTransactions = TankCareIntegrityJournal
)

internal data class TankHealthDeletionDependencies(
    val snapshotForTank: suspend (String, Long) -> TankHealthIntegritySnapshot,
    val deleteForTank: suspend (String, Long) -> Unit,
    val restoreForTank:
        suspend (String, Long, TankHealthIntegritySnapshot) -> Unit,
    val integrity: TankHealthIntegrityTransactions = TankHealthIntegrityJournal
)

internal data class OwnerTankDeletionDependencies(
    val deleteTankRecords: suspend (List<Long>) -> Unit,
    val care: TankCareDeletionDependencies,
    val health: TankHealthDeletionDependencies,
    val removeDeviceAssignmentsForTank:
        suspend (Long) -> TankAssignmentCleanupResult,
    val ownerUidProvider: () -> String = UserDataScope::requireCurrentUid
)

/**
 * Coordinates authoritative tank deletion without owning any dependent store.
 *
 * Care and Health dependencies provide their own durable integrity transactions.
 * The cleaner composes those transactions around the authoritative tank write and
 * performs device-assignment cleanup only after the tank deletion has committed.
 */
class OwnerTankDataCleaner internal constructor(
    private val dependencies: OwnerTankDeletionDependencies
) {
    enum class CleanupStage {
        CARE_TASKS,
        HEALTH_RECORDS,
        DEVICE_ASSIGNMENTS
    }

    data class CleanupIssue(
        val tankId: Long,
        val stage: CleanupStage,
        val error: Throwable
    )

    sealed interface Result {
        data object NoOp : Result

        data class DeleteFailed(
            val error: Throwable
        ) : Result

        data class Deleted(
            val tankIds: List<Long>,
            val cleanupIssues: List<CleanupIssue>
        ) : Result {
            val hasCleanupIssues: Boolean
                get() = cleanupIssues.isNotEmpty()
        }
    }

    suspend fun deleteTanks(tankIds: Iterable<Long>): Result {
        val normalizedTankIds = tankIds
            .filter { tankId -> tankId > 0L }
            .distinct()

        return if (normalizedTankIds.isEmpty()) {
            Result.NoOp
        } else {
            deleteNormalizedTanks(
                dependencies = dependencies,
                ownerUid = requireOwnerUid(dependencies.ownerUidProvider()),
                tankIds = normalizedTankIds
            )
        }
    }
}

private data class TankDeletionSnapshots(
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

private suspend fun deleteNormalizedTanks(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankIds: List<Long>
): OwnerTankDataCleaner.Result {
    return when (
        val preparation = prepareDeletion(
            dependencies = dependencies,
            ownerUid = ownerUid,
            tankIds = tankIds
        )
    ) {
        is TankDeletionPreparation.Failed ->
            OwnerTankDataCleaner.Result.DeleteFailed(preparation.error)

        is TankDeletionPreparation.Ready ->
            commitPreparedDeletion(
                dependencies = dependencies,
                ownerUid = ownerUid,
                tankIds = tankIds,
                snapshots = preparation.snapshots
            )
    }
}

private suspend fun prepareDeletion(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankIds: List<Long>
): TankDeletionPreparation {
    val beginError = beginIntegrityTransactions(
        dependencies = dependencies,
        ownerUid = ownerUid,
        tankIds = tankIds
    )
    val snapshotResult = if (beginError == null) {
        runCatching {
            captureDependentSnapshots(
                dependencies = dependencies,
                ownerUid = ownerUid,
                tankIds = tankIds
            )
        }
    } else {
        Result.failure(beginError)
    }

    val error = snapshotResult.exceptionOrNull()

    return if (error == null) {
        TankDeletionPreparation.Ready(snapshotResult.getOrThrow())
    } else {
        val abortError = withContext(NonCancellable) {
            abortIntegrityTransactions(
                dependencies = dependencies,
                ownerUid = ownerUid,
                tankIds = tankIds
            )
        }
        abortError?.let(error::addSuppressed)
        error.throwIfCancellation()
        TankDeletionPreparation.Failed(error)
    }
}

private fun beginIntegrityTransactions(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankIds: List<Long>
): Throwable? {
    val careResult = runCatching {
        dependencies.care.integrity.begin(ownerUid, tankIds)
    }
    val careError = careResult.exceptionOrNull()
    careError?.throwIfCancellation()

    return if (careError != null) {
        careError
    } else {
        val healthError = runCatching {
            dependencies.health.integrity.begin(ownerUid, tankIds)
        }.exceptionOrNull()
        healthError?.throwIfCancellation()
        if (healthError != null) {
            abortCareTransactions(
                care = dependencies.care,
                ownerUid = ownerUid,
                tankIds = tankIds
            )?.let(healthError::addSuppressed)
        }
        healthError
    }
}

private suspend fun captureDependentSnapshots(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankIds: List<Long>
): TankDeletionSnapshots {
    val careByTank = linkedMapOf<Long, List<CareTask>>()
    val healthByTank = linkedMapOf<Long, TankHealthIntegritySnapshot>()

    tankIds.forEach { tankId ->
        careByTank[tankId] = dependencies.care.snapshotForTank(tankId)
        healthByTank[tankId] =
            dependencies.health.snapshotForTank(ownerUid, tankId)
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

private suspend fun commitPreparedDeletion(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankIds: List<Long>,
    snapshots: TankDeletionSnapshots
): OwnerTankDataCleaner.Result {
    val deleteError = runCatching {
        tankIds.forEach { tankId ->
            dependencies.care.deleteForTank(tankId)
            dependencies.health.deleteForTank(ownerUid, tankId)
        }
        dependencies.deleteTankRecords(tankIds)
    }.exceptionOrNull()

    return if (deleteError == null) {
        finishCommittedDeletion(
            dependencies = dependencies,
            ownerUid = ownerUid,
            tankIds = tankIds,
            snapshots = snapshots
        )
    } else {
        withContext(NonCancellable) {
            rollbackDependentData(
                dependencies = dependencies,
                ownerUid = ownerUid,
                snapshots = snapshots
            )
        }?.let(deleteError::addSuppressed)
        deleteError.throwIfCancellation()
        OwnerTankDataCleaner.Result.DeleteFailed(deleteError)
    }
}

private suspend fun finishCommittedDeletion(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankIds: List<Long>,
    snapshots: TankDeletionSnapshots
): OwnerTankDataCleaner.Result.Deleted {
    val issues = mutableListOf<OwnerTankDataCleaner.CleanupIssue>()

    tankIds.forEach { tankId ->
        cancelDeletedCareReminders(
            dependencies = dependencies,
            ownerUid = ownerUid,
            tankId = tankId,
            tasks = snapshots.careByTank[tankId].orEmpty(),
            issues = issues
        )
        completeIntegrityTransactions(
            dependencies = dependencies,
            ownerUid = ownerUid,
            tankId = tankId,
            issues = issues
        )
        cleanupDeviceAssignments(
            dependencies = dependencies,
            tankId = tankId,
            issues = issues
        )
    }

    return OwnerTankDataCleaner.Result.Deleted(
        tankIds = tankIds,
        cleanupIssues = issues.toList()
    )
}

private suspend fun cancelDeletedCareReminders(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankId: Long,
    tasks: List<CareTask>,
    issues: MutableList<OwnerTankDataCleaner.CleanupIssue>
) {
    tasks.forEach { task ->
        val error = runCatching {
            dependencies.care.cancelReminder(ownerUid, task.id)
        }.exceptionOrNull()
        error?.throwIfCancellation()
        error?.let { failure ->
            issues += cleanupIssue(
                tankId = tankId,
                stage = OwnerTankDataCleaner.CleanupStage.CARE_TASKS,
                error = failure
            )
        }
    }
}

private fun completeIntegrityTransactions(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankId: Long,
    issues: MutableList<OwnerTankDataCleaner.CleanupIssue>
) {
    completeIntegrityTransaction(
        ownerUid = ownerUid,
        tankId = tankId,
        stage = OwnerTankDataCleaner.CleanupStage.CARE_TASKS,
        complete = dependencies.care.integrity::complete,
        issues = issues
    )
    completeIntegrityTransaction(
        ownerUid = ownerUid,
        tankId = tankId,
        stage = OwnerTankDataCleaner.CleanupStage.HEALTH_RECORDS,
        complete = dependencies.health.integrity::complete,
        issues = issues
    )
}

private fun completeIntegrityTransaction(
    ownerUid: String,
    tankId: Long,
    stage: OwnerTankDataCleaner.CleanupStage,
    complete: (String, Long) -> Unit,
    issues: MutableList<OwnerTankDataCleaner.CleanupIssue>
) {
    val error = runCatching {
        complete(ownerUid, tankId)
    }.exceptionOrNull()
    error?.throwIfCancellation()
    error?.let { failure ->
        issues += cleanupIssue(tankId, stage, failure)
    }
}

private suspend fun cleanupDeviceAssignments(
    dependencies: OwnerTankDeletionDependencies,
    tankId: Long,
    issues: MutableList<OwnerTankDataCleaner.CleanupIssue>
) {
    val result = runCatching {
        dependencies.removeDeviceAssignmentsForTank(tankId)
    }
    val error = result.exceptionOrNull()
    error?.throwIfCancellation()

    if (error != null) {
        issues += cleanupIssue(
            tankId,
            OwnerTankDataCleaner.CleanupStage.DEVICE_ASSIGNMENTS,
            error
        )
    } else {
        appendAssignmentResultIssue(
            tankId = tankId,
            result = result.getOrThrow(),
            issues = issues
        )
    }
}

private fun appendAssignmentResultIssue(
    tankId: Long,
    result: TankAssignmentCleanupResult,
    issues: MutableList<OwnerTankDataCleaner.CleanupIssue>
) {
    when (result) {
        is TankAssignmentCleanupResult.Completed -> Unit
        TankAssignmentCleanupResult.InvalidRequest -> {
            issues += cleanupIssue(
                tankId = tankId,
                stage = OwnerTankDataCleaner.CleanupStage.DEVICE_ASSIGNMENTS,
                error = IllegalArgumentException(
                    "Tank assignment cleanup received an invalid tank id."
                )
            )
        }
        is TankAssignmentCleanupResult.Failure -> {
            issues += cleanupIssue(
                tankId = tankId,
                stage = OwnerTankDataCleaner.CleanupStage.DEVICE_ASSIGNMENTS,
                error = result.error
            )
        }
    }
}

private suspend fun rollbackDependentData(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    snapshots: TankDeletionSnapshots
): Throwable? {
    var rollbackFailure: Throwable? = null

    snapshots.careByTank.forEach { (tankId, tasks) ->
        val error = runCatching {
            dependencies.care.integrity.withRollbackWritesAllowed(
                ownerUid = ownerUid,
                tankId = tankId
            ) {
                dependencies.care.restoreForTank(tankId, tasks)
            }
        }.exceptionOrNull()
        rollbackFailure = rollbackFailure.combine(error)
    }

    snapshots.healthByTank.forEach { (tankId, snapshot) ->
        val error = runCatching {
            dependencies.health.integrity.withRollbackWritesAllowed(
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
        rollbackFailure = rollbackFailure.combine(error)
    }

    rollbackFailure = rollbackFailure.combine(
        abortIntegrityTransactions(
            dependencies = dependencies,
            ownerUid = ownerUid,
            tankIds = snapshots.careByTank.keys.toList()
        )
    )

    val reconcileError = runCatching {
        dependencies.care.reconcileReminders(ownerUid)
    }.exceptionOrNull()
    return rollbackFailure.combine(reconcileError)
}

private fun abortIntegrityTransactions(
    dependencies: OwnerTankDeletionDependencies,
    ownerUid: String,
    tankIds: List<Long>
): Throwable? {
    var failure: Throwable? = null
    tankIds.forEach { tankId ->
        val careError = runCatching {
            dependencies.care.integrity.abort(ownerUid, tankId)
        }.exceptionOrNull()
        failure = failure.combine(careError)

        val healthError = runCatching {
            dependencies.health.integrity.abort(ownerUid, tankId)
        }.exceptionOrNull()
        failure = failure.combine(healthError)
    }
    return failure
}

private fun abortCareTransactions(
    care: TankCareDeletionDependencies,
    ownerUid: String,
    tankIds: List<Long>
): Throwable? {
    var failure: Throwable? = null
    tankIds.forEach { tankId ->
        failure = failure.combine(
            runCatching {
                care.integrity.abort(ownerUid, tankId)
            }.exceptionOrNull()
        )
    }
    return failure
}

private fun cleanupIssue(
    tankId: Long,
    stage: OwnerTankDataCleaner.CleanupStage,
    error: Throwable
) = OwnerTankDataCleaner.CleanupIssue(
    tankId = tankId,
    stage = stage,
    error = error
)

private fun requireOwnerUid(value: String): String {
    val owner = value.trim()
    require(owner.isNotBlank()) {
        "Tank deletion requires a non-blank owner uid."
    }
    return owner
}

private fun Throwable?.combine(other: Throwable?): Throwable? {
    if (other == null) return this
    val current = this
    return if (current == null) {
        other
    } else {
        current.addSuppressed(other)
        current
    }
}

private fun Throwable.throwIfCancellation() {
    if (this is CancellationException) throw this
}

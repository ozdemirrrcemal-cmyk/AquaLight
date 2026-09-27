package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityTransactions
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Coordinates authoritative tank deletion with a crash-safe compensating transaction.
 *
 * Care-task writes are blocked before snapshots are captured. Care tasks are deleted
 * before the tank record, so an orphan reference is never committed. If the tank write
 * fails or the operation is cancelled, task snapshots are restored before the failure
 * is returned. A durable journal allows owner-session recovery after process death.
 */
internal data class OwnerTankDeletionStores(
    val deleteTankRecords: suspend (List<Long>) -> Unit,
    val snapshotCareTasksForTank: suspend (Long) -> List<CareTask>,
    val deleteCareTasksForTank: suspend (Long) -> Unit,
    val restoreCareTasksForTank: suspend (Long, List<CareTask>) -> Unit,
    val deleteWaterAnalysesForTank: suspend (Long) -> Unit
)

class OwnerTankDataCleaner internal constructor(
    private val stores: OwnerTankDeletionStores,
    private val removeDeviceAssignmentsForTank:
        suspend (Long) -> TankAssignmentCleanupResult,
    private val cancelCareTaskReminder: suspend (String, Long) -> Unit,
    private val reconcileCareReminders: suspend (String) -> Unit,
    private val integrityTransactions: TankCareIntegrityTransactions =
        TankCareIntegrityJournal,
    private val ownerUidProvider: () -> String = UserDataScope::requireCurrentUid
) {
    enum class CleanupStage {
        CARE_TASKS,
        WATER_ANALYSES,
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

        if (normalizedTankIds.isEmpty()) return Result.NoOp

        val ownerUid = ownerUidProvider().trim().also { owner ->
            require(owner.isNotBlank()) {
                "Tank deletion requires a non-blank owner uid."
            }
        }

        return OwnerTankMutationGate.shared.withTanks(ownerUid, normalizedTankIds) {
            deleteTanksUnderGate(ownerUid, normalizedTankIds)
        }
    }

    private suspend fun deleteTanksUnderGate(
        ownerUid: String,
        normalizedTankIds: List<Long>
    ): Result {
        val snapshotsByTank = try {
            commitTankDeletion(ownerUid, normalizedTankIds)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            return Result.DeleteFailed(error)
        }
        val issues = mutableListOf<CleanupIssue>()
        normalizedTankIds.forEach { tankId ->
            issues += cleanupCommittedTank(ownerUid, tankId, snapshotsByTank[tankId].orEmpty())
        }
        return Result.Deleted(normalizedTankIds, issues)
    }

    private suspend fun commitTankDeletion(
        ownerUid: String,
        normalizedTankIds: List<Long>
    ): Map<Long, List<CareTask>> {
        integrityTransactions.begin(ownerUid, normalizedTankIds)

        val snapshotsByTank = try {
            captureCareSnapshots(ownerUid, normalizedTankIds)
        } catch (error: Throwable) {
            val abortError = withContext(NonCancellable) {
                abortTransactions(ownerUid, normalizedTankIds)
            }
            abortError?.let(error::addSuppressed)
            throw error
        }

        try {
            normalizedTankIds.forEach { tankId ->
                stores.deleteCareTasksForTank(tankId)
            }
            stores.deleteTankRecords(normalizedTankIds)
        } catch (error: Throwable) {
            val rollbackError = withContext(NonCancellable) {
                rollbackCareTasks(
                    ownerUid = ownerUid,
                    snapshotsByTank = snapshotsByTank
                )
            }
            rollbackError?.let(error::addSuppressed)
            throw error
        }

        return snapshotsByTank
    }

    private suspend fun captureCareSnapshots(
        ownerUid: String,
        tankIds: List<Long>
    ): Map<Long, List<CareTask>> {
        val snapshots = linkedMapOf<Long, List<CareTask>>()
        tankIds.forEach { tankId ->
            snapshots[tankId] = stores.snapshotCareTasksForTank(tankId)
        }
        integrityTransactions.captureSnapshots(ownerUid, snapshots)
        return snapshots
    }

    private suspend fun cleanupCommittedTank(
        ownerUid: String,
        tankId: Long,
        tasks: List<CareTask>
    ): List<CleanupIssue> {
        val issues = mutableListOf<CleanupIssue>()
        tasks.forEach { task ->
            try {
                cancelCareTaskReminder(ownerUid, task.id)
            } catch (error: Throwable) {
                error.throwIfCancellation()
                issues += CleanupIssue(tankId, CleanupStage.CARE_TASKS, error)
            }
        }
        completeAnalysisAndJournal(ownerUid, tankId)?.let(issues::add)
        cleanupDeviceAssignments(tankId)?.let(issues::add)
        return issues
    }

    private suspend fun completeAnalysisAndJournal(
        ownerUid: String,
        tankId: Long
    ): CleanupIssue? {
        // Keep the durable deletion pending until the analysis store is clean.
        // Owner-session recovery repeats this idempotent step after a crash.
        val failure = runCatching { stores.deleteWaterAnalysesForTank(tankId) }
            .exceptionOrNull()
        if (failure != null) {
            failure.throwIfCancellation()
            return CleanupIssue(tankId, CleanupStage.WATER_ANALYSES, failure)
        }
        return try {
            integrityTransactions.complete(ownerUid, tankId)
            null
        } catch (error: Throwable) {
            error.throwIfCancellation()
            CleanupIssue(tankId, CleanupStage.CARE_TASKS, error)
        }
    }

    private suspend fun cleanupDeviceAssignments(tankId: Long): CleanupIssue? = try {
        when (val result = removeDeviceAssignmentsForTank(tankId)) {
            is TankAssignmentCleanupResult.Completed -> null
            TankAssignmentCleanupResult.InvalidRequest -> CleanupIssue(
                tankId,
                CleanupStage.DEVICE_ASSIGNMENTS,
                IllegalArgumentException("Tank assignment cleanup received an invalid tank id.")
            )
            is TankAssignmentCleanupResult.Failure -> CleanupIssue(
                tankId,
                CleanupStage.DEVICE_ASSIGNMENTS,
                result.error
            )
        }
    } catch (error: Throwable) {
        error.throwIfCancellation()
        CleanupIssue(tankId, CleanupStage.DEVICE_ASSIGNMENTS, error)
    }

    private suspend fun rollbackCareTasks(
        ownerUid: String,
        snapshotsByTank: Map<Long, List<CareTask>>
    ): Throwable? {
        var rollbackFailure: Throwable? = null

        snapshotsByTank.forEach { (tankId, snapshots) ->
            try {
                integrityTransactions.withRollbackWritesAllowed(
                    ownerUid = ownerUid,
                    tankId = tankId
                ) {
                    stores.restoreCareTasksForTank(tankId, snapshots)
                }
                integrityTransactions.abort(ownerUid, tankId)
            } catch (error: Throwable) {
                if (rollbackFailure == null) {
                    rollbackFailure = error
                } else {
                    rollbackFailure?.addSuppressed(error)
                }
            }
        }

        try {
            reconcileCareReminders(ownerUid)
        } catch (error: Throwable) {
            if (rollbackFailure == null) {
                rollbackFailure = error
            } else {
                rollbackFailure?.addSuppressed(error)
            }
        }

        return rollbackFailure
    }

    private fun abortTransactions(
        ownerUid: String,
        tankIds: List<Long>
    ): Throwable? {
        var abortFailure: Throwable? = null
        tankIds.forEach { tankId ->
            try {
                integrityTransactions.abort(ownerUid, tankId)
            } catch (error: Throwable) {
                if (abortFailure == null) {
                    abortFailure = error
                } else {
                    abortFailure?.addSuppressed(error)
                }
            }
        }
        return abortFailure
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) throw this
    }
}

package com.aqua.aqualight.data.aquarium.delete

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
class OwnerTankDataCleaner internal constructor(
    private val deleteTankRecords: suspend (List<Long>) -> Unit,
    private val snapshotCareTasksForTank: suspend (Long) -> List<CareTask>,
    private val deleteCareTasksForTank: suspend (Long) -> Unit,
    private val restoreCareTasksForTank: suspend (Long, List<CareTask>) -> Unit,
    private val removeDeviceAssignmentsForTank:
        suspend (Long) -> TankAssignmentCleanupResult,
    private val integrityTransactions: TankCareIntegrityTransactions =
        TankCareIntegrityJournal,
    private val ownerUidProvider: () -> String = UserDataScope::requireCurrentUid
) {

    enum class CleanupStage {
        CARE_TASKS,
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

    suspend fun deleteTanks(
        tankIds: Iterable<Long>
    ): Result {
        val normalizedTankIds = tankIds
            .filter { tankId -> tankId > 0L }
            .distinct()

        if (normalizedTankIds.isEmpty()) {
            return Result.NoOp
        }

        val ownerUid = ownerUidProvider().trim().also { owner ->
            require(owner.isNotBlank()) {
                "Tank deletion requires a non-blank owner uid."
            }
        }

        try {
            integrityTransactions.begin(ownerUid, normalizedTankIds)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            return Result.DeleteFailed(error)
        }

        val snapshotsByTank = linkedMapOf<Long, List<CareTask>>()
        try {
            normalizedTankIds.forEach { tankId ->
                snapshotsByTank[tankId] = snapshotCareTasksForTank(tankId)
            }
            integrityTransactions.captureSnapshots(
                ownerUid = ownerUid,
                snapshotsByTank = snapshotsByTank
            )
        } catch (error: Throwable) {
            val abortError = withContext(NonCancellable) {
                abortTransactions(ownerUid, normalizedTankIds)
            }
            abortError?.let(error::addSuppressed)
            error.throwIfCancellation()
            return Result.DeleteFailed(error)
        }

        try {
            normalizedTankIds.forEach { tankId ->
                deleteCareTasksForTank(tankId)
            }
            deleteTankRecords(normalizedTankIds)
        } catch (error: Throwable) {
            val rollbackError = withContext(NonCancellable) {
                rollbackCareTasks(
                    ownerUid = ownerUid,
                    snapshotsByTank = snapshotsByTank
                )
            }
            rollbackError?.let(error::addSuppressed)
            error.throwIfCancellation()
            return Result.DeleteFailed(error)
        }

        val cleanupIssues = mutableListOf<CleanupIssue>()

        normalizedTankIds.forEach { tankId ->
            try {
                integrityTransactions.complete(ownerUid, tankId)
            } catch (error: Throwable) {
                error.throwIfCancellation()
                cleanupIssues += CleanupIssue(
                    tankId = tankId,
                    stage = CleanupStage.CARE_TASKS,
                    error = error
                )
            }

            try {
                when (
                    val result = removeDeviceAssignmentsForTank(tankId)
                ) {
                    is TankAssignmentCleanupResult.Completed -> Unit

                    TankAssignmentCleanupResult.InvalidRequest -> {
                        cleanupIssues += CleanupIssue(
                            tankId = tankId,
                            stage = CleanupStage.DEVICE_ASSIGNMENTS,
                            error = IllegalArgumentException(
                                "Tank assignment cleanup received an invalid tank id."
                            )
                        )
                    }

                    is TankAssignmentCleanupResult.Failure -> {
                        cleanupIssues += CleanupIssue(
                            tankId = tankId,
                            stage = CleanupStage.DEVICE_ASSIGNMENTS,
                            error = result.error
                        )
                    }
                }
            } catch (error: Throwable) {
                error.throwIfCancellation()
                cleanupIssues += CleanupIssue(
                    tankId = tankId,
                    stage = CleanupStage.DEVICE_ASSIGNMENTS,
                    error = error
                )
            }
        }

        return Result.Deleted(
            tankIds = normalizedTankIds,
            cleanupIssues = cleanupIssues.toList()
        )
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
                    restoreCareTasksForTank(tankId, snapshots)
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
        if (this is CancellationException) {
            throw this
        }
    }
}
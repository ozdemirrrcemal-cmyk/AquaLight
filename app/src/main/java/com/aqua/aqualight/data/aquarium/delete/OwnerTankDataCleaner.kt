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

/**
 * Coordinates authoritative tank deletion with crash-safe compensating transactions.
 *
 * Care and Health writes are blocked before snapshots are captured. Dependent records
 * are deleted before the tank record. If the tank write fails or the operation is
 * cancelled, both snapshot sets are restored before the failure is returned. Durable
 * journals allow owner-session recovery after process death.
 */
class OwnerTankDataCleaner internal constructor(
    private val deleteTankRecords: suspend (List<Long>) -> Unit,
    private val snapshotCareTasksForTank: suspend (Long) -> List<CareTask>,
    private val deleteCareTasksForTank: suspend (Long) -> Unit,
    private val restoreCareTasksForTank: suspend (Long, List<CareTask>) -> Unit,
    private val snapshotHealthRecordsForTank:
        suspend (String, Long) -> TankHealthIntegritySnapshot,
    private val deleteHealthRecordsForTank: suspend (String, Long) -> Unit,
    private val restoreHealthRecordsForTank:
        suspend (String, Long, TankHealthIntegritySnapshot) -> Unit,
    private val removeDeviceAssignmentsForTank:
        suspend (Long) -> TankAssignmentCleanupResult,
    private val cancelCareTaskReminder: suspend (String, Long) -> Unit,
    private val reconcileCareReminders: suspend (String) -> Unit,
    private val careIntegrityTransactions: TankCareIntegrityTransactions =
        TankCareIntegrityJournal,
    private val healthIntegrityTransactions: TankHealthIntegrityTransactions =
        TankHealthIntegrityJournal,
    private val ownerUidProvider: () -> String = UserDataScope::requireCurrentUid
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

        if (normalizedTankIds.isEmpty()) return Result.NoOp

        val ownerUid = ownerUidProvider().trim().also { owner ->
            require(owner.isNotBlank()) {
                "Tank deletion requires a non-blank owner uid."
            }
        }

        val beginFailure = beginTransactions(ownerUid, normalizedTankIds)
        if (beginFailure != null) {
            beginFailure.throwIfCancellation()
            return Result.DeleteFailed(beginFailure)
        }

        val careSnapshotsByTank = linkedMapOf<Long, List<CareTask>>()
        val healthSnapshotsByTank =
            linkedMapOf<Long, TankHealthIntegritySnapshot>()

        try {
            normalizedTankIds.forEach { tankId ->
                careSnapshotsByTank[tankId] =
                    snapshotCareTasksForTank(tankId)
                healthSnapshotsByTank[tankId] =
                    snapshotHealthRecordsForTank(ownerUid, tankId)
            }
            careIntegrityTransactions.captureSnapshots(
                ownerUid = ownerUid,
                snapshotsByTank = careSnapshotsByTank
            )
            healthIntegrityTransactions.captureSnapshots(
                ownerUid = ownerUid,
                snapshotsByTank = healthSnapshotsByTank
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
                deleteHealthRecordsForTank(ownerUid, tankId)
            }
            deleteTankRecords(normalizedTankIds)
        } catch (error: Throwable) {
            val rollbackError = withContext(NonCancellable) {
                rollbackDependentData(
                    ownerUid = ownerUid,
                    careSnapshotsByTank = careSnapshotsByTank,
                    healthSnapshotsByTank = healthSnapshotsByTank
                )
            }
            rollbackError?.let(error::addSuppressed)
            error.throwIfCancellation()
            return Result.DeleteFailed(error)
        }

        val cleanupIssues = mutableListOf<CleanupIssue>()

        normalizedTankIds.forEach { tankId ->
            careSnapshotsByTank[tankId].orEmpty().forEach { task ->
                try {
                    cancelCareTaskReminder(ownerUid, task.id)
                } catch (error: Throwable) {
                    error.throwIfCancellation()
                    cleanupIssues += CleanupIssue(
                        tankId = tankId,
                        stage = CleanupStage.CARE_TASKS,
                        error = error
                    )
                }
            }

            completeIntegrityTransaction(
                ownerUid = ownerUid,
                tankId = tankId,
                cleanupIssues = cleanupIssues
            )

            try {
                when (val result = removeDeviceAssignmentsForTank(tankId)) {
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

    private fun beginTransactions(
        ownerUid: String,
        tankIds: List<Long>
    ): Throwable? {
        return try {
            careIntegrityTransactions.begin(ownerUid, tankIds)
            try {
                healthIntegrityTransactions.begin(ownerUid, tankIds)
                null
            } catch (healthError: Throwable) {
                val careAbort = abortCareTransactions(ownerUid, tankIds)
                careAbort?.let(healthError::addSuppressed)
                healthError
            }
        } catch (error: Throwable) {
            error
        }
    }

    private fun completeIntegrityTransaction(
        ownerUid: String,
        tankId: Long,
        cleanupIssues: MutableList<CleanupIssue>
    ) {
        try {
            careIntegrityTransactions.complete(ownerUid, tankId)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            cleanupIssues += CleanupIssue(
                tankId = tankId,
                stage = CleanupStage.CARE_TASKS,
                error = error
            )
        }

        try {
            healthIntegrityTransactions.complete(ownerUid, tankId)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            cleanupIssues += CleanupIssue(
                tankId = tankId,
                stage = CleanupStage.HEALTH_RECORDS,
                error = error
            )
        }
    }

    private suspend fun rollbackDependentData(
        ownerUid: String,
        careSnapshotsByTank: Map<Long, List<CareTask>>,
        healthSnapshotsByTank: Map<Long, TankHealthIntegritySnapshot>
    ): Throwable? {
        var rollbackFailure: Throwable? = null

        careSnapshotsByTank.forEach { (tankId, snapshots) ->
            try {
                careIntegrityTransactions.withRollbackWritesAllowed(
                    ownerUid = ownerUid,
                    tankId = tankId
                ) {
                    restoreCareTasksForTank(tankId, snapshots)
                }
            } catch (error: Throwable) {
                rollbackFailure = rollbackFailure.combine(error)
            }
        }

        healthSnapshotsByTank.forEach { (tankId, snapshot) ->
            try {
                healthIntegrityTransactions.withRollbackWritesAllowed(
                    ownerUid = ownerUid,
                    tankId = tankId
                ) {
                    restoreHealthRecordsForTank(
                        ownerUid,
                        tankId,
                        snapshot
                    )
                }
            } catch (error: Throwable) {
                rollbackFailure = rollbackFailure.combine(error)
            }
        }

        val abortFailure = abortTransactions(
            ownerUid = ownerUid,
            tankIds = careSnapshotsByTank.keys.toList()
        )
        if (abortFailure != null) {
            rollbackFailure = rollbackFailure.combine(abortFailure)
        }

        try {
            reconcileCareReminders(ownerUid)
        } catch (error: Throwable) {
            rollbackFailure = rollbackFailure.combine(error)
        }

        return rollbackFailure
    }

    private fun abortTransactions(
        ownerUid: String,
        tankIds: List<Long>
    ): Throwable? {
        var failure: Throwable? = null
        tankIds.forEach { tankId ->
            try {
                careIntegrityTransactions.abort(ownerUid, tankId)
            } catch (error: Throwable) {
                failure = failure.combine(error)
            }
            try {
                healthIntegrityTransactions.abort(ownerUid, tankId)
            } catch (error: Throwable) {
                failure = failure.combine(error)
            }
        }
        return failure
    }

    private fun abortCareTransactions(
        ownerUid: String,
        tankIds: List<Long>
    ): Throwable? {
        var failure: Throwable? = null
        tankIds.forEach { tankId ->
            try {
                careIntegrityTransactions.abort(ownerUid, tankId)
            } catch (error: Throwable) {
                failure = failure.combine(error)
            }
        }
        return failure
    }

    private fun Throwable?.combine(error: Throwable): Throwable {
        val current = this
        return if (current == null) {
            error
        } else {
            current.addSuppressed(error)
            current
        }
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) throw this
    }
}

package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.OwnerTankMutationGate
import com.aqua.aqualight.data.aquarium.OwnerArchiveMutationGate
import com.aqua.aqualight.data.aquarium.health.WaterAnalysisDeletionIntegrity
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
 * Writes are blocked before care and water snapshots are durably captured. Both
 * histories are deleted before the tank record. If that write fails or is cancelled,
 * both snapshots are restored before resolving the journal. Water payloads remain
 * in bounded Room staging; the small journal retains only their transaction UUID.
 */
internal data class OwnerTankDeletionStores(
    val deleteTankRecords: suspend (List<Long>) -> Unit,
    val snapshotCareTasksForTank: suspend (Long) -> List<CareTask>,
    val deleteCareTasksForTank: suspend (Long) -> Unit,
    val restoreCareTasksForTank: suspend (Long, List<CareTask>) -> Unit,
    val deleteWaterAnalysesForTank: suspend (Long) -> Unit,
    val waterIntegrity: WaterAnalysisDeletionIntegrity,
    val requireArchiveSettled: suspend (String) -> Unit
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

        return OwnerArchiveMutationGate.shared.withOwner(ownerUid) {
            OwnerTankMutationGate.shared.withTanks(ownerUid, normalizedTankIds) {
                deleteTanksUnderGate(ownerUid, normalizedTankIds)
            }
        }
    }

    private suspend fun deleteTanksUnderGate(
        ownerUid: String,
        normalizedTankIds: List<Long>
    ): Result {
        val snapshotsByTank = try {
            stores.requireArchiveSettled(ownerUid)
            commitTankDeletion(ownerUid, normalizedTankIds)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            return Result.DeleteFailed(error)
        }
        val issues = mutableListOf<CleanupIssue>()
        normalizedTankIds.forEach { tankId ->
            issues += cleanupCommittedTank(ownerUid, tankId, snapshotsByTank.tasks[tankId].orEmpty(),
                checkNotNull(snapshotsByTank.waterTransactions[tankId]))
        }
        return Result.Deleted(normalizedTankIds, issues)
    }

    private suspend fun commitTankDeletion(
        ownerUid: String,
        normalizedTankIds: List<Long>
    ): TankDeletionSnapshots {
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
                stores.deleteWaterAnalysesForTank(tankId)
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
    ): TankDeletionSnapshots {
        val snapshots = linkedMapOf<Long, List<CareTask>>()
        val waterTransactions = linkedMapOf<Long, String>()
        tankIds.forEach { tankId ->
            snapshots[tankId] = stores.snapshotCareTasksForTank(tankId)
            waterTransactions[tankId] = stores.waterIntegrity.prepare(tankId)
        }
        integrityTransactions.captureSnapshots(ownerUid, snapshots, waterTransactions)
        return TankDeletionSnapshots(snapshots, waterTransactions)
    }

    private suspend fun cleanupCommittedTank(
        ownerUid: String,
        tankId: Long,
        tasks: List<CareTask>,
        waterTransaction: String
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
        completeAnalysisAndJournal(ownerUid, tankId, waterTransaction)?.let(issues::add)
        cleanupDeviceAssignments(tankId)?.let(issues::add)
        return issues
    }

    private suspend fun completeAnalysisAndJournal(
        ownerUid: String,
        tankId: Long,
        waterTransaction: String
    ): CleanupIssue? {
        return try {
            integrityTransactions.complete(ownerUid, tankId)
            stores.waterIntegrity.finish(tankId, waterTransaction)
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
        snapshotsByTank: TankDeletionSnapshots
    ): Throwable? {
        var rollbackFailure: Throwable? = null

        snapshotsByTank.tasks.forEach { (tankId, snapshots) ->
            try {
                integrityTransactions.withRollbackWritesAllowed(
                    ownerUid = ownerUid,
                    tankId = tankId
                ) {
                    stores.restoreCareTasksForTank(tankId, snapshots)
                    stores.waterIntegrity.restore(tankId, checkNotNull(snapshotsByTank.waterTransactions[tankId]))
                }
                integrityTransactions.abort(ownerUid, tankId)
                stores.waterIntegrity.finish(tankId, snapshotsByTank.waterTransactions[tankId])
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

    private suspend fun abortTransactions(
        ownerUid: String,
        tankIds: List<Long>
    ): Throwable? {
        var abortFailure: Throwable? = null
        tankIds.forEach { tankId ->
            try {
                integrityTransactions.abort(ownerUid, tankId)
                stores.waterIntegrity.finish(tankId)
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

private data class TankDeletionSnapshots(
    val tasks: Map<Long, List<CareTask>>,
    val waterTransactions: Map<Long, String>
)

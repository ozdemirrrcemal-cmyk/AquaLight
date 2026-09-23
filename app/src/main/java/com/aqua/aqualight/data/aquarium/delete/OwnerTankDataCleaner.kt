package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityJournal
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityTransactions
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityJournal
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityTransactions
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.user.UserDataScope

internal data class TankCareDeletionDependencies(
    val snapshotForTank: suspend (Long) -> List<CareTask>,
    val deleteForTank: suspend (Long) -> Unit,
    val restoreForTank:
        suspend (Long, List<CareTask>) -> Unit,
    val cancelCareTaskReminder:
        suspend (String, Long) -> Unit,
    val reconcileReminders: suspend (String) -> Unit,
    val integrity: TankCareIntegrityTransactions =
        TankCareIntegrityJournal
)

internal data class TankHealthDeletionDependencies(
    val snapshotForTank:
        suspend (String, Long) -> TankHealthIntegritySnapshot,
    val deleteForTank: suspend (String, Long) -> Unit,
    val restoreForTank:
        suspend (
            String,
            Long,
            TankHealthIntegritySnapshot
        ) -> Unit,
    val integrity: TankHealthIntegrityTransactions =
        TankHealthIntegrityJournal
)

internal data class OwnerTankDeletionDependencies(
    val deleteTankRecords: suspend (List<Long>) -> Unit,
    val care: TankCareDeletionDependencies,
    val health: TankHealthDeletionDependencies,
    val removeDeviceAssignmentsForTank:
        suspend (Long) -> TankAssignmentCleanupResult,
    val ownerUidProvider: () -> String =
        UserDataScope::requireCurrentUid
)

/**
 * Coordinates one authoritative tank deletion transaction.
 *
 * Store-specific snapshot, rollback and cleanup behavior is delegated to
 * focused collaborators. This type owns only request normalization and the
 * transaction entry point.
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

    suspend fun deleteTanks(
        tankIds: Iterable<Long>
    ): Result {
        val normalizedTankIds = tankIds
            .filter { tankId -> tankId > 0L }
            .distinct()

        return if (normalizedTankIds.isEmpty()) {
            Result.NoOp
        } else {
            OwnerTankDeletionCoordinator(
                dependencies = dependencies,
                ownerUid = requireTankDeletionOwnerUid(
                    dependencies.ownerUidProvider()
                ),
                tankIds = normalizedTankIds
            ).delete()
        }
    }
}

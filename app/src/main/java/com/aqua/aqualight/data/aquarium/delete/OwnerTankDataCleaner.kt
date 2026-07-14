package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult
import java.util.concurrent.CancellationException

/**
 * Coordinates authoritative tank deletion with best-effort dependent cleanup.
 *
 * The tank store is the primary record. Once it is durably deleted, cleanup
 * failures must never be reported as if the tank still exists. Instead every
 * dependent cleanup is attempted and explicit recovery diagnostics are returned.
 */
class OwnerTankDataCleaner internal constructor(
    private val deleteTankRecords: suspend (List<Long>) -> Unit,
    private val deleteCareTasksForTank: suspend (Long) -> Unit,
    private val removeDeviceAssignmentsForTank:
        suspend (Long) -> TankAssignmentCleanupResult
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

        try {
            deleteTankRecords(normalizedTankIds)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            return Result.DeleteFailed(error)
        }

        val cleanupIssues = mutableListOf<CleanupIssue>()

        normalizedTankIds.forEach { tankId ->
            try {
                deleteCareTasksForTank(tankId)
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

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }
}

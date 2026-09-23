package com.aqua.aqualight.data.aquarium.delete

import com.aqua.aqualight.data.aquarium.devices.TankAssignmentCleanupResult

internal class OwnerTankDeletionCompletion(
    private val dependencies: OwnerTankDeletionDependencies,
    private val ownerUid: String,
    private val tankIds: List<Long>,
    private val snapshots: TankDeletionSnapshots
) {

    suspend fun finish():
        OwnerTankDataCleaner.Result.Deleted {
        val issues =
            mutableListOf<OwnerTankDataCleaner.CleanupIssue>()

        tankIds.forEach { tankId ->
            cancelCareReminders(tankId, issues)
            completeIntegrity(tankId, issues)
            cleanupAssignments(tankId, issues)
        }

        return OwnerTankDataCleaner.Result.Deleted(
            tankIds = tankIds,
            cleanupIssues = issues.toList()
        )
    }

    private suspend fun cancelCareReminders(
        tankId: Long,
        issues:
            MutableList<OwnerTankDataCleaner.CleanupIssue>
    ) {
        snapshots.careByTank[tankId]
            .orEmpty()
            .forEach { task ->
                val error = runCatching {
                    dependencies.care
                        .cancelCareTaskReminder(
                            ownerUid,
                            task.id
                        )
                }.exceptionOrNull()
                error?.throwIfTankDeletionCancellation()
                error?.let { failure ->
                    issues += tankDeletionCleanupIssue(
                        tankId = tankId,
                        stage =
                            OwnerTankDataCleaner.CleanupStage
                                .CARE_TASKS,
                        error = failure
                    )
                }
            }
    }

    private fun completeIntegrity(
        tankId: Long,
        issues:
            MutableList<OwnerTankDataCleaner.CleanupIssue>
    ) {
        completeOne(
            tankId = tankId,
            stage =
                OwnerTankDataCleaner.CleanupStage.CARE_TASKS,
            complete =
                dependencies.care.integrity::complete,
            issues = issues
        )
        completeOne(
            tankId = tankId,
            stage =
                OwnerTankDataCleaner.CleanupStage
                    .HEALTH_RECORDS,
            complete =
                dependencies.health.integrity::complete,
            issues = issues
        )
    }

    private fun completeOne(
        tankId: Long,
        stage: OwnerTankDataCleaner.CleanupStage,
        complete: (String, Long) -> Unit,
        issues:
            MutableList<OwnerTankDataCleaner.CleanupIssue>
    ) {
        val error = runCatching {
            complete(ownerUid, tankId)
        }.exceptionOrNull()
        error?.throwIfTankDeletionCancellation()
        error?.let { failure ->
            issues += tankDeletionCleanupIssue(
                tankId,
                stage,
                failure
            )
        }
    }

    private suspend fun cleanupAssignments(
        tankId: Long,
        issues:
            MutableList<OwnerTankDataCleaner.CleanupIssue>
    ) {
        val result = runCatching {
            dependencies.removeDeviceAssignmentsForTank(
                tankId
            )
        }
        val error = result.exceptionOrNull()
        error?.throwIfTankDeletionCancellation()

        if (error != null) {
            issues += tankDeletionCleanupIssue(
                tankId,
                OwnerTankDataCleaner.CleanupStage
                    .DEVICE_ASSIGNMENTS,
                error
            )
        } else {
            appendAssignmentResult(
                tankId = tankId,
                result = result.getOrThrow(),
                issues = issues
            )
        }
    }

    private fun appendAssignmentResult(
        tankId: Long,
        result: TankAssignmentCleanupResult,
        issues:
            MutableList<OwnerTankDataCleaner.CleanupIssue>
    ) {
        when (result) {
            is TankAssignmentCleanupResult.Completed -> Unit

            TankAssignmentCleanupResult.InvalidRequest ->
                issues += tankDeletionCleanupIssue(
                    tankId = tankId,
                    stage =
                        OwnerTankDataCleaner.CleanupStage
                            .DEVICE_ASSIGNMENTS,
                    error = IllegalArgumentException(
                        "Tank assignment cleanup received " +
                            "an invalid tank id."
                    )
                )

            is TankAssignmentCleanupResult.Failure ->
                issues += tankDeletionCleanupIssue(
                    tankId = tankId,
                    stage =
                        OwnerTankDataCleaner.CleanupStage
                            .DEVICE_ASSIGNMENTS,
                    error = result.error
                )
        }
    }
}

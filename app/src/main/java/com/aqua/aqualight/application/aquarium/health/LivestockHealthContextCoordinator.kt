package com.aqua.aqualight.application.aquarium.health

import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.application.care.MaintenanceOperations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LivestockHealthContextCoordinator(
    private val waterAnalysisOperations: WaterAnalysisOperations,
    private val maintenanceOperations: MaintenanceOperations
) : LivestockHealthContextOperations {

    override fun contextForTank(tankId: Long): Flow<LivestockHealthContextSnapshot> {
        require(tankId > 0L) { "tankId must be positive." }
        return combine(
            waterAnalysisOperations.analysesForTank(tankId),
            maintenanceOperations.tasks
        ) { analyses, tasks ->
            LivestockHealthContextSnapshot(
                latestWaterAnalysis = analyses.maxWithOrNull(
                    compareBy<WaterAnalysisSnapshot> { analysis -> analysis.measuredAtMillis }
                        .thenBy { analysis -> analysis.id }
                ),
                lastWaterChangeAtMillis = tasks.latestCompletedWaterChangeAt(tankId)
            )
        }
    }
}

private fun List<CareTaskSnapshot>.latestCompletedWaterChangeAt(tankId: Long): Long? =
    asSequence()
        .filter { task ->
            task.tankId == tankId &&
                task.type == CareTaskType.WATER_CHANGE &&
                task.status == CareTaskStatus.COMPLETED
        }
        .mapNotNull(CareTaskSnapshot::completedAtMillis)
        .maxOrNull()

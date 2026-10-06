package com.aqua.aqualight.application.aquarium.health

import kotlinx.coroutines.flow.Flow

data class LivestockHealthContextSnapshot(
    val latestWaterAnalysis: WaterAnalysisSnapshot?,
    val lastWaterChangeAtMillis: Long?
)

interface LivestockHealthContextOperations {
    fun contextForTank(tankId: Long): Flow<LivestockHealthContextSnapshot>
}

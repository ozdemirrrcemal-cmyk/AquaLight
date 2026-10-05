package com.aqua.aqualight.application.aquarium.health

import kotlinx.coroutines.flow.Flow

const val LIVESTOCK_HEALTH_MAX_PHOTOS = 3

data class LivestockObservationInput(
    val requestId: String,
    val tankId: Long,
    val livestockId: Long,
    val symptomKeys: List<String>,
    val onsetKey: String,
    val otherObservation: String,
    val note: String,
    val photoUris: List<String>,
    val affectedCount: Int
)

data class LivestockCheckInput(
    val requestId: String,
    val status: String,
    val affectedCount: Int,
    val checkedAtMillis: Long,
    val note: String,
    val photoUris: List<String>
)

data class LivestockCheckSnapshot(
    val status: String,
    val affectedCount: Int,
    val checkedAtMillis: Long,
    val note: String,
    val photoUris: List<String>
)

enum class LivestockEvaluationTrigger {
    INITIAL_OBSERVATION,
    USER_REFRESH
}

data class LivestockEvaluationInput(
    val requestId: String,
    val trigger: LivestockEvaluationTrigger,
    val waterAnalysis: WaterAnalysisSnapshot?
)

data class LivestockEvaluationSnapshot(
    val id: Long,
    val evaluatedAtMillis: Long,
    val trigger: LivestockEvaluationTrigger,
    val affectedCount: Int,
    val basedOnCheckCount: Int,
    val basedOnLatestCheckAtMillis: Long?,
    val waterAnalysis: WaterAnalysisSnapshot?
)

data class LivestockObservationSnapshot(
    val id: Long,
    val tankId: Long,
    val livestockId: Long,
    val symptomKeys: List<String>,
    val onsetKey: String,
    val otherObservation: String,
    val note: String,
    val photoUris: List<String>,
    val affectedCount: Int,
    val totalCount: Int,
    val createdAtMillis: Long,
    val closedAtMillis: Long?,
    val closeReason: String?,
    val checks: List<LivestockCheckSnapshot>,
    val evaluations: List<LivestockEvaluationSnapshot>
) {
    val currentAffectedCount: Int
        get() = checks.maxByOrNull { it.checkedAtMillis }?.affectedCount ?: affectedCount

    val latestEvaluation: LivestockEvaluationSnapshot?
        get() = evaluations.maxByOrNull { evaluation -> evaluation.evaluatedAtMillis }

    fun isEvaluationStale(latestWaterAnalysis: WaterAnalysisSnapshot?): Boolean {
        val evaluation = latestEvaluation ?: return true
        val latestCheckAtMillis = checks.maxOfOrNull { check -> check.checkedAtMillis }
        return evaluation.affectedCount != currentAffectedCount ||
            evaluation.basedOnCheckCount != checks.size ||
            evaluation.basedOnLatestCheckAtMillis != latestCheckAtMillis ||
            evaluation.waterAnalysis?.id != latestWaterAnalysis?.id
    }
}

interface LivestockHealthOperations {
    fun observationsForTank(tankId: Long): Flow<List<LivestockObservationSnapshot>>
    suspend fun createObservation(
        input: LivestockObservationInput,
        evaluation: LivestockEvaluationInput
    ): Long
    suspend fun addEvaluation(
        tankId: Long,
        observationId: Long,
        input: LivestockEvaluationInput
    )
    suspend fun addCheck(tankId: Long, observationId: Long, input: LivestockCheckInput)
    suspend fun closeObservation(tankId: Long, observationId: Long, reason: String)
}

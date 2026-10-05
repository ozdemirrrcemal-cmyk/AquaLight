package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.lifecycle.SavedStateHandle
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot

internal sealed interface LivestockEvaluationSaveResult {
    data class FollowUpStarted(
        val observationId: Long,
        val affectedCount: Int
    ) : LivestockEvaluationSaveResult

    data object Refreshed : LivestockEvaluationSaveResult
}

internal data class LivestockEvaluationSaveRoute(
    val tankId: Long,
    val livestockId: Long,
    val symptomKey: String,
    val affectedCount: Int
)

internal data class LivestockEvaluationSaveContext(
    val record: LivestockObservationSnapshot?,
    val draft: SavedStateHandle?,
    val route: LivestockEvaluationSaveRoute,
    val latestWaterAnalysis: WaterAnalysisSnapshot?
)

internal suspend fun saveLivestockEvaluation(
    healthViewModel: LivestockHealthViewModel,
    context: LivestockEvaluationSaveContext
): LivestockEvaluationSaveResult? =
    context.record?.let { record ->
        healthViewModel.addEvaluation(
            tankId = context.route.tankId,
            observationId = record.id,
            input = refreshLivestockEvaluationInput(
                record = record,
                waterAnalysis = context.latestWaterAnalysis
            )
        )
        LivestockEvaluationSaveResult.Refreshed
    } ?: saveInitialLivestockEvaluation(healthViewModel, context)

private suspend fun saveInitialLivestockEvaluation(
    healthViewModel: LivestockHealthViewModel,
    context: LivestockEvaluationSaveContext
): LivestockEvaluationSaveResult? {
    val state = context.draft
    val observationInput = state?.toLivestockObservationInput(
        tankId = context.route.tankId,
        livestockId = context.route.livestockId,
        symptomKey = context.route.symptomKey,
        affectedCount = context.route.affectedCount
    )
    return if (state == null || observationInput == null) {
        null
    } else {
        val evaluationInput = initialLivestockEvaluationInput(
            observationRequestId = observationInput.requestId,
            waterAnalysis = context.latestWaterAnalysis
        )
        state[LivestockHealthObservationFragment.DRAFT_SAVING] = true
        try {
            val observationId = healthViewModel.create(
                input = observationInput,
                evaluation = evaluationInput
            )
            state[LivestockHealthObservationFragment.DRAFT_COMMITTED] = true
            LivestockEvaluationSaveResult.FollowUpStarted(
                observationId = observationId,
                affectedCount = observationInput.affectedCount
            )
        } finally {
            state[LivestockHealthObservationFragment.DRAFT_SAVING] = false
        }
    }
}

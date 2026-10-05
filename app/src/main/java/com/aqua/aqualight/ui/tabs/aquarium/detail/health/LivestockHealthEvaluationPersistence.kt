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

internal suspend fun saveLivestockEvaluation(
    healthViewModel: LivestockHealthViewModel,
    record: LivestockObservationSnapshot?,
    draft: SavedStateHandle?,
    tankId: Long,
    livestockId: Long,
    symptomKey: String,
    affectedCount: Int,
    latestWaterAnalysis: WaterAnalysisSnapshot?
): LivestockEvaluationSaveResult? {
    if (record != null) {
        healthViewModel.addEvaluation(
            tankId = tankId,
            observationId = record.id,
            input = refreshLivestockEvaluationInput(
                record = record,
                waterAnalysis = latestWaterAnalysis
            )
        )
        return LivestockEvaluationSaveResult.Refreshed
    }

    val state = draft ?: return null
    val observationInput = state.toLivestockObservationInput(
        tankId = tankId,
        livestockId = livestockId,
        symptomKey = symptomKey,
        affectedCount = affectedCount
    ) ?: return null
    val evaluationInput = initialLivestockEvaluationInput(
        observationRequestId = observationInput.requestId,
        waterAnalysis = latestWaterAnalysis
    )

    state[LivestockHealthObservationFragment.DRAFT_SAVING] = true
    return try {
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

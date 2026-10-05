package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationTrigger
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot

internal fun initialLivestockEvaluationInput(
    observationRequestId: String,
    waterAnalysis: WaterAnalysisSnapshot?
): LivestockEvaluationInput =
    LivestockEvaluationInput(
        requestId = observationRequestId,
        trigger = LivestockEvaluationTrigger.INITIAL_OBSERVATION,
        waterAnalysis = waterAnalysis
    )

internal fun refreshLivestockEvaluationInput(
    record: LivestockObservationSnapshot,
    waterAnalysis: WaterAnalysisSnapshot?
): LivestockEvaluationInput =
    LivestockEvaluationInput(
        requestId = buildString {
            append("eval-refresh-")
            append(record.id)
            append('-')
            append(record.checks.size)
            append('-')
            append(waterAnalysis?.id ?: 0L)
        },
        trigger = LivestockEvaluationTrigger.USER_REFRESH,
        waterAnalysis = waterAnalysis
    )

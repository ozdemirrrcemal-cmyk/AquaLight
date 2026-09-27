package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContextProvider
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContextResult
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessmentEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun interface WaterAnalysisEvaluationPreparation {
    suspend fun prepare(input: WaterAnalysisInput): StoredWaterEvaluation
}

internal class DefaultWaterAnalysisEvaluationPreparation(
    private val contextProvider: AquariumHealthContextProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : WaterAnalysisEvaluationPreparation {
    override suspend fun prepare(input: WaterAnalysisInput): StoredWaterEvaluation = withContext(dispatcher) {
        val result = contextProvider.capture(input.tankId)
        check(result is AquariumHealthContextResult.Available) { "Water-analysis context is unavailable." }
        WaterEvaluationCodec.encode(input, result.context,
            WaterQualityAssessmentEngine.assess(input, result.context))
    }
}

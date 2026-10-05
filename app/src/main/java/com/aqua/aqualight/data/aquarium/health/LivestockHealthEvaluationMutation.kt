package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal suspend fun LivestockHealthDataStoreManager.addEvaluation(
    tankId: Long,
    observationId: Long,
    input: LivestockEvaluationInput
) = appendLivestockEvaluation(
    tankId = tankId,
    observationId = observationId,
    input = input,
    mutate = ::mutateRecord
)

private suspend fun appendLivestockEvaluation(
    tankId: Long,
    observationId: Long,
    input: LivestockEvaluationInput,
    mutate: suspend (
        Long,
        Long,
        (StoredLivestockObservation) -> StoredLivestockObservation
    ) -> Unit
) = withContext(NonCancellable + Dispatchers.IO) {
    require(tankId > 0L && observationId > 0L)
    require(input.trigger == LivestockEvaluationTrigger.USER_REFRESH)
    mutate(tankId, observationId) { record ->
        val existing = record.evaluationsList.firstOrNull {
            it.requestId == input.requestId
        }
        if (existing != null) {
            require(existing.matchesInput(record, input)) {
                "Evaluation request was already used for different data."
            }
            record
        } else {
            require(record.closedAtMillis == 0L) {
                "Closed follow-up cannot be re-evaluated."
            }
            appendNewEvaluation(record, input)
        }
    }
}

private fun appendNewEvaluation(
    record: StoredLivestockObservation,
    input: LivestockEvaluationInput
): StoredLivestockObservation {
    val now = System.currentTimeMillis()
    val latestCheckAt = record.checksList.maxOfOrNull { it.checkedAtMillis } ?: 0L
    val latestEvaluationAt =
        record.evaluationsList.maxOfOrNull { it.evaluatedAtMillis } ?: 0L
    val evaluatedAt = maxOf(
        now,
        record.createdAtMillis,
        latestCheckAt,
        latestEvaluationAt + 1L
    )
    val maxEvaluationId = record.evaluationsList.maxOfOrNull { it.id } ?: 0L
    check(maxEvaluationId < Long.MAX_VALUE)
    val evaluationId = maxOf(evaluatedAt, maxEvaluationId + 1L)
    return record.toBuilder()
        .addEvaluations(
            buildStoredEvaluation(
                id = evaluationId,
                record = record,
                input = input,
                evaluatedAtMillis = evaluatedAt
            )
        )
        .build()
}

package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationTrigger
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal const val MIN_LIVESTOCK_EVALUATION_TIME_MILLIS = 946_684_800_000L
internal const val MAX_LIVESTOCK_EVALUATION_TIME_MILLIS = 4_102_444_800_000L
private const val MAX_EVALUATION_REQUEST_ID_LENGTH = 64
private const val MAX_EVALUATION_WATER_MEASUREMENTS = 32
private const val MIN_EVALUATION_TEMPERATURE_C = -50.0
private const val MAX_EVALUATION_TEMPERATURE_C = 100.0

internal fun validateStoredEvaluation(
    record: StoredLivestockObservation,
    evaluation: StoredLivestockEvaluation
) {
    validateEvaluationIdentity(evaluation)
    requireEvaluationEnum<LivestockEvaluationTrigger>(
        evaluation.trigger,
        "evaluation.trigger"
    )
    if (evaluation.basedOnCheckCount !in 0..record.checksCount) {
        invalidEvaluation()
    }

    val basedOnChecks = record.checksList.take(evaluation.basedOnCheckCount)
    val latestCheck = basedOnChecks.maxByOrNull { check -> check.checkedAtMillis }
    validateEvaluationBasis(
        record = record,
        evaluation = evaluation,
        expectedAffected = latestCheck?.affectedCount ?: record.affectedCount,
        expectedLatestCheckAt = latestCheck?.checkedAtMillis ?: 0L
    )
    validateStoredWaterSnapshot(evaluation)
}

internal fun validateEvaluationInput(
    record: StoredLivestockObservation,
    input: LivestockEvaluationInput
) {
    require(input.requestId.length in 1..MAX_EVALUATION_REQUEST_ID_LENGTH)
    input.waterAnalysis?.let { water ->
        require(water.id > 0L && water.tankId == record.tankId)
        require(
            water.measuredAtMillis in
                MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS
        )
        require(
            water.createdAtMillis in
                MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS
        )
        require(water.measurements.size <= MAX_EVALUATION_WATER_MEASUREMENTS)
    }
}

private fun validateEvaluationIdentity(evaluation: StoredLivestockEvaluation) {
    if (evaluation.id <= 0L) invalidEvaluation()
    if (evaluation.requestId.length !in 1..MAX_EVALUATION_REQUEST_ID_LENGTH) {
        invalidEvaluation()
    }
    if (
        evaluation.evaluatedAtMillis !in
        MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS
    ) {
        invalidEvaluation()
    }
}

private fun validateEvaluationBasis(
    record: StoredLivestockObservation,
    evaluation: StoredLivestockEvaluation,
    expectedAffected: Int,
    expectedLatestCheckAt: Long
) {
    if (evaluation.affectedCount != expectedAffected) invalidEvaluation()
    if (evaluation.basedOnLatestCheckAtMillis != expectedLatestCheckAt) {
        invalidEvaluation()
    }
    if (evaluation.evaluatedAtMillis < record.createdAtMillis) invalidEvaluation()
    if (evaluation.evaluatedAtMillis < expectedLatestCheckAt) invalidEvaluation()
}

private fun validateStoredWaterSnapshot(evaluation: StoredLivestockEvaluation) {
    if (evaluation.hasWaterAnalysis) {
        validatePresentWaterSnapshot(evaluation)
    } else {
        validateAbsentWaterSnapshot(evaluation)
    }
}

private fun validateAbsentWaterSnapshot(evaluation: StoredLivestockEvaluation) {
    if (evaluation.waterAnalysisId != 0L) invalidEvaluation()
    if (evaluation.waterAnalysisMeasuredAtMillis != 0L) invalidEvaluation()
    if (evaluation.waterAnalysisCreatedAtMillis != 0L) invalidEvaluation()
    if (evaluation.hasTemperature) invalidEvaluation()
    if (evaluation.waterMeasurementsCount != 0) invalidEvaluation()
}

private fun validatePresentWaterSnapshot(evaluation: StoredLivestockEvaluation) {
    if (evaluation.waterAnalysisId <= 0L) invalidEvaluation()
    if (
        evaluation.waterAnalysisMeasuredAtMillis !in
        MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS
    ) {
        invalidEvaluation()
    }
    if (
        evaluation.waterAnalysisCreatedAtMillis !in
        MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS
    ) {
        invalidEvaluation()
    }
    if (evaluation.waterAnalysisMeasuredAtMillis > evaluation.evaluatedAtMillis) {
        invalidEvaluation()
    }
    if (evaluation.waterAnalysisCreatedAtMillis > evaluation.evaluatedAtMillis) {
        invalidEvaluation()
    }
    if (evaluation.waterMeasurementsCount > MAX_EVALUATION_WATER_MEASUREMENTS) {
        invalidEvaluation()
    }

    if (evaluation.hasTemperature) {
        requireEvaluationEnum<WaterTemperatureSource>(
            evaluation.temperatureSource,
            "evaluation.temperatureSource"
        )
        if (!evaluation.temperatureCelsius.isFinite()) invalidEvaluation()
        if (
            evaluation.temperatureCelsius !in
            MIN_EVALUATION_TEMPERATURE_C..MAX_EVALUATION_TEMPERATURE_C
        ) {
            invalidEvaluation()
        }
    } else {
        if (evaluation.temperatureCelsius != 0.0) invalidEvaluation()
        if (evaluation.temperatureSource.isNotBlank()) invalidEvaluation()
    }
    evaluation.waterMeasurementsList.forEach(::validateStoredEvaluationMeasurement)
}

private fun validateStoredEvaluationMeasurement(
    measurement: StoredLivestockEvaluationWaterMeasurement
) {
    requireEvaluationEnum<WaterParameter>(measurement.parameter, "evaluation.parameter")
    requireEvaluationEnum<WaterMeasurementMethod>(measurement.method, "evaluation.method")
    requireEvaluationEnum<WaterMeasurementBasis>(measurement.basis, "evaluation.basis")
    requireEvaluationEnum<WaterMeasurementUnit>(measurement.unit, "evaluation.unit")
    requireEvaluationEnum<WaterMeasurementBasis>(
        measurement.canonicalBasis,
        "evaluation.canonicalBasis"
    )
    requireEvaluationEnum<WaterMeasurementUnit>(
        measurement.canonicalUnit,
        "evaluation.canonicalUnit"
    )
    if (!measurement.value.isFinite() || measurement.value < 0.0) invalidEvaluation()
    if (
        measurement.hasCanonicalValue &&
        (!measurement.canonicalValue.isFinite() || measurement.canonicalValue < 0.0)
    ) {
        invalidEvaluation()
    }
    if (!measurement.hasCanonicalValue && measurement.canonicalValue != 0.0) {
        invalidEvaluation()
    }
}

private inline fun <reified T : Enum<T>> requireEvaluationEnum(
    raw: String,
    field: String
): T = runCatching { enumValueOf<T>(raw) }.getOrElse {
    throw StoreInvariantViolation("$field contains an unsupported enum value.")
}

private fun invalidEvaluation(): Nothing =
    throw StoreInvariantViolation("Invalid livestock evaluation.")

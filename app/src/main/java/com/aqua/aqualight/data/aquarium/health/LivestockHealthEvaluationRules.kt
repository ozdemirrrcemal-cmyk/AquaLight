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

internal fun validateStoredEvaluation(
    record: StoredLivestockObservation,
    evaluation: StoredLivestockEvaluation
) {
    if (
        evaluation.id <= 0L ||
        evaluation.requestId.length !in 1..MAX_EVALUATION_REQUEST_ID_LENGTH ||
        evaluation.evaluatedAtMillis !in
            MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS
    ) {
        invalidEvaluation()
    }
    requireEvaluationEnum<LivestockEvaluationTrigger>(
        evaluation.trigger,
        "evaluation.trigger"
    )
    if (evaluation.basedOnCheckCount !in 0..record.checksCount) invalidEvaluation()

    val basedOnChecks = record.checksList.take(evaluation.basedOnCheckCount)
    val latestCheck = basedOnChecks.maxByOrNull { check -> check.checkedAtMillis }
    val expectedAffected = latestCheck?.affectedCount ?: record.affectedCount
    val expectedLatestCheckAt = latestCheck?.checkedAtMillis ?: 0L
    if (
        evaluation.affectedCount != expectedAffected ||
        evaluation.basedOnLatestCheckAtMillis != expectedLatestCheckAt ||
        evaluation.evaluatedAtMillis < record.createdAtMillis ||
        evaluation.evaluatedAtMillis < expectedLatestCheckAt
    ) {
        invalidEvaluation()
    }
    validateStoredWaterSnapshot(evaluation)
}

internal fun validateEvaluationInput(
    record: StoredLivestockObservation,
    input: LivestockEvaluationInput
) {
    require(input.requestId.length in 1..MAX_EVALUATION_REQUEST_ID_LENGTH)
    input.waterAnalysis?.let { water ->
        require(water.id > 0L && water.tankId == record.tankId)
        require(water.measuredAtMillis in
            MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS)
        require(water.createdAtMillis in
            MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS)
        require(water.measurements.size <= MAX_EVALUATION_WATER_MEASUREMENTS)
    }
}

private fun validateStoredWaterSnapshot(evaluation: StoredLivestockEvaluation) {
    if (!evaluation.hasWaterAnalysis) {
        if (
            evaluation.waterAnalysisId != 0L ||
            evaluation.waterAnalysisMeasuredAtMillis != 0L ||
            evaluation.waterAnalysisCreatedAtMillis != 0L ||
            evaluation.hasTemperature ||
            evaluation.waterMeasurementsCount != 0
        ) {
            invalidEvaluation()
        }
        return
    }

    if (
        evaluation.waterAnalysisId <= 0L ||
        evaluation.waterAnalysisMeasuredAtMillis !in
            MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS ||
        evaluation.waterAnalysisCreatedAtMillis !in
            MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS ||
        evaluation.waterAnalysisMeasuredAtMillis > evaluation.evaluatedAtMillis ||
        evaluation.waterAnalysisCreatedAtMillis > evaluation.evaluatedAtMillis ||
        evaluation.waterMeasurementsCount > MAX_EVALUATION_WATER_MEASUREMENTS
    ) {
        invalidEvaluation()
    }
    if (evaluation.hasTemperature) {
        requireEvaluationEnum<WaterTemperatureSource>(
            evaluation.temperatureSource,
            "evaluation.temperatureSource"
        )
        if (
            !evaluation.temperatureCelsius.isFinite() ||
            evaluation.temperatureCelsius !in -50.0..100.0
        ) {
            invalidEvaluation()
        }
    } else if (
        evaluation.temperatureCelsius != 0.0 ||
        evaluation.temperatureSource.isNotBlank()
    ) {
        invalidEvaluation()
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

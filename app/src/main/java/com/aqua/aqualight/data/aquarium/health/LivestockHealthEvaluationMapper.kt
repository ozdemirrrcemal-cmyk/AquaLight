package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationInput
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationSnapshot
import com.aqua.aqualight.application.aquarium.health.LivestockEvaluationTrigger
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal fun buildStoredEvaluation(
    id: Long,
    record: StoredLivestockObservation,
    input: LivestockEvaluationInput,
    evaluatedAtMillis: Long
): StoredLivestockEvaluation {
    validateEvaluationInput(record, input)
    require(id > 0L)
    require(evaluatedAtMillis in
        MIN_LIVESTOCK_EVALUATION_TIME_MILLIS..MAX_LIVESTOCK_EVALUATION_TIME_MILLIS)

    val checks = record.checksList
    val latestCheck = checks.maxByOrNull { check -> check.checkedAtMillis }
    require(evaluatedAtMillis >= record.createdAtMillis)
    require(evaluatedAtMillis >= (latestCheck?.checkedAtMillis ?: record.createdAtMillis))
    input.waterAnalysis?.let { water ->
        require(water.measuredAtMillis <= evaluatedAtMillis)
        require(water.createdAtMillis <= evaluatedAtMillis)
    }
    val builder = StoredLivestockEvaluation.newBuilder()
        .setId(id)
        .setRequestId(input.requestId)
        .setTrigger(input.trigger.name)
        .setEvaluatedAtMillis(evaluatedAtMillis)
        .setAffectedCount(latestCheck?.affectedCount ?: record.affectedCount)
        .setBasedOnCheckCount(checks.size)
        .setBasedOnLatestCheckAtMillis(latestCheck?.checkedAtMillis ?: 0L)

    input.waterAnalysis?.let { water ->
        builder
            .setHasWaterAnalysis(true)
            .setWaterAnalysisId(water.id)
            .setWaterAnalysisMeasuredAtMillis(water.measuredAtMillis)
            .setWaterAnalysisCreatedAtMillis(water.createdAtMillis)
            .setHasTemperature(water.temperatureCelsius != null)
            .setTemperatureCelsius(water.temperatureCelsius ?: 0.0)
            .setTemperatureSource(water.temperatureSource?.name.orEmpty())
            .addAllWaterMeasurements(
                water.measurements.map(WaterMeasurementSnapshot::toStoredEvaluationMeasurement)
            )
    }
    return builder.build()
}

internal fun StoredLivestockEvaluation.toSnapshot(
    tankId: Long
): LivestockEvaluationSnapshot =
    LivestockEvaluationSnapshot(
        id = id,
        evaluatedAtMillis = evaluatedAtMillis,
        trigger = enumOrEvaluationViolation(trigger, "evaluation.trigger"),
        affectedCount = affectedCount,
        basedOnCheckCount = basedOnCheckCount,
        basedOnLatestCheckAtMillis = basedOnLatestCheckAtMillis.takeIf { it > 0L },
        waterAnalysis = toWaterAnalysisSnapshot(tankId)
    )

internal fun StoredLivestockEvaluation.matchesInput(
    record: StoredLivestockObservation,
    input: LivestockEvaluationInput
): Boolean {
    if (basedOnCheckCount !in 0..record.checksCount) return false
    val revisionRecord = record.toBuilder()
        .clearChecks()
        .addAllChecks(record.checksList.take(basedOnCheckCount))
        .build()
    val expected = buildStoredEvaluation(
        id = id,
        record = revisionRecord,
        input = input,
        evaluatedAtMillis = evaluatedAtMillis
    )
    return expected == this
}

private fun StoredLivestockEvaluation.toWaterAnalysisSnapshot(
    tankId: Long
): WaterAnalysisSnapshot? {
    if (!hasWaterAnalysis) return null
    return WaterAnalysisSnapshot(
        id = waterAnalysisId,
        tankId = tankId,
        measuredAtMillis = waterAnalysisMeasuredAtMillis,
        temperatureCelsius = temperatureCelsius.takeIf { hasTemperature },
        temperatureSource = if (hasTemperature) {
            enumOrEvaluationViolation<WaterTemperatureSource>(
                temperatureSource,
                "evaluation.temperatureSource"
            )
        } else {
            null
        },
        measurements = waterMeasurementsList.map(
            StoredLivestockEvaluationWaterMeasurement::toSnapshot
        ),
        createdAtMillis = waterAnalysisCreatedAtMillis
    )
}

private fun WaterMeasurementSnapshot.toStoredEvaluationMeasurement():
    StoredLivestockEvaluationWaterMeasurement =
    StoredLivestockEvaluationWaterMeasurement.newBuilder()
        .setParameter(parameter.name)
        .setValue(value)
        .setMethod(method.name)
        .setTestKitId(testKitId.orEmpty())
        .setBasis(basis.name)
        .setUnit(unit.name)
        .setHasCanonicalValue(canonicalValue != null)
        .setCanonicalValue(canonicalValue ?: 0.0)
        .setCanonicalBasis(canonicalBasis.name)
        .setCanonicalUnit(canonicalUnit.name)
        .build()

private fun StoredLivestockEvaluationWaterMeasurement.toSnapshot():
    WaterMeasurementSnapshot =
    WaterMeasurementSnapshot(
        parameter = enumOrEvaluationViolation<WaterParameter>(
            parameter,
            "evaluation.parameter"
        ),
        value = value,
        method = enumOrEvaluationViolation<WaterMeasurementMethod>(
            method,
            "evaluation.method"
        ),
        testKitId = testKitId.ifBlank { null },
        basis = enumOrEvaluationViolation<WaterMeasurementBasis>(
            basis,
            "evaluation.basis"
        ),
        unit = enumOrEvaluationViolation<WaterMeasurementUnit>(
            unit,
            "evaluation.unit"
        ),
        canonicalValue = canonicalValue.takeIf { hasCanonicalValue },
        canonicalBasis = enumOrEvaluationViolation(
            canonicalBasis,
            "evaluation.canonicalBasis"
        ),
        canonicalUnit = enumOrEvaluationViolation(
            canonicalUnit,
            "evaluation.canonicalUnit"
        )
    )

private inline fun <reified T : Enum<T>> enumOrEvaluationViolation(
    raw: String,
    field: String
): T = runCatching { enumValueOf<T>(raw) }.getOrElse {
    throw StoreInvariantViolation("$field contains an unsupported enum value.")
}

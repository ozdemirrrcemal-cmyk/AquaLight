package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal data class WaterAnalysisDraftRecord(
    val tankId: Long,
    val measuredAtMillis: Long,
    val temperatureCelsius: Double?,
    val temperatureSource: WaterTemperatureSource?,
    val measurements: List<WaterMeasurementRecord>,
    val requestId: String
)

internal data class WaterAnalysisRecord(
    val id: Long,
    val ownerUid: String,
    val tankId: Long,
    val measuredAtMillis: Long,
    val temperatureCelsius: Double?,
    val temperatureSource: WaterTemperatureSource?,
    val measurements: List<WaterMeasurementRecord>,
    val createdAtMillis: Long,
    val requestId: String = "",
    val evaluation: StoredWaterEvaluation? = null
)

internal data class WaterMeasurementRecord(
    val parameter: WaterParameter,
    val value: Double,
    val method: WaterMeasurementMethod,
    val testKitId: String?,
    val basis: WaterMeasurementBasis,
    val unit: WaterMeasurementUnit
)

internal fun StoredWaterAnalysis.toRecordStrict(): WaterAnalysisRecord =
    WaterAnalysisRecord(
        id = id,
        ownerUid = ownerUid,
        tankId = tankId,
        measuredAtMillis = measuredAtMillis,
        temperatureCelsius = temperatureCelsius.takeIf { hasTemperature },
        temperatureSource = if (hasTemperature) {
            enumValueOrViolation<WaterTemperatureSource>(
                field = "analysis.temperatureSource",
                raw = temperatureSource
            )
        } else {
            null
        },
        measurements = measurementsList.map(StoredWaterMeasurement::toRecordStrict),
        createdAtMillis = createdAtMillis,
        requestId = requestId,
        evaluation = if (hasEvaluation()) evaluation else null
    )

internal fun StoredWaterMeasurement.toRecordStrict(): WaterMeasurementRecord =
    WaterMeasurementRecord(
        parameter = enumValueOrViolation("measurement.parameter", parameter),
        value = value,
        method = enumValueOrViolation("measurement.method", method),
        testKitId = testKitId.ifBlank { null },
        basis = enumValueOrViolation("measurement.basis", basis),
        unit = enumValueOrViolation("measurement.unit", unit)
    )

internal fun WaterAnalysisRecord.toStoredStrict(): StoredWaterAnalysis =
    StoredWaterAnalysis.newBuilder()
        .setId(id)
        .setOwnerUid(ownerUid)
        .setTankId(tankId)
        .setMeasuredAtMillis(measuredAtMillis)
        .setHasTemperature(temperatureCelsius != null)
        .setTemperatureCelsius(temperatureCelsius ?: 0.0)
        .setTemperatureSource(temperatureSource?.name.orEmpty())
        .setCreatedAtMillis(createdAtMillis)
        .setRequestId(requestId)
        .addAllMeasurements(measurements.map(WaterMeasurementRecord::toStoredStrict))
        .apply { this@toStoredStrict.evaluation?.let(::setEvaluation) }
        .build()

private fun WaterMeasurementRecord.toStoredStrict(): StoredWaterMeasurement =
    StoredWaterMeasurement.newBuilder()
        .setParameter(parameter.name)
        .setValue(value)
        .setMethod(method.name)
        .setTestKitId(testKitId.orEmpty())
        .setBasis(basis.name)
        .setUnit(unit.name)
        .build()

private inline fun <reified T : Enum<T>> enumValueOrViolation(
    field: String,
    raw: String
): T = runCatching { enumValueOf<T>(raw) }.getOrElse {
    throw StoreInvariantViolation("$field contains an unsupported enum value.")
}

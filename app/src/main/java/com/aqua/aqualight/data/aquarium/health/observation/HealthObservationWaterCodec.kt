package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementResultId
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import com.aqua.aqualight.application.aquarium.health.observation.RecordedObservationTemperature
import com.aqua.aqualight.application.aquarium.health.observation.RecordedObservationWater
import com.aqua.aqualight.application.aquarium.health.observation.RecordedObservationWaterIdentity
import com.aqua.aqualight.data.aquarium.health.StoredWaterCanonicalMeasurement
import com.aqua.aqualight.data.aquarium.health.StoredWaterMeasurement
import java.util.Collections

/** Copies source-native and canonical values; reading observation history never reruns a conversion. */
internal object HealthObservationWaterCodec {
    fun encode(source: WaterAnalysisSnapshot): StoredHealthWaterSnapshot = StoredHealthWaterSnapshot.newBuilder()
        .setAnalysisId(source.id).setOriginalTankId(source.tankId).setMeasuredAtMillis(source.measuredAtMillis)
        .setCreatedAtMillis(source.createdAtMillis)
        .setAssessmentContextRevision(source.assessment?.contextRevision.orEmpty())
        .setTemperatureSource(source.temperatureSource?.name.orEmpty())
        .apply { source.temperatureCelsius?.let(::setTemperatureCelsius) }
        .addAllRawMeasurements(source.measurements.map { measurement ->
            StoredWaterMeasurement.newBuilder()
            .setParameter(measurement.parameter.name).setValue(measurement.value)
            .setMethod(measurement.method.name).setTestKitId(measurement.testKitId.orEmpty())
            .setBasis(measurement.basis.name).setUnit(measurement.unit.name).build() })
        .addAllCanonicalMeasurements(source.measurements.map { measurement ->
            StoredWaterCanonicalMeasurement.newBuilder()
            .setParameter(measurement.parameter.name).setBasis(measurement.canonicalBasis.name)
            .setUnit(measurement.canonicalUnit.name).setConversionRevision("frozen-source-v1")
            .apply { measurement.canonicalValue?.let(::setValue) }.build() }).build()

    fun decode(value: StoredHealthWaterSnapshot): RecordedObservationWater {
        require(value.analysisId > 0L && value.originalTankId > 0L)
        require(value.measuredAtMillis > 0L && value.createdAtMillis > 0L)
        val canonical = value.canonicalMeasurementsList.associateBy { it.parameter }
        val raw = value.rawMeasurementsList.associateBy { it.parameter }
        require(canonical.size == value.canonicalMeasurementsCount && raw.size == value.rawMeasurementsCount)
        require(raw.isNotEmpty() && raw.keys == canonical.keys)
        val measurements = value.rawMeasurementsList.map { measurement ->
            measurement(value.analysisId, measurement, checkNotNull(canonical[measurement.parameter]))
        }
        val temperature = if (value.hasTemperatureCelsius()) {
            require(value.temperatureCelsius.isFinite())
            RecordedObservationTemperature(value.temperatureCelsius,
                enumValueOf<WaterTemperatureSource>(value.temperatureSource))
        } else {
            require(value.temperatureSource.isEmpty())
            null
        }
        return RecordedObservationWater(RecordedObservationWaterIdentity(value.analysisId, value.originalTankId,
            value.measuredAtMillis, value.createdAtMillis), Collections.unmodifiableList(measurements), temperature)
    }

    private fun measurement(id: Long, raw: StoredWaterMeasurement,
        canonical: StoredWaterCanonicalMeasurement): WaterMeasurementSnapshot {
        val parameter = enumValueOf<WaterParameter>(raw.parameter)
        val converted = canonical.value.takeIf { canonical.hasValue() }
        require(raw.value.isFinite() && converted?.isFinite() != false)
        return WaterMeasurementSnapshot(WaterMeasurementResultId(id, parameter), parameter, raw.value,
            enumValueOf<WaterMeasurementMethod>(raw.method), raw.testKitId.ifEmpty { null },
            enumValueOf<WaterMeasurementBasis>(raw.basis), enumValueOf<WaterMeasurementUnit>(raw.unit), converted,
            enumValueOf<WaterMeasurementBasis>(canonical.basis), enumValueOf<WaterMeasurementUnit>(canonical.unit))
    }
}

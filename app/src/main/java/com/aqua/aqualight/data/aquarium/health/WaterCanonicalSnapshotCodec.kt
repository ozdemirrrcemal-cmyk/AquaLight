package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementNormalizer
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterParameterDefinitions

internal object WaterCanonicalSnapshotCodec {
    fun encode(input: WaterAnalysisInput): List<StoredWaterCanonicalMeasurement> = input.measurements.map { raw ->
        StoredWaterCanonicalMeasurement.newBuilder().setParameter(raw.parameter.name)
            .setBasis(WaterParameterDefinitions.canonicalBasis(raw.parameter).name)
            .setUnit(WaterParameterDefinitions.canonicalUnit(raw.parameter).name)
            .setConversionRevision(WaterMeasurementNormalizer.CONVERSION_REVISION)
            .apply {
                WaterMeasurementNormalizer.canonicalValueForStoredSource(raw.parameter, raw.value, raw.selection)
                    ?.let(::setValue)
            }.build()
    }

    fun validate(rows: List<StoredWaterCanonicalMeasurement>, parameters: Set<WaterParameter>) {
        require(rows.map { it.parameter }.toSet() == parameters.map { it.name }.toSet())
        require(rows.size == parameters.size)
        rows.forEach { row ->
            WaterEvaluationFindingCodec.enumValue<WaterMeasurementBasis>(row.basis)
            WaterEvaluationFindingCodec.enumValue<WaterMeasurementUnit>(row.unit)
            require(row.conversionRevision.isNotBlank())
            require(!row.hasValue() || row.value.isFinite() && row.value >= 0)
        }
    }
}

package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.CorruptionException
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.IOException
import java.io.InputStream

/** Unsupported data is preserved for a newer reader; it is not a corrupt/empty store. */
internal sealed class WaterAnalysisReadFailure(message: String) : IOException(message) {
    class UnsupportedSchema(val schemaVersion: Int) :
        WaterAnalysisReadFailure("Water-analysis schema $schemaVersion is unsupported.")

    class UnsupportedValue(val field: String, val rawValue: String) :
        WaterAnalysisReadFailure("Water-analysis field $field contains an unsupported value.")
}

/** Strict additive v1/v2/v3 reader. It never writes, relabels, filters or synthesizes measurements. */
internal object WaterAnalysisLegacyReader {
    fun readFrom(input: InputStream): WaterAnalysesStore {
        val parsed = parse(input)
        if (parsed.schemaVersion !in SUPPORTED_VERSIONS) {
            throw WaterAnalysisReadFailure.UnsupportedSchema(parsed.schemaVersion)
        }
        requireKnownValues(parsed)
        return try {
            if (parsed.schemaVersion == CommercialStoreSchema.WATER_ANALYSES_VERSION) {
                WaterAnalysisStoreRules.validateStore(parsed)
            } else {
                WaterAnalysisStoreRules.upgradeLegacyStore(parsed)
            }
        } catch (error: StoreInvariantViolation) {
            throw CorruptionException("Water analyses proto violates its stored-data contract.", error)
        }
    }

    private fun parse(input: InputStream): WaterAnalysesStore = try {
        WaterAnalysesStore.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        if (error.cause is IOException) throw IOException("Cannot read water-analysis input.", error)
        throw CorruptionException("Cannot read water analyses proto.", error)
    }

    private fun requireKnownValues(store: WaterAnalysesStore) {
        store.analysesList.forEach { analysis ->
            if (analysis.hasTemperature) {
                requireKnown("analysis.temperatureSource", analysis.temperatureSource, temperatureSources)
            }
            analysis.measurementsList.forEach { measurement ->
                requireKnown("measurement.parameter", measurement.parameter, parameters)
                requireKnown("measurement.method", measurement.method, methods)
                requireKnown("measurement.basis", measurement.basis, bases)
                requireKnown("measurement.unit", measurement.unit, units)
                if (measurement.method == WaterMeasurementMethod.TEST_KIT.name) {
                    requireKnown(
                        "measurement.testKitId", measurement.testKitId,
                        WaterMeasurementCatalog.testKitIdsFor(WaterParameter.valueOf(measurement.parameter)).toSet()
                    )
                }
            }
        }
    }

    private fun requireKnown(field: String, value: String, supported: Set<String>) {
        if (value.isBlank()) throw CorruptionException("Required water-analysis field $field is blank.")
        if (value !in supported) throw WaterAnalysisReadFailure.UnsupportedValue(field, value)
    }

    private val SUPPORTED_VERSIONS = setOf(1, 2, CommercialStoreSchema.WATER_ANALYSES_VERSION)
    private val parameters = WaterParameter.entries.mapTo(mutableSetOf()) { it.name }
    private val methods = WaterMeasurementMethod.entries.mapTo(mutableSetOf()) { it.name }
    private val bases = WaterMeasurementBasis.entries.mapTo(mutableSetOf()) { it.name }
    private val units = WaterMeasurementUnit.entries.mapTo(mutableSetOf()) { it.name }
    private val temperatureSources = WaterTemperatureSource.entries.mapTo(mutableSetOf()) { it.name }
}

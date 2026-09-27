package com.aqua.aqualight.data.aquarium.health

import androidx.datastore.core.CorruptionException
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterParameterDefinitions
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class WaterAnalysisLegacyReaderTest {
    @Test
    fun nonemptyLegacyVersionsPreserveOwnerIdentityRawValuesAndSourceFields() {
        listOf(1, 2).forEach { version ->
            val record = WaterAnalysisLegacyFixture.record().toBuilder()
                .addMeasurements(WaterAnalysisLegacyFixture.measurement().toBuilder()
                    .setParameter(WaterParameter.GH.name)
                    .setBasis(WaterMeasurementBasis.GH.name)
                    .setUnit(WaterMeasurementUnit.DGH.name)
                    .setMethod(WaterMeasurementMethod.SENSOR.name)
                    .clearTestKitId()
                ).build()
            val source = WaterAnalysisLegacyFixture.store(record).toBuilder().setSchemaVersion(version).build()
            val bytes = source.toByteArray()
            val migrated = WaterAnalysisLegacyReader.readFrom(ByteArrayInputStream(bytes))
            assertEquals(CommercialStoreSchema.WATER_ANALYSES_VERSION, migrated.schemaVersion)
            assertEquals(source.analysesList, migrated.analysesList)
            assertArrayEquals(bytes, migrated.toBuilder().setSchemaVersion(version).build().toByteArray())
        }
    }

    @Test
    fun everyCurrentPersistedEnumAndSupportedSelectionRoundTripsWithoutRelabelling() {
        WaterParameter.entries.forEach(::assertParameterSelections)
    }

    @Test
    fun futureValuesAreTypedUnsupportedFailuresAndNeverSilentlyFiltered() {
        val measurement = WaterAnalysisLegacyFixture.measurement()
        val mutations = listOf(
            "measurement.parameter" to measurement.toBuilder().setParameter("FUTURE_PARAMETER").build(),
            "measurement.method" to measurement.toBuilder().setMethod("FUTURE_METHOD").build(),
            "measurement.basis" to measurement.toBuilder().setBasis("FUTURE_BASIS").build(),
            "measurement.unit" to measurement.toBuilder().setUnit("FUTURE_UNIT").build(),
            "measurement.testKitId" to measurement.toBuilder().setTestKitId("future_kit").build()
        )
        mutations.forEach { (field, changed) ->
            val record = WaterAnalysisLegacyFixture.record().toBuilder().setMeasurements(0, changed).build()
            val failure = assertThrows(WaterAnalysisReadFailure.UnsupportedValue::class.java) {
                read(WaterAnalysisLegacyFixture.store(record))
            }
            assertEquals(field, failure.field)
        }
    }

    @Test
    fun unknownTemperatureSourceAndSchemaAreNotReportedAsCorruption() {
        val record = WaterAnalysisLegacyFixture.record().toBuilder()
            .setHasTemperature(true).setTemperatureCelsius(1.0).setTemperatureSource("FUTURE_SOURCE").build()
        val failure = assertThrows(WaterAnalysisReadFailure.UnsupportedValue::class.java) {
            read(WaterAnalysisLegacyFixture.store(record))
        }
        assertEquals("analysis.temperatureSource", failure.field)
        listOf(0, FUTURE_SCHEMA).forEach { version ->
            val source = WaterAnalysisLegacyFixture.store().toBuilder().setSchemaVersion(version).build()
            val unsupported = assertThrows(WaterAnalysisReadFailure.UnsupportedSchema::class.java) { read(source) }
            assertEquals(version, unsupported.schemaVersion)
        }
    }

    @Test
    fun streamIoFailureRemainsDistinctFromMalformedProtoAndInvalidStoredValues() {
        val inputFailure = IOException("Injected input failure")
        val stream = object : InputStream() {
            override fun read(): Int = throw inputFailure
        }
        val reported = assertThrows(IOException::class.java) {
            WaterAnalysisLegacyReader.readFrom(stream)
        }
        assertSame(inputFailure, reported.cause?.cause ?: reported)
        assertThrows(CorruptionException::class.java) {
            WaterAnalysisLegacyReader.readFrom(ByteArrayInputStream(byteArrayOf(INVALID_WIRE_TAG)))
        }
        val invalid = WaterAnalysisLegacyFixture.record().toBuilder().setOwnerUid(" ").build()
        assertThrows(CorruptionException::class.java) { read(WaterAnalysisLegacyFixture.store(invalid)) }
    }

    @Test
    fun unknownProtoFieldsSurviveAdditiveVersionUpgrade() {
        val source = WaterAnalysisLegacyFixture.store().toBuilder().setSchemaVersion(1).build()
        val buffer = ByteArrayOutputStream()
        buffer.write(source.toByteArray())
        CodedOutputStream.newInstance(buffer).also { output ->
            output.writeString(UNKNOWN_FIELD_NUMBER, "retained future metadata")
            output.flush()
        }
        val parsedOriginal = WaterAnalysesStore.parseFrom(buffer.toByteArray())
        val result = WaterAnalysisLegacyReader.readFrom(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(parsedOriginal, result.toBuilder().setSchemaVersion(1).build())
    }

    @Test
    fun knownTemperatureSourcesNamedKitAndRequestIdentityRemainIntact() {
        WaterTemperatureSource.entries.forEach { source ->
            val measurement = WaterAnalysisLegacyFixture.measurement().toBuilder()
                .setParameter(WaterParameter.NITRATE.name).setBasis(WaterMeasurementBasis.NO3.name)
                .setTestKitId(WaterMeasurementCatalog.SALIFERT_NITRATE_TEST_KIT_ID).build()
            val record = WaterAnalysisLegacyFixture.record().toBuilder()
                .setMeasurements(0, measurement).setRequestId(REQUEST_ID)
                .setHasTemperature(true).setTemperatureCelsius(1.0).setTemperatureSource(source.name).build()
            val stored = WaterAnalysisLegacyFixture.store(record).toBuilder().setSchemaVersion(2).build()
            assertEquals(record, read(stored).analysesList.single())
        }
    }

    private fun assertSelectionRoundTrip(
        parameter: WaterParameter,
        basis: WaterMeasurementBasis,
        unit: WaterMeasurementUnit,
        method: WaterMeasurementMethod
    ) {
        val kitId = if (method == WaterMeasurementMethod.TEST_KIT) WaterMeasurementCatalog.OTHER_TEST_KIT_ID else ""
        val measurement = WaterAnalysisLegacyFixture.measurement().toBuilder()
            .setParameter(parameter.name).setBasis(basis.name).setUnit(unit.name).setMethod(method.name)
            .setTestKitId(kitId)
            .build()
        val record = WaterAnalysisLegacyFixture.record().toBuilder().setMeasurements(0, measurement).build()
        assertEquals(record, read(WaterAnalysisLegacyFixture.store(record)).analysesList.single())
    }

    private fun assertParameterSelections(parameter: WaterParameter) {
        WaterParameterDefinitions.basisOptions(parameter).forEach { basis ->
            val units = WaterParameterDefinitions.unitOptions(parameter)
                .ifEmpty { listOf(WaterMeasurementUnit.NONE) }
            units.forEach { unit ->
                WaterMeasurementMethod.entries.forEach { method ->
                    assertSelectionRoundTrip(parameter, basis, unit, method)
                }
            }
        }
    }

    private fun read(source: WaterAnalysesStore): WaterAnalysesStore =
        WaterAnalysisLegacyReader.readFrom(ByteArrayInputStream(source.toByteArray()))

    private companion object {
        const val FUTURE_SCHEMA = 99
        const val INVALID_WIRE_TAG: Byte = 15
        const val UNKNOWN_FIELD_NUMBER = 127
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}

internal object WaterAnalysisLegacyFixture {
    fun measurement(): StoredWaterMeasurement = StoredWaterMeasurement.newBuilder()
        .setParameter(WaterParameter.AMMONIA_AMMONIUM.name)
        .setBasis(WaterMeasurementBasis.NH3_NH4.name)
        .setUnit(WaterMeasurementUnit.MG_L.name)
        .setValue(RAW_VALUE)
        .setMethod(WaterMeasurementMethod.TEST_KIT.name)
        .setTestKitId(WaterMeasurementCatalog.OTHER_TEST_KIT_ID)
        .build()

    fun record(): StoredWaterAnalysis = StoredWaterAnalysis.newBuilder()
        .setId(1L).setTankId(2L).setOwnerUid("legacy-owner")
        .setMeasuredAtMillis(OBSERVED_TIME).setCreatedAtMillis(CREATED_TIME)
        .addMeasurements(measurement()).build()

    fun store(record: StoredWaterAnalysis = record()): WaterAnalysesStore = WaterAnalysesStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.WATER_ANALYSES_VERSION).addAnalyses(record).build()

    private const val RAW_VALUE = 0.123456789
    private const val OBSERVED_TIME = 1_790_000_000_000L
    private const val CREATED_TIME = 1_790_000_001_000L
}

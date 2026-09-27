package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WaterAnalysisRequestFingerprintTest {
    @Test
    fun measurementOrderAndRequestIdentityDoNotChangeCanonicalPayload() {
        val source = draft()
        assertEquals(fingerprint(source), fingerprint(source.copy(
            measurements = source.measurements.reversed(), requestId = "another-request")))
        val stored = WaterAnalysisRecord(9, "owner", source.tankId, source.measuredAtMillis,
            source.temperatureCelsius, source.temperatureSource, source.measurements, TIME, source.requestId)
        assertEquals(fingerprint(source), WaterAnalysisRequestFingerprint.of(stored.toStoredStrict()))
    }

    @Test
    fun sampleSourceTemperatureAndIndividualMeasurementChangesCannotReplay() {
        val source = draft()
        val changed = listOf(source.copy(tankId = 3), source.copy(measuredAtMillis = TIME + 1),
            source.copy(temperatureCelsius = 20.0, temperatureSource = WaterTemperatureSource.MANUAL),
            source.copy(measurements = source.measurements.dropLast(1))) +
            listOf(source.measurements.first().copy(value = 8.0),
                source.measurements.first().copy(method = WaterMeasurementMethod.DIGITAL),
                source.measurements.first().copy(testKitId = "other"),
                source.measurements.first().copy(basis = WaterMeasurementBasis.NO3_N),
                source.measurements.first().copy(unit = WaterMeasurementUnit.PPM)).map {
                source.copy(measurements = listOf(it, source.measurements.last()))
            }
        changed.forEach { assertNotEquals(fingerprint(source), fingerprint(it)) }
    }

    @Test
    fun absentZeroAndSignedZeroHaveExplicitSemantics() {
        val absent = draft()
        val zero = absent.copy(temperatureCelsius = 0.0, temperatureSource = WaterTemperatureSource.MANUAL)
        assertNotEquals(fingerprint(absent), fingerprint(zero))
        assertEquals(fingerprint(zero), fingerprint(zero.copy(temperatureCelsius = -0.0)))
    }

    private fun fingerprint(draft: WaterAnalysisDraftRecord) = WaterAnalysisRequestFingerprint.of(draft)

    private fun draft() = WaterAnalysisDraftRecord(2, TIME, null, null, listOf(
        WaterMeasurementRecord(WaterParameter.PH, 7.0, WaterMeasurementMethod.MANUAL,
            null, WaterMeasurementBasis.PH, WaterMeasurementUnit.NONE),
        WaterMeasurementRecord(WaterParameter.NITRATE, 2.0, WaterMeasurementMethod.MANUAL,
            null, WaterMeasurementBasis.NO3, WaterMeasurementUnit.MG_L)
    ), "request")

    private companion object { const val TIME = 1_770_000_000_000L }
}

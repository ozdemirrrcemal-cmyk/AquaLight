package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WaterAnalysisStoreRulesTest {

    @Test
    fun protoRoundTripPreservesRawMeasurementMetadata() {
        val record = validRecord(
            measurement = WaterMeasurementRecord(
                parameter = WaterParameter.NITRATE,
                value = 12.3456,
                method = WaterMeasurementMethod.TEST_KIT,
                testKitId = "other",
                basis = WaterMeasurementBasis.NO3_N,
                unit = WaterMeasurementUnit.MG_L
            )
        )

        val restored = record.toStoredStrict().toRecordStrict()

        assertEquals(record, restored)
        WaterAnalysisStoreRules.validateRecord(restored, OWNER_UID)
    }

    @Test
    fun legacySensorMeasurementSurvivesReadAlthoughNewSensorWritesAreUnavailable() {
        val record = validRecord(
            WaterMeasurementRecord(
                parameter = WaterParameter.PH,
                value = 7.0,
                method = WaterMeasurementMethod.SENSOR,
                testKitId = null,
                basis = WaterMeasurementBasis.PH,
                unit = WaterMeasurementUnit.NONE
            )
        )
        val stored = WaterAnalysisStoreRules.defaultStore().toBuilder()
            .addAnalyses(record.toStoredStrict())
            .build()

        assertEquals(
            record,
            WaterAnalysisStoreRules.validateStore(stored).analysesList.single().toRecordStrict()
        )
    }

    @Test
    fun duplicateParametersFailClosed() {
        val measurement = WaterMeasurementRecord(
            parameter = WaterParameter.PH,
            value = 7.0,
            method = WaterMeasurementMethod.MANUAL,
            testKitId = null,
            basis = WaterMeasurementBasis.PH,
            unit = WaterMeasurementUnit.NONE
        )
        val record = validRecord(measurement).copy(
            measurements = listOf(measurement, measurement)
        )

        assertThrows(StoreInvariantViolation::class.java) {
            WaterAnalysisStoreRules.validateRecord(record, OWNER_UID)
        }
    }

    @Test
    fun ownerMismatchFailsClosed() {
        assertThrows(StoreInvariantViolation::class.java) {
            WaterAnalysisStoreRules.validateRecord(
                validRecord(
                    WaterMeasurementRecord(
                        parameter = WaterParameter.PH,
                        value = 7.0,
                        method = WaterMeasurementMethod.MANUAL,
                        testKitId = null,
                        basis = WaterMeasurementBasis.PH,
                        unit = WaterMeasurementUnit.NONE
                    )
                ),
                "different-owner"
            )
        }
    }

    private fun validRecord(measurement: WaterMeasurementRecord): WaterAnalysisRecord =
        WaterAnalysisRecord(
            id = 11L,
            ownerUid = OWNER_UID,
            tankId = 7L,
            measuredAtMillis = VALID_TIME,
            temperatureCelsius = 24.5,
            temperatureSource =
                com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource.MANUAL,
            measurements = listOf(measurement),
            createdAtMillis = VALID_TIME
        )

    private companion object {
        const val OWNER_UID = "owner-1"
        const val VALID_TIME = 1_790_000_000_000L
    }
}

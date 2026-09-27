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
    fun latestEventOrdersBySampleThenCommitThenIdentity() {
        val measurement = WaterMeasurementRecord(
            parameter = WaterParameter.PH,
            value = 7.0,
            method = WaterMeasurementMethod.MANUAL,
            testKitId = null,
            basis = WaterMeasurementBasis.PH,
            unit = WaterMeasurementUnit.NONE
        )
        val base = validRecord(measurement)
        val olderCommitWithHigherId = base.copy(id = 99L, createdAtMillis = VALID_TIME)
        val laterCommit = base.copy(id = 12L, createdAtMillis = VALID_TIME + 1L)
        val olderSample = base.copy(id = 100L, measuredAtMillis = VALID_TIME - 1L)

        assertEquals(
            listOf(laterCommit, olderCommitWithHigherId, olderSample),
            WaterAnalysisIdentityRules.newestFirst(
                listOf(olderSample, olderCommitWithHigherId, laterCommit)
            )
        )
    }

    @Test
    fun versionOneRecordsUpgradeWithoutChangingMeasurementsOrIdentity() {
        val record = validRecord(validPhMeasurement())
        val legacy = WaterAnalysisStoreRules.defaultStore().toBuilder()
            .setSchemaVersion(1)
            .addAnalyses(record.toStoredStrict())
            .build()

        val upgraded = WaterAnalysisStoreRules.upgradeLegacyStore(legacy)
        assertEquals(2, upgraded.schemaVersion)
        assertEquals(record, upgraded.analysesList.single().toRecordStrict())
    }

    @Test
    fun repeatedRequestReturnsSameIdentityAndRejectsChangedPayload() {
        val record = validRecord(validPhMeasurement()).copy(requestId = REQUEST_ID)
        val store = WaterAnalysisStoreRules.defaultStore().toBuilder()
            .addAnalyses(record.toStoredStrict())
            .build()
        val draft = WaterAnalysisDraftRecord(
            tankId = record.tankId,
            measuredAtMillis = record.measuredAtMillis,
            temperatureCelsius = record.temperatureCelsius,
            temperatureSource = record.temperatureSource,
            measurements = record.measurements,
            requestId = REQUEST_ID
        )

        assertEquals(record.id, requireNotNull(WaterAnalysisIdentityRules.replayId(store, OWNER_UID, draft)))
        assertThrows(StoreInvariantViolation::class.java) {
            WaterAnalysisIdentityRules.replayId(
                store,
                OWNER_UID,
                draft.copy(measuredAtMillis = VALID_TIME + 1L)
            )
        }
    }

    @Test
    fun duplicateOwnerRequestIdFailsStoreValidation() {
        val record = validRecord(validPhMeasurement()).copy(requestId = REQUEST_ID)
        val store = WaterAnalysisStoreRules.defaultStore().toBuilder()
            .addAnalyses(record.toStoredStrict())
            .addAnalyses(record.copy(id = 12L).toStoredStrict())
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            WaterAnalysisStoreRules.validateStore(store)
        }
    }

    private fun validPhMeasurement(): WaterMeasurementRecord = WaterMeasurementRecord(
        parameter = WaterParameter.PH,
        value = 7.0,
        method = WaterMeasurementMethod.MANUAL,
        testKitId = null,
        basis = WaterMeasurementBasis.PH,
        unit = WaterMeasurementUnit.NONE
    )

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
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}

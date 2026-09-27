package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisOperations
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterParameterDefinitions
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementNormalizer
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSelection
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementResultId
import com.aqua.aqualight.data.auth.OwnerSessionWriteLease
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class DefaultWaterAnalysisOperations(
    private val store: WaterAnalysisDataStoreManager,
    private val session: OwnerSessionWriteLease
) : WaterAnalysisOperations {

    override fun analysesForTank(tankId: Long): Flow<List<WaterAnalysisSnapshot>> =
        store.analysesForTankFlow(session.ownerUid, tankId).onStart { session.requireCurrent() }.map { analyses ->
            session.requireCurrent()
            analyses.map { record -> record.toApplicationSnapshot() }
        }

    override fun analysis(analysisId: Long): Flow<WaterAnalysisSnapshot?> =
        store.analysisFlow(session.ownerUid, analysisId).onStart { session.requireCurrent() }.map { record ->
            session.requireCurrent()
            record?.toApplicationSnapshot()
        }

    override suspend fun saveAnalysis(input: WaterAnalysisInput): Long {
        session.requireCurrent()
        WaterAnalysisPolicy.validate(input)
        return store.addAnalysis(input.toDraftRecord(), session)
    }

    override suspend fun deleteAnalysis(analysisId: Long) =
        store.deleteAnalysis(analysisId, session)

    private fun WaterAnalysisInput.toDraftRecord(): WaterAnalysisDraftRecord =
        WaterAnalysisDraftRecord(
            tankId = tankId,
            measuredAtMillis = measuredAtMillis,
            temperatureCelsius = temperatureCelsius,
            temperatureSource = temperatureSource,
            requestId = requestId,
            measurements = measurements.map { measurement ->
                WaterMeasurementRecord(
                    parameter = measurement.parameter,
                    value = measurement.value,
                    method = measurement.selection.method,
                    testKitId = measurement.selection.testKitId,
                    basis = measurement.selection.basis,
                    unit = measurement.selection.unit
                )
            }
        )
}

internal fun WaterAnalysisRecord.toApplicationSnapshot(): WaterAnalysisSnapshot =
    WaterAnalysisSnapshot(
        id = id,
        tankId = tankId,
        measuredAtMillis = measuredAtMillis,
        temperatureCelsius = temperatureCelsius,
        temperatureSource = temperatureSource,
        measurements = measurements.map { measurement ->
            val canonicalBasis = WaterParameterDefinitions.canonicalBasis(measurement.parameter)
            val canonicalUnit = WaterParameterDefinitions.canonicalUnit(measurement.parameter)
            WaterMeasurementSnapshot(
                resultId = WaterMeasurementResultId(id, measurement.parameter),
                parameter = measurement.parameter,
                value = measurement.value,
                method = measurement.method,
                testKitId = measurement.testKitId,
                basis = measurement.basis,
                unit = measurement.unit,
                canonicalValue = WaterMeasurementNormalizer.canonicalValueForStoredSource(
                    parameter = measurement.parameter,
                    value = measurement.value,
                    selection = WaterMeasurementSelection(
                        method = measurement.method,
                        testKitId = measurement.testKitId,
                        basis = measurement.basis,
                        unit = measurement.unit
                    )
                ),
                canonicalBasis = canonicalBasis,
                canonicalUnit = canonicalUnit
            )
        },
        createdAtMillis = createdAtMillis
    )

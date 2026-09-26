package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisOperations
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementNormalizer
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.data.user.withCurrentOwnerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultWaterAnalysisOperations(
    private val store: WaterAnalysisDataStoreManager
) : WaterAnalysisOperations {

    override fun analysesForTank(tankId: Long): Flow<List<WaterAnalysisSnapshot>> =
        store.analysesForTankFlow(tankId).map { analyses ->
            analyses.map { record -> record.toApplicationSnapshot() }
        }

    override fun analysis(analysisId: Long): Flow<WaterAnalysisSnapshot?> =
        store.analysisFlow(analysisId).map { record ->
            record?.toApplicationSnapshot()
        }

    override suspend fun saveAnalysis(input: WaterAnalysisInput): Long =
        withCurrentOwnerScope {
            WaterAnalysisPolicy.validate(input)
            store.addAnalysis(input.toDraftRecord())
        }

    override suspend fun deleteAnalysis(analysisId: Long) =
        withCurrentOwnerScope {
            store.deleteAnalysis(analysisId)
        }

    private fun WaterAnalysisInput.toDraftRecord(): WaterAnalysisDraftRecord =
        WaterAnalysisDraftRecord(
            tankId = tankId,
            measuredAtMillis = measuredAtMillis,
            temperatureCelsius = temperatureCelsius,
            temperatureSource = temperatureSource,
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

    private fun WaterAnalysisRecord.toApplicationSnapshot(): WaterAnalysisSnapshot =
        WaterAnalysisSnapshot(
            id = id,
            tankId = tankId,
            measuredAtMillis = measuredAtMillis,
            temperatureCelsius = temperatureCelsius,
            temperatureSource = temperatureSource,
            measurements = measurements.map { measurement ->
                val canonicalBasis =
                    WaterMeasurementCatalog.canonicalBasis(measurement.parameter)
                val canonicalUnit =
                    WaterMeasurementCatalog.canonicalUnit(measurement.parameter)
                WaterMeasurementSnapshot(
                    parameter = measurement.parameter,
                    value = measurement.value,
                    method = measurement.method,
                    testKitId = measurement.testKitId,
                    basis = measurement.basis,
                    unit = measurement.unit,
                    canonicalValue = WaterMeasurementNormalizer.canonicalValue(
                        parameter = measurement.parameter,
                        value = measurement.value,
                        basis = measurement.basis,
                        unit = measurement.unit
                    ),
                    canonicalBasis = canonicalBasis,
                    canonicalUnit = canonicalUnit
                )
            },
            createdAtMillis = createdAtMillis
        )
}

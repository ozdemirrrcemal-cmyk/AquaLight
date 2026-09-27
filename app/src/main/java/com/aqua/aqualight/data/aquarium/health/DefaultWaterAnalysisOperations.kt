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
import androidx.datastore.core.CorruptionException
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisFailure
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisUnavailableException
import com.aqua.aqualight.data.store.StoreInvariantViolation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class DefaultWaterAnalysisOperations(
    private val store: WaterAnalysisDataStoreManager,
    private val session: OwnerSessionWriteLease,
    private val evaluationPreparation: WaterAnalysisEvaluationPreparation?
) : WaterAnalysisOperations {

    override fun analysesForTank(tankId: Long): Flow<List<WaterAnalysisSnapshot>> =
        store.analysesForTankFlow(session.ownerUid, tankId).onStart { session.requireCurrent() }.map { analyses ->
            session.requireCurrent()
            analyses.map { record -> record.toApplicationSnapshot() }
        }.withWaterReadFailures()

    override fun latestAnalysis(tankId: Long): Flow<WaterAnalysisSnapshot?> =
        store.latestAnalysisFlow(session.ownerUid, tankId).onStart { session.requireCurrent() }.map { record ->
            session.requireCurrent()
            record?.toApplicationSnapshot()
        }.withWaterReadFailures()

    override fun analysis(tankId: Long, analysisId: Long): Flow<WaterAnalysisSnapshot?> =
        store.analysisFlow(session.ownerUid, tankId, analysisId).onStart { session.requireCurrent() }.map { record ->
            session.requireCurrent()
            record?.toApplicationSnapshot()
        }.withWaterReadFailures()

    override suspend fun saveAnalysis(input: WaterAnalysisInput): Long {
        session.requireCurrent()
        val frozenInput = input.copy(measurements = input.measurements.toList())
        WaterAnalysisPolicy.validate(frozenInput)
        return store.addAnalysis(frozenInput.toDraftRecord(), session) {
            evaluationPreparation?.prepare(frozenInput)
        }
    }

    override suspend fun deleteAnalysis(tankId: Long, analysisId: Long) =
        store.deleteAnalysis(tankId, analysisId, session)

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

private fun <T> Flow<T>.withWaterReadFailures(): Flow<T> = flowOn(Dispatchers.IO).catch { error ->
    if (error is CancellationException) throw error
    val failure = when (error) {
        is WaterAnalysisReadFailure.UnsupportedSchema -> WaterAnalysisFailure.UNSUPPORTED_SCHEMA
        is WaterAnalysisReadFailure.UnsupportedValue -> WaterAnalysisFailure.UNSUPPORTED_VALUE
        is CorruptionException, is StoreInvariantViolation -> WaterAnalysisFailure.CORRUPT_DATA
        else -> WaterAnalysisFailure.STORE_UNAVAILABLE
    }
    throw WaterAnalysisUnavailableException(failure, error)
}

internal fun WaterAnalysisRecord.toApplicationSnapshot(): WaterAnalysisSnapshot =
    WaterAnalysisSnapshot(
        id = id,
        tankId = tankId,
        measuredAtMillis = measuredAtMillis,
        temperatureCelsius = temperatureCelsius,
        temperatureSource = temperatureSource,
        measurements = measurements.map { measurement ->
            val frozen = evaluation?.canonicalMeasurementsList?.single { it.parameter == measurement.parameter.name }
            val canonicalBasis = frozen?.basis?.let {
                enumValueOf<com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis>(it)
            } ?: WaterParameterDefinitions.canonicalBasis(measurement.parameter)
            val canonicalUnit = frozen?.unit?.let {
                enumValueOf<com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit>(it)
            } ?: WaterParameterDefinitions.canonicalUnit(measurement.parameter)
            WaterMeasurementSnapshot(
                resultId = WaterMeasurementResultId(id, measurement.parameter),
                parameter = measurement.parameter,
                value = measurement.value,
                method = measurement.method,
                testKitId = measurement.testKitId,
                basis = measurement.basis,
                unit = measurement.unit,
                canonicalValue = if (frozen != null) frozen.value.takeIf { frozen.hasValue() }
                else WaterMeasurementNormalizer.canonicalValueForStoredSource(
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
        createdAtMillis = createdAtMillis,
        assessment = evaluation?.let { WaterEvaluationCodec.decode(it, importOrigin?.sourceTankId ?: tankId) },
        contextCapturedAtMillis = evaluation?.capturedAtMillis
    )

package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementInput
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource

internal data class WaterAnalysisInputBuildRequest(
    val tankId: Long,
    val measuredAtMillis: Long,
    val temperatureText: String,
    val temperatureSource: WaterTemperatureSource,
    val parameterState: WaterAnalysisParameterState
)

internal sealed interface WaterAnalysisInputBuildResult {
    data class Success(val input: WaterAnalysisInput) : WaterAnalysisInputBuildResult

    sealed interface Failure : WaterAnalysisInputBuildResult {
        data object MeasurementRequired : Failure
        data class InvalidParameterValue(val parameterId: WaterTestParameterId) : Failure
        data object InvalidMeasurementSelection : Failure
        data object InvalidTemperature : Failure
    }
}

internal object WaterAnalysisInputBuilder {

    fun build(request: WaterAnalysisInputBuildRequest): WaterAnalysisInputBuildResult {
        val measurements = buildMeasurements(request.parameterState)
        val temperature = parseTemperature(request.temperatureText)

        return when {
            measurements.failure != null -> measurements.failure
            measurements.values.isEmpty() ->
                WaterAnalysisInputBuildResult.Failure.MeasurementRequired
            temperature.invalid ->
                WaterAnalysisInputBuildResult.Failure.InvalidTemperature
            else -> WaterAnalysisInputBuildResult.Success(
                WaterAnalysisInput(
                    tankId = request.tankId,
                    measuredAtMillis = request.measuredAtMillis,
                    temperatureCelsius = temperature.value,
                    temperatureSource = temperature.value?.let { request.temperatureSource },
                    measurements = measurements.values
                )
            )
        }
    }

    private fun buildMeasurements(
        state: WaterAnalysisParameterState
    ): MeasurementBuildResult {
        val values = mutableListOf<WaterMeasurementInput>()
        var failure: WaterAnalysisInputBuildResult.Failure? = null

        state.parameterValues.forEach { (parameterId, rawValue) ->
            val trimmed = rawValue.trim()
            if (trimmed.isNotEmpty() && failure == null) {
                val value = WaterAnalysisValueParser.parse(trimmed)
                val selection = state.measurementSelections[parameterId]
                    ?: WaterMeasurementUiCatalog.defaultSelection(parameterId)
                val domainSelection = selection.toValidDomainSelectionOrNull(parameterId)

                when {
                    value == null -> {
                        failure =
                            WaterAnalysisInputBuildResult.Failure.InvalidParameterValue(parameterId)
                    }
                    domainSelection == null -> {
                        failure =
                            WaterAnalysisInputBuildResult.Failure.InvalidMeasurementSelection
                    }
                    else -> values += WaterMeasurementInput(
                        parameter = parameterId.toDomainParameter(),
                        value = value,
                        selection = domainSelection
                    )
                }
            }
        }

        return MeasurementBuildResult(values = values, failure = failure)
    }

    private fun parseTemperature(rawValue: String): TemperatureBuildResult {
        val trimmed = rawValue.trim()
        val value = trimmed.takeIf(String::isNotEmpty)
            ?.let(WaterAnalysisValueParser::parse)
        return TemperatureBuildResult(
            value = value,
            invalid = trimmed.isNotEmpty() && value == null
        )
    }

    private data class MeasurementBuildResult(
        val values: List<WaterMeasurementInput>,
        val failure: WaterAnalysisInputBuildResult.Failure?
    )

    private data class TemperatureBuildResult(
        val value: Double?,
        val invalid: Boolean
    )
}

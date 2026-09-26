package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import java.util.Calendar

internal enum class TemperatureSource {
    SENSOR,
    MANUAL
}

internal class WaterAnalysisDraftUiState {
    val selectedCalendar: Calendar = Calendar.getInstance()
    var temperatureSource: TemperatureSource = TemperatureSource.MANUAL
    var sensorUiState: TemperatureSensorUiState = TemperatureSensorUiState.Unavailable
    var manualTemperatureValue: String = ""

    val parameterValues = linkedMapOf<WaterTestParameterId, String>()
    val additionalParameters = linkedSetOf<WaterTestParameterId>()

    fun restore(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        val measurementTime = savedInstanceState.getLong(
            STATE_MEASUREMENT_TIME_MILLIS,
            NO_SAVED_TIME
        )
        if (measurementTime != NO_SAVED_TIME) {
            selectedCalendar.timeInMillis = measurementTime
        }

        temperatureSource = savedInstanceState.getString(STATE_TEMPERATURE_SOURCE)
            ?.let { raw -> runCatching { TemperatureSource.valueOf(raw) }.getOrNull() }
            ?: TemperatureSource.MANUAL
        manualTemperatureValue = savedInstanceState
            .getString(STATE_MANUAL_TEMPERATURE)
            .orEmpty()

        savedInstanceState.getStringArrayList(STATE_ADDITIONAL_PARAMETER_IDS)
            .orEmpty()
            .mapNotNull { rawId ->
                runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
            }
            .forEach(additionalParameters::add)

        val valueIds = savedInstanceState
            .getStringArrayList(STATE_PARAMETER_VALUE_IDS)
            .orEmpty()
        val values = savedInstanceState
            .getStringArrayList(STATE_PARAMETER_VALUES)
            .orEmpty()

        valueIds.zip(values).forEach { (rawId, value) ->
            runCatching { WaterTestParameterId.valueOf(rawId) }
                .getOrNull()
                ?.let { id -> parameterValues[id] = value }
        }
    }

    fun save(outState: Bundle) {
        outState.putLong(STATE_MEASUREMENT_TIME_MILLIS, selectedCalendar.timeInMillis)
        outState.putString(STATE_TEMPERATURE_SOURCE, temperatureSource.name)
        outState.putString(STATE_MANUAL_TEMPERATURE, manualTemperatureValue)
        outState.putStringArrayList(
            STATE_ADDITIONAL_PARAMETER_IDS,
            ArrayList(additionalParameters.map { it.name })
        )
        outState.putStringArrayList(
            STATE_PARAMETER_VALUE_IDS,
            ArrayList(parameterValues.keys.map { it.name })
        )
        outState.putStringArrayList(
            STATE_PARAMETER_VALUES,
            ArrayList(parameterValues.values)
        )
    }

    private companion object {
        const val STATE_MEASUREMENT_TIME_MILLIS = "measurement_time_millis"
        const val STATE_TEMPERATURE_SOURCE = "temperature_source"
        const val STATE_MANUAL_TEMPERATURE = "manual_temperature"
        const val STATE_ADDITIONAL_PARAMETER_IDS = "additional_parameter_ids"
        const val STATE_PARAMETER_VALUE_IDS = "parameter_value_ids"
        const val STATE_PARAMETER_VALUES = "parameter_values"
        const val NO_SAVED_TIME = -1L
    }
}

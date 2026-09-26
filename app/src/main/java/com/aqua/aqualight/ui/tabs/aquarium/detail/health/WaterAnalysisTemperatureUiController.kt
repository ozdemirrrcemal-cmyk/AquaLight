package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisSensorSectionBinding

internal class WaterAnalysisTemperatureUiController(
    private val fragment: Fragment,
    private val binding: ItemTankHealthAnalysisSensorSectionBinding,
    savedInstanceState: Bundle?
) {
    private var temperatureSource = savedInstanceState
        ?.getString(STATE_TEMPERATURE_SOURCE)
        ?.let { rawSource -> runCatching { TemperatureSource.valueOf(rawSource) }.getOrNull() }
        ?: TemperatureSource.MANUAL
    private var manualTemperatureValue =
        savedInstanceState?.getString(STATE_MANUAL_TEMPERATURE).orEmpty()
    private var sensorUiState: TemperatureSensorUiState = TemperatureSensorUiState.Unavailable

    fun bind() {
        binding.inputTemperature.setText(manualTemperatureValue)
        binding.inputTemperature.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    if (temperatureSource == TemperatureSource.MANUAL) {
                        manualTemperatureValue = s?.toString().orEmpty()
                    }
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )

        binding.cardSensorSource.setOnClickListener {
            val sensorReading = sensorUiState as? TemperatureSensorUiState.Reading
                ?: return@setOnClickListener
            if (temperatureSource == TemperatureSource.MANUAL) {
                manualTemperatureValue = binding.inputTemperature.text?.toString().orEmpty()
            }
            temperatureSource = TemperatureSource.SENSOR
            if (binding.inputTemperature.text?.toString() != sensorReading.temperatureText) {
                binding.inputTemperature.setText(sensorReading.temperatureText)
            }
            renderTemperatureSource()
        }
        binding.cardManualSource.setOnClickListener {
            temperatureSource = TemperatureSource.MANUAL
            renderTemperatureSource()
        }

        renderSensorUiState()
    }

    fun saveState(outState: Bundle) {
        if (temperatureSource == TemperatureSource.MANUAL) {
            manualTemperatureValue = binding.inputTemperature.text?.toString().orEmpty()
        }
        outState.putString(STATE_TEMPERATURE_SOURCE, temperatureSource.name)
        outState.putString(STATE_MANUAL_TEMPERATURE, manualTemperatureValue)
    }

    private fun renderSensorUiState() {
        binding.tvSensorStatusValue.text = when (val state = sensorUiState) {
            TemperatureSensorUiState.Unavailable ->
                fragment.getString(R.string.tank_health_analysis_sensor_unavailable)

            TemperatureSensorUiState.Loading ->
                fragment.getString(R.string.tank_health_analysis_sensor_loading)

            is TemperatureSensorUiState.Available ->
                fragment.getString(
                    R.string.tank_health_analysis_sensor_available,
                    state.deviceName
                )

            is TemperatureSensorUiState.Reading ->
                fragment.getString(
                    R.string.tank_health_analysis_sensor_reading,
                    state.deviceName,
                    state.temperatureText
                )

            is TemperatureSensorUiState.Stale ->
                fragment.getString(R.string.tank_health_analysis_sensor_stale)

            TemperatureSensorUiState.Error ->
                fragment.getString(R.string.tank_health_analysis_sensor_error)
        }

        val sensorSelectable = sensorUiState is TemperatureSensorUiState.Reading
        binding.cardSensorSource.isEnabled = sensorSelectable
        binding.cardSensorSource.isClickable = sensorSelectable
        binding.cardSensorSource.alpha = if (sensorSelectable) ENABLED_ALPHA else DISABLED_ALPHA

        if (!sensorSelectable && temperatureSource == TemperatureSource.SENSOR) {
            temperatureSource = TemperatureSource.MANUAL
        }
        renderTemperatureSource()
    }

    private fun renderTemperatureSource() {
        val context = fragment.requireContext()
        val primary = ContextCompat.getColor(context, R.color.aqua_accent_primary)
        val transparent = ContextCompat.getColor(context, R.color.aqua_color_transparent)
        val outline = ContextCompat.getColor(context, R.color.aqua_card_metric_outline)
        val selectedText = ContextCompat.getColor(context, R.color.aqua_content_on_dark)
        val unselectedText = ContextCompat.getColor(context, R.color.aqua_card_text_secondary)

        val sensorReading = sensorUiState as? TemperatureSensorUiState.Reading
        val sensorSelected = temperatureSource == TemperatureSource.SENSOR &&
            sensorReading != null

        binding.cardSensorSource.setCardBackgroundColor(
            if (sensorSelected) primary else transparent
        )
        binding.cardSensorSource.strokeColor = if (sensorSelected) primary else outline
        binding.tvSensorSource.setTextColor(if (sensorSelected) selectedText else unselectedText)
        binding.tvSensorSource.setTypeface(
            null,
            if (sensorSelected) Typeface.BOLD else Typeface.NORMAL
        )

        binding.cardManualSource.setCardBackgroundColor(
            if (sensorSelected) transparent else primary
        )
        binding.cardManualSource.strokeColor = if (sensorSelected) outline else primary
        binding.tvManualSource.setTextColor(if (sensorSelected) unselectedText else selectedText)
        binding.tvManualSource.setTypeface(
            null,
            if (sensorSelected) Typeface.NORMAL else Typeface.BOLD
        )

        binding.cardSensorReadingBadge.isVisible = sensorSelected
        val targetValue = if (sensorSelected) {
            requireNotNull(sensorReading).temperatureText
        } else {
            manualTemperatureValue
        }
        if (binding.inputTemperature.text?.toString() != targetValue) {
            binding.inputTemperature.setText(targetValue)
        }

        binding.inputTemperature.isFocusable = !sensorSelected
        binding.inputTemperature.isFocusableInTouchMode = !sensorSelected
        binding.inputTemperature.isClickable = !sensorSelected
        binding.inputTemperature.isCursorVisible = !sensorSelected
    }

    private enum class TemperatureSource {
        SENSOR,
        MANUAL
    }

    private companion object {
        const val STATE_TEMPERATURE_SOURCE = "temperature_source"
        const val STATE_MANUAL_TEMPERATURE = "manual_temperature"
        const val ENABLED_ALPHA = 1f
        const val DISABLED_ALPHA = 0.5f
    }
}

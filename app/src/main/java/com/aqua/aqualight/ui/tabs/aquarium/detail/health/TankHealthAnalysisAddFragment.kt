package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisAddBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TankHealthAnalysisAddFragment :
    Fragment(R.layout.fragment_tank_health_analysis_add) {

    private var _binding: FragmentTankHealthAnalysisAddBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate = LocalDate.of(2026, 9, 26)
    private var selectedTime: LocalTime = LocalTime.of(14, 10)
    private var temperatureSource: TemperatureSource = TemperatureSource.SENSOR

    private val tankId: Long
        get() = requireArguments().getLong(ARG_TANK_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(tankId > 0L) {
            "TankHealthAnalysisAddFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisAddBinding.bind(view)

        setupHeader()
        setupMeasurementTime()
        setupTemperatureSource()
        setupNavigation()
        renderMeasurementTime()
        renderTemperatureSource()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_add),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupMeasurementTime() {
        binding.cardDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                    renderMeasurementTime()
                },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth
            ).show()
        }

        binding.cardTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedTime = LocalTime.of(hourOfDay, minute)
                    renderMeasurementTime()
                },
                selectedTime.hour,
                selectedTime.minute,
                true
            ).show()
        }
    }

    private fun setupTemperatureSource() {
        binding.cardSensorSource.setOnClickListener {
            temperatureSource = TemperatureSource.SENSOR
            renderTemperatureSource()
        }
        binding.cardManualSource.setOnClickListener {
            temperatureSource = TemperatureSource.MANUAL
            renderTemperatureSource()
        }
    }

    private fun setupNavigation() {
        binding.btnHistory.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.tankHealthAnalysisAddFragment) {
                return@setOnClickListener
            }

            navController.navigate(
                R.id.action_tankHealthAnalysisAddFragment_to_tankHealthAnalysisHistoryFragment,
                bundleOf(ARG_TANK_ID to tankId)
            )
        }

        // Persistence is intentionally deferred to the data-integration stage.
        binding.btnSaveAnalysis.setOnClickListener { }
    }

    private fun renderMeasurementTime() {
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        binding.tvDateValue.text = selectedDate.format(
            DateTimeFormatter.ofPattern(DATE_PATTERN, locale)
        )
        binding.tvTimeValue.text = selectedTime.format(
            DateTimeFormatter.ofPattern(TIME_PATTERN, locale)
        )
    }

    private fun renderTemperatureSource() {
        val context = requireContext()
        val primary = ContextCompat.getColor(context, R.color.aqua_accent_primary)
        val transparent = ContextCompat.getColor(context, R.color.aqua_color_transparent)
        val outline = ContextCompat.getColor(context, R.color.aqua_card_metric_outline)
        val selectedText = ContextCompat.getColor(context, R.color.aqua_content_on_dark)
        val unselectedText = ContextCompat.getColor(context, R.color.aqua_card_text_secondary)

        val sensorSelected = temperatureSource == TemperatureSource.SENSOR
        binding.cardSensorSource.setCardBackgroundColor(
            if (sensorSelected) primary else transparent
        )
        binding.cardSensorSource.strokeColor = if (sensorSelected) primary else outline
        binding.tvSensorSource.setTextColor(if (sensorSelected) selectedText else unselectedText)

        binding.cardManualSource.setCardBackgroundColor(
            if (sensorSelected) transparent else primary
        )
        binding.cardManualSource.strokeColor = if (sensorSelected) outline else primary
        binding.tvManualSource.setTextColor(if (sensorSelected) unselectedText else selectedText)

        binding.cardSensorReadingBadge.isVisible = sensorSelected
        binding.inputTemperature.isFocusable = !sensorSelected
        binding.inputTemperature.isFocusableInTouchMode = !sensorSelected
        binding.inputTemperature.isClickable = !sensorSelected
        binding.inputTemperatureLayout.defaultHintTextColor = ColorStateList.valueOf(
            unselectedText
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private enum class TemperatureSource {
        SENSOR,
        MANUAL
    }

    private companion object {
        const val ARG_TANK_ID = "tankId"
        const val DATE_PATTERN = "d MMM yyyy"
        const val TIME_PATTERN = "HH:mm"
    }
}

package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisAddBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import java.util.Calendar

class TankHealthAnalysisAddFragment :
    Fragment(R.layout.fragment_tank_health_analysis_add) {

    private val args: TankHealthAnalysisAddFragmentArgs by navArgs()

    private var _binding: FragmentTankHealthAnalysisAddBinding? = null
    private val binding get() = _binding!!

    private val selectedCalendar: Calendar = Calendar.getInstance().apply {
        set(
            INITIAL_YEAR,
            Calendar.SEPTEMBER,
            INITIAL_DAY,
            INITIAL_HOUR,
            INITIAL_MINUTE,
            0
        )
        set(Calendar.MILLISECOND, 0)
    }
    private var temperatureSource: TemperatureSource = TemperatureSource.SENSOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthAnalysisAddFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisAddBinding.bind(view)

        setupHeader()
        setupPickerResultListeners()
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

    private fun setupPickerResultListeners() {
        childFragmentManager.setFragmentResultListener(
            DATE_PICKER_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppDatePickerDialogFragment.RESULT_KEY) !=
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }

            selectedCalendar.timeInMillis = result.getLong(
                AppDatePickerDialogFragment.RESULT_MILLIS
            )
            renderMeasurementTime()
        }

        childFragmentManager.setFragmentResultListener(
            TIME_PICKER_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppTimePickerDialogFragment.RESULT_KEY) !=
                AppTimePickerDialogFragment.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }

            selectedCalendar.timeInMillis = result.getLong(
                AppTimePickerDialogFragment.RESULT_MILLIS
            )
            selectedCalendar.set(Calendar.SECOND, 0)
            selectedCalendar.set(Calendar.MILLISECOND, 0)
            renderMeasurementTime()
        }
    }

    private fun setupMeasurementTime() {
        binding.measurementTimeSection.cardDate.setOnClickListener {
            AppDatePickerDialogFragment.show(
                fragmentManager = childFragmentManager,
                requestKey = DATE_PICKER_REQUEST_KEY,
                initialMillis = selectedCalendar.timeInMillis
            )
        }

        binding.measurementTimeSection.cardTime.setOnClickListener {
            AppTimePickerDialogFragment.show(
                fragmentManager = childFragmentManager,
                requestKey = TIME_PICKER_REQUEST_KEY,
                initialMillis = selectedCalendar.timeInMillis
            )
        }
    }

    private fun setupTemperatureSource() {
        binding.sensorSection.cardSensorSource.setOnClickListener {
            temperatureSource = TemperatureSource.SENSOR
            renderTemperatureSource()
        }
        binding.sensorSection.cardManualSource.setOnClickListener {
            temperatureSource = TemperatureSource.MANUAL
            renderTemperatureSource()
        }
    }

    private fun setupNavigation() {
        binding.btnHistory.setOnClickListener {
            findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.tankHealthAnalysisAddFragment,
                directions = TankHealthAnalysisAddFragmentDirections
                    .actionTankHealthAnalysisAddFragmentToTankHealthAnalysisHistoryFragment(
                        args.tankId
                    )
            )
        }

        // Persistence is intentionally deferred to the data-integration stage.
        binding.btnSaveAnalysis.setOnClickListener { }
    }

    private fun renderMeasurementTime() {
        binding.measurementTimeSection.tvDateValue.text = LocaleFormatter.formatDate(
            requireContext(),
            selectedCalendar.timeInMillis
        )
        binding.measurementTimeSection.tvTimeValue.text = LocaleFormatter.formatTime(
            requireContext(),
            selectedCalendar.timeInMillis
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
        binding.sensorSection.cardSensorSource.setCardBackgroundColor(
            if (sensorSelected) primary else transparent
        )
        binding.sensorSection.cardSensorSource.strokeColor = if (sensorSelected) primary else outline
        binding.sensorSection.tvSensorSource.setTextColor(if (sensorSelected) selectedText else unselectedText)

        binding.sensorSection.cardManualSource.setCardBackgroundColor(
            if (sensorSelected) transparent else primary
        )
        binding.sensorSection.cardManualSource.strokeColor = if (sensorSelected) outline else primary
        binding.sensorSection.tvManualSource.setTextColor(if (sensorSelected) unselectedText else selectedText)

        binding.sensorSection.cardSensorReadingBadge.isVisible = sensorSelected
        binding.sensorSection.inputTemperature.isFocusable = !sensorSelected
        binding.sensorSection.inputTemperature.isFocusableInTouchMode = !sensorSelected
        binding.sensorSection.inputTemperature.isClickable = !sensorSelected
        binding.sensorSection.inputTemperatureLayout.defaultHintTextColor = ColorStateList.valueOf(
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
        const val INITIAL_YEAR = 2026
        const val INITIAL_DAY = 26
        const val INITIAL_HOUR = 14
        const val INITIAL_MINUTE = 10
        const val DATE_PICKER_REQUEST_KEY = "tank_health_analysis_date_picker"
        const val TIME_PICKER_REQUEST_KEY = "tank_health_analysis_time_picker"
    }
}

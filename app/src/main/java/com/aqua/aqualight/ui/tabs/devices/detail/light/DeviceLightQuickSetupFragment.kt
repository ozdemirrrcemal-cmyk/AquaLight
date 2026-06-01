package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightQuickSetupBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_TITLE
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_PROGRAM_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_PROGRAM_NAME
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.LightQuickSetupDays
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.LightQuickSetupDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupChannelBalancePreset
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

class DeviceLightQuickSetupFragment :
    Fragment(R.layout.fragment_device_light_quick_setup) {

    private var _binding: FragmentDeviceLightQuickSetupBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceTitle: String
        get() = requireArguments()
            .getString(ARG_DEVICE_TITLE)
            .orEmpty()

    private var draft: LightQuickSetupDraft = LightQuickSetupDraft()

    private var isProgrammaticSliderChange = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceLightQuickSetupBinding.bind(view)

        setupHeader()
        configureSliderRanges()
        setupSliders()
        setupClicks()
        renderState()
    }

    private fun setupHeader() = with(binding.deviceHeader) {
        tvTitle.text = getString(R.string.light_quick_setup_title)

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        headerActionsContainer.visibility = View.GONE
        btnActionOne.visibility = View.GONE
        btnActionTwo.visibility = View.GONE
        btnActionThree.visibility = View.GONE
    }

    private fun configureSliderRanges() = with(binding) {
        sliderRampDuration.valueFrom = RAMP_MINUTES_MIN.toFloat()
        sliderRampDuration.valueTo = RAMP_MINUTES_MAX.toFloat()
        sliderRampDuration.stepSize = RAMP_STEP_MINUTES.toFloat()

        sliderPeakIntensity.valueFrom = PEAK_INTENSITY_MIN.toFloat()
        sliderPeakIntensity.valueTo = PEAK_INTENSITY_MAX.toFloat()
        sliderPeakIntensity.stepSize = 1f
    }

    private fun setupSliders() = with(binding) {
        sliderRampDuration.addOnChangeListener { _, value, _ ->
            if (isProgrammaticSliderChange) {
                return@addOnChangeListener
            }

            draft = draft.copy(
                rampMinutes = value.roundToInt()
            )

            renderRampState()
            renderPreviewState()
        }

        sliderPeakIntensity.addOnChangeListener { _, value, _ ->
            if (isProgrammaticSliderChange) {
                return@addOnChangeListener
            }

            draft = draft.copy(
                peakIntensityPercent =
                    value
                        .roundToInt()
                        .coerceIn(
                            PEAK_INTENSITY_MIN,
                            PEAK_INTENSITY_MAX
                        )
            )

            renderPeakState()
            renderPreviewState()
        }
    }

    private fun setupClicks() = with(binding) {
        btnSunriseStart.setOnClickListener {
            showTimePickerSheet(
                titleRes = R.string.light_quick_setup_sunrise_start,
                initialMinutes = draft.sunriseStartMinutes,
                onTimeSelected = { selectedMinutes ->
                    draft = draft.copy(
                        sunriseStartMinutes = selectedMinutes
                    )

                    renderTimeState()
                    renderPreviewState()
                }
            )
        }

        btnSunsetEnd.setOnClickListener {
            showTimePickerSheet(
                titleRes = R.string.light_quick_setup_sunset_end,
                initialMinutes = draft.sunsetEndMinutes,
                onTimeSelected = { selectedMinutes ->
                    draft = draft.copy(
                        sunsetEndMinutes = selectedMinutes
                    )

                    renderTimeState()
                    renderPreviewState()
                }
            )
        }

        chipDayMon.setOnClickListener {
            toggleDay(
                day = LightQuickSetupDays.MONDAY
            )
        }

        chipDayTue.setOnClickListener {
            toggleDay(
                day = LightQuickSetupDays.TUESDAY
            )
        }

        chipDayWed.setOnClickListener {
            toggleDay(
                day = LightQuickSetupDays.WEDNESDAY
            )
        }

        chipDayThu.setOnClickListener {
            toggleDay(
                day = LightQuickSetupDays.THURSDAY
            )
        }

        chipDayFri.setOnClickListener {
            toggleDay(
                day = LightQuickSetupDays.FRIDAY
            )
        }

        chipDaySat.setOnClickListener {
            toggleDay(
                day = LightQuickSetupDays.SATURDAY
            )
        }

        chipDaySun.setOnClickListener {
            toggleDay(
                day = LightQuickSetupDays.SUNDAY
            )
        }

        btnRamp30.setOnClickListener {
            setRampMinutes(
                minutes = 30
            )
        }

        btnRamp60.setOnClickListener {
            setRampMinutes(
                minutes = 60
            )
        }

        btnRamp90.setOnClickListener {
            setRampMinutes(
                minutes = 90
            )
        }

        chipBalanceNatural.setOnClickListener {
            setBalancePreset(
                preset = QuickSetupChannelBalancePreset.NATURAL
            )
        }

        chipBalancePlant.setOnClickListener {
            setBalancePreset(
                preset = QuickSetupChannelBalancePreset.PLANT
            )
        }

        chipBalanceWarm.setOnClickListener {
            setBalancePreset(
                preset = QuickSetupChannelBalancePreset.WARM
            )
        }

        chipBalanceBlue.setOnClickListener {
            setBalancePreset(
                preset = QuickSetupChannelBalancePreset.BLUE
            )
        }

        btnCreateQuickProgram.setOnClickListener {
            openGeneratedProgram()
        }
    }

    private fun renderState() {
        renderTimeState()
        renderRampState()
        renderPeakState()
        renderDayChips()
        renderBalanceState()
        renderPreviewState()
    }

    private fun renderTimeState() = with(binding) {
        tvSunriseStartTime.text =
            minutesToTime(
                minutes = draft.sunriseStartMinutes
            )

        tvSunsetEndTime.text =
            minutesToTime(
                minutes = draft.sunsetEndMinutes
            )
    }

    private fun renderRampState() = with(binding) {
        tvRampDurationValue.text =
            getString(
                R.string.light_quick_setup_ramp_value_format,
                draft.rampMinutes
            )

        isProgrammaticSliderChange = true

        try {
            sliderRampDuration.value = draft.rampMinutes.toFloat()
        } finally {
            isProgrammaticSliderChange = false
        }

        applyTextChipState(
            chip = btnRamp30,
            selected = draft.rampMinutes == 30
        )

        applyTextChipState(
            chip = btnRamp60,
            selected = draft.rampMinutes == 60
        )

        applyTextChipState(
            chip = btnRamp90,
            selected = draft.rampMinutes == 90
        )
    }

    private fun renderPeakState() = with(binding) {
        tvPeakIntensityValue.text =
            getString(
                R.string.common_percent_value,
                draft.peakIntensityPercent
            )

        isProgrammaticSliderChange = true

        try {
            sliderPeakIntensity.value = draft.peakIntensityPercent.toFloat()
        } finally {
            isProgrammaticSliderChange = false
        }
    }

    private fun renderDayChips() = with(binding) {
        renderDayChip(
            chip = chipDayMon,
            day = LightQuickSetupDays.MONDAY
        )

        renderDayChip(
            chip = chipDayTue,
            day = LightQuickSetupDays.TUESDAY
        )

        renderDayChip(
            chip = chipDayWed,
            day = LightQuickSetupDays.WEDNESDAY
        )

        renderDayChip(
            chip = chipDayThu,
            day = LightQuickSetupDays.THURSDAY
        )

        renderDayChip(
            chip = chipDayFri,
            day = LightQuickSetupDays.FRIDAY
        )

        renderDayChip(
            chip = chipDaySat,
            day = LightQuickSetupDays.SATURDAY
        )

        renderDayChip(
            chip = chipDaySun,
            day = LightQuickSetupDays.SUNDAY
        )
    }

    private fun renderDayChip(
        chip: TextView,
        day: Int
    ) {
        applyTextChipState(
            chip = chip,
            selected = draft.selectedDays.contains(day)
        )
    }

    private fun renderBalanceState() = with(binding) {
        val selectedPreset = draft.balancePreset

        applyTextChipState(
            chip = chipBalanceNatural,
            selected = selectedPreset == QuickSetupChannelBalancePreset.NATURAL
        )

        applyTextChipState(
            chip = chipBalancePlant,
            selected = selectedPreset == QuickSetupChannelBalancePreset.PLANT
        )

        applyTextChipState(
            chip = chipBalanceWarm,
            selected = selectedPreset == QuickSetupChannelBalancePreset.WARM
        )

        applyTextChipState(
            chip = chipBalanceBlue,
            selected = selectedPreset == QuickSetupChannelBalancePreset.BLUE
        )

        tvQuickChannelSummary.text =
            channelSummaryLabel(
                preset = selectedPreset
            )
    }

    private fun renderPreviewState() = with(binding) {
        val sunriseStartTime =
            minutesToTime(
                minutes = draft.sunriseStartMinutes
            )

        val peakStartTime =
            minutesToTime(
                minutes =
                    wrapMinutes(
                        minutes = draft.sunriseStartMinutes + draft.rampMinutes
                    )
            )

        val sunsetRampStartTime =
            minutesToTime(
                minutes =
                    wrapMinutes(
                        minutes = draft.sunsetEndMinutes - draft.rampMinutes
                    )
            )

        val sunsetEndTime =
            minutesToTime(
                minutes = draft.sunsetEndMinutes
            )

        tvQuickPreviewSummary.text =
            getString(
                R.string.light_quick_setup_preview_summary_format,
                sunriseStartTime,
                peakStartTime,
                sunsetRampStartTime,
                sunsetEndTime,
                selectedDaysLabel()
            )

        tvQuickChannelSummary.text =
            channelSummaryLabel(
                preset = draft.balancePreset
            )
    }

    private fun toggleDay(
        day: Int
    ) {
        val updatedDays =
            draft.selectedDays.toMutableSet()

        if (updatedDays.contains(day)) {
            if (updatedDays.size == 1) {
                showMessage(
                    message = getString(
                        R.string.light_quick_setup_error_one_day_required
                    )
                )

                return
            }

            updatedDays.remove(day)
        } else {
            updatedDays.add(day)
        }

        draft = draft.copy(
            selectedDays = updatedDays.toSet()
        )

        renderDayChips()
        renderPreviewState()
    }

    private fun setRampMinutes(
        minutes: Int
    ) {
        draft = draft.copy(
            rampMinutes =
                minutes.coerceIn(
                    RAMP_MINUTES_MIN,
                    RAMP_MINUTES_MAX
                )
        )

        renderRampState()
        renderPreviewState()
    }

    private fun setBalancePreset(
        preset: QuickSetupChannelBalancePreset
    ) {
        draft = draft.copy(
            balancePreset = preset
        )

        renderBalanceState()
        renderPreviewState()
    }

    private fun showTimePickerSheet(
        @StringRes titleRes: Int,
        initialMinutes: Int,
        onTimeSelected: (Int) -> Unit
    ) {
        val dialog = BottomSheetDialog(requireContext())

        val sheetView =
            layoutInflater.inflate(
                R.layout.bottom_sheet_light_time_picker,
                null
            )

        val tvTitle =
            sheetView.findViewById<TextView>(
                R.id.tvTimePickerTitle
            )

        val tvSelectedTime =
            sheetView.findViewById<TextView>(
                R.id.tvSelectedLightTime
            )

        val btnMinusHour =
            sheetView.findViewById<TextView>(
                R.id.btnLightTimeMinusHour
            )

        val btnMinusStep =
            sheetView.findViewById<TextView>(
                R.id.btnLightTimeMinusStep
            )

        val btnPlusStep =
            sheetView.findViewById<TextView>(
                R.id.btnLightTimePlusStep
            )

        val btnPlusHour =
            sheetView.findViewById<TextView>(
                R.id.btnLightTimePlusHour
            )

        val btnSave =
            sheetView.findViewById<TextView>(
                R.id.btnLightTimeSave
            )

        val btnCancel =
            sheetView.findViewById<TextView>(
                R.id.btnLightTimeCancel
            )

        var selectedMinutes =
            wrapMinutes(
                minutes = initialMinutes
            )

        fun renderSelectedTime() {
            tvSelectedTime.text =
                minutesToTime(
                    minutes = selectedMinutes
                )
        }

        tvTitle.text = getString(titleRes)
        renderSelectedTime()

        btnMinusHour.setOnClickListener {
            selectedMinutes =
                wrapMinutes(
                    minutes = selectedMinutes - MINUTES_IN_HOUR
                )

            renderSelectedTime()
        }

        btnMinusStep.setOnClickListener {
            selectedMinutes =
                wrapMinutes(
                    minutes = selectedMinutes - TIME_STEP_MINUTES
                )

            renderSelectedTime()
        }

        btnPlusStep.setOnClickListener {
            selectedMinutes =
                wrapMinutes(
                    minutes = selectedMinutes + TIME_STEP_MINUTES
                )

            renderSelectedTime()
        }

        btnPlusHour.setOnClickListener {
            selectedMinutes =
                wrapMinutes(
                    minutes = selectedMinutes + MINUTES_IN_HOUR
                )

            renderSelectedTime()
        }

        btnSave.setOnClickListener {
            dialog.dismiss()

            onTimeSelected(
                selectedMinutes
            )
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun selectedDaysLabel(): String {
        return when (draft.selectedDays) {
            LightQuickSetupDays.all -> {
                getString(R.string.light_quick_setup_days_every_day)
            }

            LightQuickSetupDays.weekdays -> {
                getString(R.string.light_quick_setup_days_weekdays)
            }

            LightQuickSetupDays.weekend -> {
                getString(R.string.light_quick_setup_days_weekend)
            }

            else -> {
                getString(
                    R.string.light_quick_setup_days_count_format,
                    draft.selectedDays.size
                )
            }
        }
    }

    private fun channelSummaryLabel(
        preset: QuickSetupChannelBalancePreset
    ): String {
        return getString(
            R.string.light_quick_setup_channel_summary_format,
            preset.red,
            preset.green,
            preset.blue,
            preset.white
        )
    }

    private fun openGeneratedProgram() {
        findNavController().navigate(
            R.id.action_deviceLightQuickSetupFragment_to_deviceLightProgramEditorFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle,
                ARG_PROGRAM_ID to null,
                ARG_PROGRAM_NAME to getString(
                    R.string.light_quick_setup_generated_program_name
                )
            )
        )
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes =
            wrapMinutes(
                minutes = minutes
            )

        val hour = safeMinutes / MINUTES_IN_HOUR
        val minute = safeMinutes % MINUTES_IN_HOUR

        return "%02d:%02d".format(
            hour,
            minute
        )
    }

    private fun wrapMinutes(
        minutes: Int
    ): Int {
        return ((minutes % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY
    }

    private fun applyTextChipState(
        chip: TextView,
        selected: Boolean
    ) {
        chip.setBackgroundResource(
            if (selected) {
                R.drawable.bg_light_editor_chip_selected
            } else {
                R.drawable.bg_light_editor_chip_unselected
            }
        )

        chip.setTextColor(
            color(
                if (selected) {
                    R.color.background_color
                } else {
                    R.color.settings_text_secondary
                }
            )
        )
    }

    private fun color(
        @ColorRes colorRes: Int
    ): Int {
        return requireContext().getColor(
            colorRes
        )
    }

    private fun showMessage(
        message: String
    ) {
        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    private companion object {
        private const val MINUTES_IN_HOUR = 60
        private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR

        private const val TIME_STEP_MINUTES = 15

        private const val RAMP_MINUTES_MIN = 15
        private const val RAMP_MINUTES_MAX = 120
        private const val RAMP_STEP_MINUTES = 15

        private const val PEAK_INTENSITY_MIN = 10
        private const val PEAK_INTENSITY_MAX = 100
    }
}
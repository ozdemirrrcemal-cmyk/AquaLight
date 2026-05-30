package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramEditorBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

class DeviceLightProgramEditorFragment : Fragment(R.layout.fragment_device_light_program_editor) {

    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val programName: String
        get() = requireArguments().getString(ARG_PROGRAM_NAME) ?: DEFAULT_PROGRAM_NAME

    private var isProMode: Boolean = false

    private val customRepeatDays = mutableSetOf(
        DAY_MON,
        DAY_TUE,
        DAY_WED,
        DAY_THU,
        DAY_FRI,
        DAY_SAT,
        DAY_SUN
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramEditorBinding.bind(view)

        configureSliderRanges()
        renderDummyState()
        setupSliders()
        setupClicks()
    }

    private fun configureSliderRanges() = with(binding) {
        listOf(
            sliderProgramRed,
            sliderProgramGreen,
            sliderProgramBlue,
            sliderProgramWhite
        ).forEach { slider ->
            slider.valueFrom = 0f
            slider.valueTo = 100f
            slider.stepSize = 1f
        }
    }

    private fun renderDummyState() = with(binding) {
        tvProgramEditorTitle.text = programName
        tvProgramEditorSubtitle.text = "09:00 → 19:15 · 10h 15m photoperiod"

        sliderProgramRed.value = 80f
        sliderProgramGreen.value = 84f
        sliderProgramBlue.value = 79f
        sliderProgramWhite.value = 65f

        updateChannelValue(tvProgramRedValue, "Red", sliderProgramRed.value)
        updateChannelValue(tvProgramGreenValue, "Green", sliderProgramGreen.value)
        updateChannelValue(tvProgramBlueValue, "Blue", sliderProgramBlue.value)
        updateChannelValue(tvProgramWhiteValue, "White", sliderProgramWhite.value)

        tvAcclimationValue.text = "Off"

        setRepeatChipState("Every")
        setSimpleMode()
    }

    private fun setupSliders() = with(binding) {
        bindChannelSlider(sliderProgramRed, tvProgramRedValue, "Red")
        bindChannelSlider(sliderProgramGreen, tvProgramGreenValue, "Green")
        bindChannelSlider(sliderProgramBlue, tvProgramBlueValue, "Blue")
        bindChannelSlider(sliderProgramWhite, tvProgramWhiteValue, "White")
    }

    private fun bindChannelSlider(
        slider: Slider,
        valueView: TextView,
        label: String
    ) {
        slider.addOnChangeListener { _, value, _ ->
            updateChannelValue(
                valueView = valueView,
                label = label,
                value = value
            )
        }
    }

    private fun updateChannelValue(
        valueView: TextView,
        label: String,
        value: Float
    ) {
        valueView.text = "$label ${value.roundToInt()}%"
    }

    private fun setupClicks() = with(binding) {
        btnProgramEditorBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnSimpleMode.setOnClickListener {
            setSimpleMode()
        }

        btnProMode.setOnClickListener {
            setProMode()
        }

        btnProgramPreviewTop.setOnClickListener {
            showMessage("Preview day simulation will be added")
        }

        btnProgramSaveTop.setOnClickListener {
            showMessage("Program save will be connected later")
        }

        btnAddCurvePoint.setOnClickListener {
            showPointEditor(
                label = "New point",
                time = "11:00",
                intensity = 60,
                canDelete = false
            )
        }

        pointStart.setOnClickListener {
            showPointEditor("Sunrise start", "08:00", 0)
        }

        pointRampUp.setOnClickListener {
            showPointEditor("Ramp complete", "10:00", 80)
        }

        pointPeakStart.setOnClickListener {
            showPointEditor("Peak start", "12:00", 100)
        }

        pointPeakEnd.setOnClickListener {
            showPointEditor("Peak end", "16:00", 100)
        }

        pointEnd.setOnClickListener {
            showPointEditor("Lights off", "20:00", 0)
        }

        rowPointStart.setOnClickListener {
            showPointEditor("Sunrise start", "08:00", 0)
        }

        rowPointRamp.setOnClickListener {
            showPointEditor("Ramp complete", "10:00", 80)
        }

        rowPointPeakStart.setOnClickListener {
            showPointEditor("Peak start", "12:00", 100)
        }

        rowPointPeakEnd.setOnClickListener {
            showPointEditor("Peak end", "16:00", 100)
        }

        rowPointEnd.setOnClickListener {
            showPointEditor("Lights off", "20:00", 0)
        }

        chipRepeatEveryDay.setOnClickListener {
            customRepeatDays.clear()
            customRepeatDays.addAll(
                listOf(
                    DAY_MON,
                    DAY_TUE,
                    DAY_WED,
                    DAY_THU,
                    DAY_FRI,
                    DAY_SAT,
                    DAY_SUN
                )
            )
            setRepeatChipState("Every")
            showMessage("Repeat: Every day")
        }

        chipRepeatWeekdays.setOnClickListener {
            customRepeatDays.clear()
            customRepeatDays.addAll(
                listOf(
                    DAY_MON,
                    DAY_TUE,
                    DAY_WED,
                    DAY_THU,
                    DAY_FRI
                )
            )
            setRepeatChipState("Weekdays")
            showMessage("Repeat: Weekdays")
        }

        chipRepeatWeekend.setOnClickListener {
            customRepeatDays.clear()
            customRepeatDays.addAll(
                listOf(
                    DAY_SAT,
                    DAY_SUN
                )
            )
            setRepeatChipState("Weekend")
            showMessage("Repeat: Weekend")
        }

        chipRepeatCustom.setOnClickListener {
            showCustomDayPickerSheet()
        }

        chipRampLinear.setOnClickListener {
            showMessage("Ramp smoothing: Linear")
        }

        chipRampSoft.setOnClickListener {
            showMessage("Ramp smoothing: Soft")
        }

        chipRampNatural.setOnClickListener {
            showMessage("Ramp smoothing: Natural")
        }

        rowAcclimation.setOnClickListener {
            showAcclimationSettingsSheet()
        }

        rowCloudSimulation.setOnClickListener {
            showMessage("Cloud simulation will be added later")
        }

        btnPreviewDay.setOnClickListener {
            showMessage("Preview day simulation will be added")
        }

        btnSaveProgram.setOnClickListener {
            showMessage("Program save will be connected after UI is complete")
        }
    }

    private fun showPointEditor(
        label: String,
        time: String,
        intensity: Int,
        canDelete: Boolean = true
    ) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(
            R.layout.bottom_sheet_light_point_editor,
            null
        )

        val tvPointLabel = sheetView.findViewById<TextView>(R.id.tvPointLabel)
        val tvPointTime = sheetView.findViewById<TextView>(R.id.tvPointTime)
        val tvPointIntensityValue =
            sheetView.findViewById<TextView>(R.id.tvPointIntensityValue)
        val sliderPointIntensity =
            sheetView.findViewById<Slider>(R.id.sliderPointIntensity)

        val btnPointTimeMinus =
            sheetView.findViewById<TextView>(R.id.btnPointTimeMinus)
        val btnPointTimePlus =
            sheetView.findViewById<TextView>(R.id.btnPointTimePlus)
        val btnPointSave =
            sheetView.findViewById<TextView>(R.id.btnPointSave)
        val btnPointDelete =
            sheetView.findViewById<TextView>(R.id.btnPointDelete)
        val btnPointCancel =
            sheetView.findViewById<TextView>(R.id.btnPointCancel)

        var selectedMinutes = timeToMinutes(time)
        var selectedIntensity = intensity.coerceIn(0, 100)

        tvPointLabel.text = label
        tvPointTime.text = minutesToTime(selectedMinutes)
        tvPointIntensityValue.text = "$selectedIntensity%"

        sliderPointIntensity.valueFrom = 0f
        sliderPointIntensity.valueTo = 100f
        sliderPointIntensity.stepSize = 1f
        sliderPointIntensity.value = selectedIntensity.toFloat()

        btnPointDelete.visibility = if (canDelete) {
            View.VISIBLE
        } else {
            View.GONE
        }

        btnPointTimeMinus.setOnClickListener {
            selectedMinutes = (selectedMinutes - POINT_STEP_MINUTES).coerceAtLeast(0)
            tvPointTime.text = minutesToTime(selectedMinutes)
        }

        btnPointTimePlus.setOnClickListener {
            selectedMinutes = (selectedMinutes + POINT_STEP_MINUTES)
                .coerceAtMost(MINUTES_IN_DAY - POINT_STEP_MINUTES)
            tvPointTime.text = minutesToTime(selectedMinutes)
        }

        sliderPointIntensity.addOnChangeListener { _, value, _ ->
            selectedIntensity = value.roundToInt()
            tvPointIntensityValue.text = "$selectedIntensity%"
        }

        btnPointSave.setOnClickListener {
            dialog.dismiss()
            showMessage(
                "Point saved: ${minutesToTime(selectedMinutes)} · $selectedIntensity%"
            )
        }

        btnPointDelete.setOnClickListener {
            dialog.dismiss()
            showMessage("Point deleted: $label")
        }

        btnPointCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showAcclimationSettingsSheet() = with(binding) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(
            R.layout.bottom_sheet_light_acclimation_settings,
            null
        )

        val switchEnabled =
            sheetView.findViewById<SwitchMaterial>(R.id.switchAcclimationEnabled)
        val tvDurationValue =
            sheetView.findViewById<TextView>(R.id.tvAcclimationDurationValue)
        val tvStartValue =
            sheetView.findViewById<TextView>(R.id.tvAcclimationStartValue)
        val tvSummary =
            sheetView.findViewById<TextView>(R.id.tvAcclimationSummary)
        val sliderStart =
            sheetView.findViewById<Slider>(R.id.sliderAcclimationStart)

        val chip3Days =
            sheetView.findViewById<TextView>(R.id.chipAcclimation3Days)
        val chip7Days =
            sheetView.findViewById<TextView>(R.id.chipAcclimation7Days)
        val chip14Days =
            sheetView.findViewById<TextView>(R.id.chipAcclimation14Days)

        val btnSave =
            sheetView.findViewById<TextView>(R.id.btnAcclimationSave)
        val btnCancel =
            sheetView.findViewById<TextView>(R.id.btnAcclimationCancel)

        var enabled = tvAcclimationValue.text.toString() != "Off"
        var selectedDays = extractAcclimationDays(tvAcclimationValue.text.toString())
        var startIntensity = extractAcclimationStartIntensity(
            tvAcclimationValue.text.toString()
        ).coerceIn(20, 80)

        switchEnabled.isChecked = enabled
        tvDurationValue.text = "$selectedDays days"

        sliderStart.valueFrom = 20f
        sliderStart.valueTo = 80f
        sliderStart.stepSize = 5f
        sliderStart.value = startIntensity.toFloat()

        tvStartValue.text = "$startIntensity%"

        fun updateSummary() {
            tvSummary.text = if (enabled) {
                "Starts at $startIntensity% and gradually reaches full program intensity over $selectedDays days."
            } else {
                "Acclimation is disabled. The program will run at normal intensity."
            }
        }

        fun setDuration(
            days: Int
        ) {
            selectedDays = days
            tvDurationValue.text = "$selectedDays days"
            updateSummary()
        }

        updateSummary()

        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            enabled = isChecked
            updateSummary()
        }

        chip3Days.setOnClickListener {
            setDuration(3)
        }

        chip7Days.setOnClickListener {
            setDuration(7)
        }

        chip14Days.setOnClickListener {
            setDuration(14)
        }

        sliderStart.addOnChangeListener { _, value, _ ->
            startIntensity = value.roundToInt()
            tvStartValue.text = "$startIntensity%"
            updateSummary()
        }

        btnSave.setOnClickListener {
            tvAcclimationValue.text = if (enabled) {
                "$selectedDays days · Start $startIntensity%"
            } else {
                "Off"
            }

            dialog.dismiss()

            showMessage(
                if (enabled) {
                    "Acclimation enabled for $selectedDays days"
                } else {
                    "Acclimation disabled"
                }
            )
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showCustomDayPickerSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(
            R.layout.bottom_sheet_light_day_picker,
            null
        )

        val selectedDays = customRepeatDays.toMutableSet()

        val chipMon = sheetView.findViewById<TextView>(R.id.chipPickerMon)
        val chipTue = sheetView.findViewById<TextView>(R.id.chipPickerTue)
        val chipWed = sheetView.findViewById<TextView>(R.id.chipPickerWed)
        val chipThu = sheetView.findViewById<TextView>(R.id.chipPickerThu)
        val chipFri = sheetView.findViewById<TextView>(R.id.chipPickerFri)
        val chipSat = sheetView.findViewById<TextView>(R.id.chipPickerSat)
        val chipSun = sheetView.findViewById<TextView>(R.id.chipPickerSun)

        val tvSummary = sheetView.findViewById<TextView>(R.id.tvDayPickerSummary)

        val btnWeekdays =
            sheetView.findViewById<TextView>(R.id.btnDayPickerWeekdays)
        val btnWeekend =
            sheetView.findViewById<TextView>(R.id.btnDayPickerWeekend)
        val btnEveryDay =
            sheetView.findViewById<TextView>(R.id.btnDayPickerEveryDay)
        val btnSave =
            sheetView.findViewById<TextView>(R.id.btnDayPickerSave)
        val btnCancel =
            sheetView.findViewById<TextView>(R.id.btnDayPickerCancel)

        fun renderChip(
            chip: TextView,
            day: Int
        ) {
            val isSelected = selectedDays.contains(day)

            if (isSelected) {
                chip.setBackgroundColor(
                    requireContext().getColor(R.color.light_accent)
                )
                chip.setTextColor(
                    requireContext().getColor(R.color.background_color)
                )
            } else {
                chip.setBackgroundResource(android.R.color.transparent)
                chip.setTextColor(
                    requireContext().getColor(R.color.settings_text_secondary)
                )
            }
        }

        fun selectedDaysLabel(): String {
            return when {
                selectedDays.size == 7 -> {
                    "Every day selected"
                }

                selectedDays == setOf(DAY_MON, DAY_TUE, DAY_WED, DAY_THU, DAY_FRI) -> {
                    "Weekdays selected"
                }

                selectedDays == setOf(DAY_SAT, DAY_SUN) -> {
                    "Weekend selected"
                }

                else -> {
                    "${selectedDays.size} days selected"
                }
            }
        }

        fun renderAll() {
            renderChip(chipMon, DAY_MON)
            renderChip(chipTue, DAY_TUE)
            renderChip(chipWed, DAY_WED)
            renderChip(chipThu, DAY_THU)
            renderChip(chipFri, DAY_FRI)
            renderChip(chipSat, DAY_SAT)
            renderChip(chipSun, DAY_SUN)

            tvSummary.text = selectedDaysLabel()
        }

        fun toggleDay(
            day: Int
        ) {
            if (selectedDays.contains(day)) {
                if (selectedDays.size == 1) {
                    showMessage("At least one day must be selected")
                    return
                }

                selectedDays.remove(day)
            } else {
                selectedDays.add(day)
            }

            renderAll()
        }

        chipMon.setOnClickListener {
            toggleDay(DAY_MON)
        }

        chipTue.setOnClickListener {
            toggleDay(DAY_TUE)
        }

        chipWed.setOnClickListener {
            toggleDay(DAY_WED)
        }

        chipThu.setOnClickListener {
            toggleDay(DAY_THU)
        }

        chipFri.setOnClickListener {
            toggleDay(DAY_FRI)
        }

        chipSat.setOnClickListener {
            toggleDay(DAY_SAT)
        }

        chipSun.setOnClickListener {
            toggleDay(DAY_SUN)
        }

        btnWeekdays.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(
                listOf(
                    DAY_MON,
                    DAY_TUE,
                    DAY_WED,
                    DAY_THU,
                    DAY_FRI
                )
            )
            renderAll()
        }

        btnWeekend.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(
                listOf(
                    DAY_SAT,
                    DAY_SUN
                )
            )
            renderAll()
        }

        btnEveryDay.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(
                listOf(
                    DAY_MON,
                    DAY_TUE,
                    DAY_WED,
                    DAY_THU,
                    DAY_FRI,
                    DAY_SAT,
                    DAY_SUN
                )
            )
            renderAll()
        }

        btnSave.setOnClickListener {
            customRepeatDays.clear()
            customRepeatDays.addAll(selectedDays)

            setRepeatChipState("Custom")

            dialog.dismiss()
            showMessage(selectedDaysLabel())
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        renderAll()

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun setRepeatChipState(
        selected: String
    ) = with(binding) {
        val selectedBg = requireContext().getColor(R.color.light_accent)
        val selectedText = requireContext().getColor(R.color.background_color)
        val normalText = requireContext().getColor(R.color.settings_text_secondary)

        fun applyState(
            chip: TextView,
            isSelected: Boolean
        ) {
            if (isSelected) {
                chip.setBackgroundColor(selectedBg)
                chip.setTextColor(selectedText)
            } else {
                chip.setBackgroundResource(android.R.color.transparent)
                chip.setTextColor(normalText)
            }
        }

        applyState(chipRepeatEveryDay, selected == "Every")
        applyState(chipRepeatWeekdays, selected == "Weekdays")
        applyState(chipRepeatWeekend, selected == "Weekend")
        applyState(chipRepeatCustom, selected == "Custom")
    }

    private fun setSimpleMode() = with(binding) {
        isProMode = false

        btnSimpleMode.setBackgroundColor(
            requireContext().getColor(R.color.light_accent)
        )
        btnSimpleMode.setTextColor(
            requireContext().getColor(R.color.background_color)
        )

        btnProMode.setBackgroundResource(android.R.color.transparent)
        btnProMode.setTextColor(
            requireContext().getColor(R.color.settings_text_secondary)
        )

        tvEditorModeDescription.text =
            "Simple mode uses one main intensity curve and keeps RGB/W balance separate."

        tvCurveTitle.text = "Overall Intensity Curve"
        tvCurveSubtitle.text = "Tap a point to edit time and intensity"
        tvChannelBalanceTitle.text = "Channel Balance"
        tvChannelBalanceSubtitle.text = "Used by the simple intensity curve"
    }

    private fun setProMode() = with(binding) {
        isProMode = true

        btnProMode.setBackgroundColor(
            requireContext().getColor(R.color.light_accent)
        )
        btnProMode.setTextColor(
            requireContext().getColor(R.color.background_color)
        )

        btnSimpleMode.setBackgroundResource(android.R.color.transparent)
        btnSimpleMode.setTextColor(
            requireContext().getColor(R.color.settings_text_secondary)
        )

        tvEditorModeDescription.text =
            "Pro mode will allow separate curves for Red, Green, Blue and White channels."

        tvCurveTitle.text = "Pro Channel Curves"
        tvCurveSubtitle.text = "Separate channel curves will be connected later"
        tvChannelBalanceTitle.text = "Base Channel Values"
        tvChannelBalanceSubtitle.text = "Temporary base values until pro curves are connected"
    }

    private fun timeToMinutes(
        time: String
    ): Int {
        val parts = time.split(":")
        if (parts.size != 2) return 0

        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        return (hour * 60 + minute).coerceIn(0, MINUTES_IN_DAY - 1)
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes = minutes.coerceIn(0, MINUTES_IN_DAY - 1)
        val hour = safeMinutes / 60
        val minute = safeMinutes % 60
        return "%02d:%02d".format(hour, minute)
    }

    private fun extractAcclimationDays(
        text: String
    ): Int {
        val firstNumber = text
            .split(" ")
            .firstOrNull()
            ?.toIntOrNull()

        return firstNumber ?: 7
    }

    private fun extractAcclimationStartIntensity(
        text: String
    ): Int {
        val startPart = text
            .substringAfter("Start", "")
            .trim()
            .replace("%", "")
            .toIntOrNull()

        return startPart ?: 40
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

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_PROGRAM_NAME = "programName"

        private const val DEFAULT_PROGRAM_NAME = "Every Day Program"
        private const val POINT_STEP_MINUTES = 15
        private const val MINUTES_IN_DAY = 24 * 60

        private const val DAY_MON = 1
        private const val DAY_TUE = 2
        private const val DAY_WED = 3
        private const val DAY_THU = 4
        private const val DAY_FRI = 5
        private const val DAY_SAT = 6
        private const val DAY_SUN = 7

        fun newInstance(
            deviceId: Long,
            programName: String = DEFAULT_PROGRAM_NAME
        ): DeviceLightProgramEditorFragment {
            return DeviceLightProgramEditorFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                    putString(ARG_PROGRAM_NAME, programName)
                }
            }
        }
    }
}
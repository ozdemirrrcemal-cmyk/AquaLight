package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramEditorBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

class DeviceLightProgramEditorFragment :
    Fragment(R.layout.fragment_device_light_program_editor) {

    private var _binding: FragmentDeviceLightProgramEditorBinding? = null
    private val binding get() = _binding!!

    private val programName: String
        get() = requireArguments()
            .getString(ARG_PROGRAM_NAME)
            .orEmpty()
            .ifBlank {
                DEFAULT_PROGRAM_NAME
            }

    private var isProMode: Boolean = false
    private var selectedProChannel: ProChannel = ProChannel.RED
    private var selectedRepeatMode: RepeatMode = RepeatMode.EVERY
    private var selectedRampSmoothing: RampSmoothing = RampSmoothing.LINEAR

    private var simpleCurvePoints: MutableMap<CurvePointId, CurvePointState> =
        createSimpleCurvePoints()

    private val proChannelCurves: MutableMap<ProChannel, MutableMap<CurvePointId, CurvePointState>> =
        mutableMapOf(
            ProChannel.RED to createChannelCurvePoints(ProChannel.RED.defaultPeak),
            ProChannel.GREEN to createChannelCurvePoints(ProChannel.GREEN.defaultPeak),
            ProChannel.BLUE to createChannelCurvePoints(ProChannel.BLUE.defaultPeak),
            ProChannel.WHITE to createChannelCurvePoints(ProChannel.WHITE.defaultPeak)
        )

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

        setupHeader()
        configureSliderRanges()
        renderPreviewState()
        setupSliders()
        setupClicks()
    }

    private fun setupHeader() = with(binding.deviceHeader) {
        tvTitle.text = programName

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        headerActionsContainer.visibility = View.VISIBLE

        btnActionOne.visibility = View.VISIBLE
        btnActionOne.setImageResource(R.drawable.ic_light_program_24)
        btnActionOne.contentDescription = "Preview Program"
        btnActionOne.setOnClickListener {
            showPreviewMessage()
        }

        btnActionTwo.visibility = View.VISIBLE
        btnActionTwo.setImageResource(R.drawable.ic_check_24)
        btnActionTwo.contentDescription = "Save Program"
        btnActionTwo.setOnClickListener {
            saveProgram()
        }

        btnActionThree.visibility = View.GONE
    }

    private fun configureSliderRanges() = with(binding) {
        listOf(
            sliderProgramRed,
            sliderProgramGreen,
            sliderProgramBlue,
            sliderProgramWhite
        ).forEach { slider ->
            slider.valueFrom = 0f
            slider.valueTo = MAX_PERCENT.toFloat()
            slider.stepSize = 1f
        }
    }

    private fun renderPreviewState() = with(binding) {
        sliderProgramRed.value = ProChannel.RED.defaultPeak.toFloat()
        sliderProgramGreen.value = ProChannel.GREEN.defaultPeak.toFloat()
        sliderProgramBlue.value = ProChannel.BLUE.defaultPeak.toFloat()
        sliderProgramWhite.value = ProChannel.WHITE.defaultPeak.toFloat()

        renderChannelValues()

        tvAcclimationValue.text = "Off"

        setRepeatMode(
            mode = RepeatMode.EVERY,
            showToast = false
        )

        setRampSmoothing(
            smoothing = RampSmoothing.LINEAR,
            showToast = false
        )

        setSimpleMode()
    }

    private fun setupSliders() = with(binding) {
        bindChannelSlider(
            slider = sliderProgramRed,
            valueView = tvProgramRedValue,
            label = "Red"
        )

        bindChannelSlider(
            slider = sliderProgramGreen,
            valueView = tvProgramGreenValue,
            label = "Green"
        )

        bindChannelSlider(
            slider = sliderProgramBlue,
            valueView = tvProgramBlueValue,
            label = "Blue"
        )

        bindChannelSlider(
            slider = sliderProgramWhite,
            valueView = tvProgramWhiteValue,
            label = "White"
        )
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

    private fun setupClicks() = with(binding) {
        btnSimpleMode.setOnClickListener {
            setSimpleMode()
        }

        btnProMode.setOnClickListener {
            setProMode()
        }

        chipProRed.setOnClickListener {
            selectProChannel(
                channel = ProChannel.RED
            )
        }

        chipProGreen.setOnClickListener {
            selectProChannel(
                channel = ProChannel.GREEN
            )
        }

        chipProBlue.setOnClickListener {
            selectProChannel(
                channel = ProChannel.BLUE
            )
        }

        chipProWhite.setOnClickListener {
            selectProChannel(
                channel = ProChannel.WHITE
            )
        }

        btnAddCurvePoint.setOnClickListener {
            showNewPointEditor()
        }

        viewProgramEditorCurve.setOnClickListener {
            showPointEditor(
                pointId = CurvePointId.PEAK_START
            )
        }

        rowPointStart.setOnClickListener {
            showPointEditor(
                pointId = CurvePointId.START
            )
        }

        rowPointPeakStart.setOnClickListener {
            showPointEditor(
                pointId = CurvePointId.PEAK_START
            )
        }

        rowPointPeakEnd.setOnClickListener {
            showPointEditor(
                pointId = CurvePointId.PEAK_END
            )
        }

        rowPointEnd.setOnClickListener {
            showPointEditor(
                pointId = CurvePointId.END
            )
        }

        chipRepeatEveryDay.setOnClickListener {
            customRepeatDays.clear()
            customRepeatDays.addAll(allDays())

            setRepeatMode(
                mode = RepeatMode.EVERY,
                showToast = true
            )
        }

        chipRepeatWeekdays.setOnClickListener {
            customRepeatDays.clear()
            customRepeatDays.addAll(weekDays())

            setRepeatMode(
                mode = RepeatMode.WEEKDAYS,
                showToast = true
            )
        }

        chipRepeatWeekend.setOnClickListener {
            customRepeatDays.clear()
            customRepeatDays.addAll(weekendDays())

            setRepeatMode(
                mode = RepeatMode.WEEKEND,
                showToast = true
            )
        }

        chipRepeatCustom.setOnClickListener {
            showCustomDayPickerSheet()
        }

        chipRampLinear.setOnClickListener {
            setRampSmoothing(
                smoothing = RampSmoothing.LINEAR,
                showToast = true
            )
        }

        chipRampSoft.setOnClickListener {
            setRampSmoothing(
                smoothing = RampSmoothing.SOFT,
                showToast = true
            )
        }

        chipRampNatural.setOnClickListener {
            setRampSmoothing(
                smoothing = RampSmoothing.NATURAL,
                showToast = true
            )
        }

        rowAcclimation.setOnClickListener {
            showAcclimationSettingsSheet()
        }

        rowCloudSimulation.setOnClickListener {
            showMessage("Cloud simulation will be added later")
        }

        btnPreviewDay.setOnClickListener {
            showPreviewMessage()
        }

        btnSaveProgram.setOnClickListener {
            saveProgram()
        }
    }

    private fun setSimpleMode() = with(binding) {
        isProMode = false

        applyTextChipState(
            chip = btnSimpleMode,
            selected = true
        )

        applyTextChipState(
            chip = btnProMode,
            selected = false
        )

        proChannelSelectorRow.visibility = View.GONE
        cardProgramChannelBalance.visibility = View.VISIBLE

        tvEditorModeDescription.text =
            "One daily intensity curve with fixed RGB/W balance."

        tvCurveTitle.text = "Daily Light Curve"
        tvCurveSubtitle.text = "Tap a point or row to edit time and output"

        tvCurvePointsSubtitle.text =
            "Start, peak and end points used by the daily curve"

        tvChannelBalanceTitle.text = "Channel Balance"
        tvChannelBalanceSubtitle.text = "Used across the whole simple curve"

        renderCurrentCurve()
    }

    private fun setProMode() = with(binding) {
        isProMode = true

        applyTextChipState(
            chip = btnSimpleMode,
            selected = false
        )

        applyTextChipState(
            chip = btnProMode,
            selected = true
        )

        proChannelSelectorRow.visibility = View.VISIBLE
        cardProgramChannelBalance.visibility = View.GONE

        tvEditorModeDescription.text =
            "Each WRGB channel can have its own daily curve."

        tvCurvePointsSubtitle.text =
            "Start, peak and end points for the selected channel"

        selectProChannel(
            channel = selectedProChannel
        )
    }

    private fun selectProChannel(
        channel: ProChannel
    ) = with(binding) {
        selectedProChannel = channel

        tvCurveTitle.text = "${channel.label} Channel Curve"
        tvCurveSubtitle.text =
            "Edit time and output points for ${channel.label.lowercase()} only"

        renderProChannelChips()
        renderCurrentCurve()
    }

    private fun renderProChannelChips() = with(binding) {
        chipProRed.applyProChannelStyle(
            channel = ProChannel.RED
        )

        chipProGreen.applyProChannelStyle(
            channel = ProChannel.GREEN
        )

        chipProBlue.applyProChannelStyle(
            channel = ProChannel.BLUE
        )

        chipProWhite.applyProChannelStyle(
            channel = ProChannel.WHITE
        )
    }

    private fun renderCurrentCurve() = with(binding) {
        val curve = currentCurvePoints()

        val start = curve.getValue(CurvePointId.START)
        val peakStart = curve.getValue(CurvePointId.PEAK_START)
        val peakEnd = curve.getValue(CurvePointId.PEAK_END)
        val end = curve.getValue(CurvePointId.END)

        viewProgramEditorCurve.setProgramCurve(
            start = start.time,
            sunriseEnd = peakStart.time,
            peakEnd = peakEnd.time,
            end = end.time,
            startIntensity = start.intensity,
            sunriseEndIntensity = peakStart.intensity,
            peakEndIntensity = peakEnd.intensity,
            endIntensity = end.intensity
        )

        tvCurveStartSummary.text = start.time
        tvCurvePeakSummary.text =
            "${maxOf(peakStart.intensity, peakEnd.intensity)}%"
        tvCurveEndSummary.text = end.time

        tvPointStartTime.text = start.time
        tvPointStartLabel.text = start.label
        tvPointStartPercent.text = "${start.intensity}%"

        tvPointPeakStartTime.text = peakStart.time
        tvPointPeakStartLabel.text = peakStart.label
        tvPointPeakStartPercent.text = "${peakStart.intensity}%"

        tvPointPeakEndTime.text = peakEnd.time
        tvPointPeakEndLabel.text = peakEnd.label
        tvPointPeakEndPercent.text = "${peakEnd.intensity}%"

        tvPointEndTime.text = end.time
        tvPointEndLabel.text = end.label
        tvPointEndPercent.text = "${end.intensity}%"

        tvCurveHint.text =
            if (isProMode) {
                "${selectedProChannel.label} curve · ${peakStart.time} · Peak start · ${peakStart.intensity}%"
            } else {
                "Simple curve · ${peakStart.time} · Peak start · ${peakStart.intensity}%"
            }
    }

    private fun showPointEditor(
        pointId: CurvePointId
    ) {
        val currentPoint =
            currentCurvePoints()[pointId] ?: return

        showPointEditorSheet(
            label = currentPoint.label,
            time = currentPoint.time,
            intensity = currentPoint.intensity,
            canDelete = currentPoint.canDelete,
            onSave = { selectedTime, selectedIntensity ->
                currentCurvePoints()[pointId] =
                    currentPoint.copy(
                        time = selectedTime,
                        intensity = selectedIntensity
                    )

                renderCurrentCurve()

                showMessage(
                    "Point saved: $selectedTime · $selectedIntensity%"
                )
            },
            onDelete = {
                showMessage("Default points cannot be deleted")
            }
        )
    }

    private fun showNewPointEditor() {
        showPointEditorSheet(
            label = "New intermediate point",
            time = "11:00",
            intensity = 60,
            canDelete = false,
            onSave = { selectedTime, selectedIntensity ->
                showMessage(
                    "Intermediate point ready: $selectedTime · $selectedIntensity%"
                )
            },
            onDelete = {
                Unit
            }
        )
    }

    private fun showPointEditorSheet(
        label: String,
        time: String,
        intensity: Int,
        canDelete: Boolean,
        onSave: (String, Int) -> Unit,
        onDelete: () -> Unit
    ) {
        val dialog = BottomSheetDialog(requireContext())

        val sheetView =
            layoutInflater.inflate(
                R.layout.bottom_sheet_light_point_editor,
                null
            )

        val tvPointLabel =
            sheetView.findViewById<TextView>(R.id.tvPointLabel)

        val tvPointTime =
            sheetView.findViewById<TextView>(R.id.tvPointTime)

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
        var selectedIntensity = intensity.coerceIn(0, MAX_PERCENT)

        tvPointLabel.text = label
        tvPointTime.text = minutesToTime(selectedMinutes)
        tvPointIntensityValue.text = "$selectedIntensity%"

        sliderPointIntensity.valueFrom = 0f
        sliderPointIntensity.valueTo = MAX_PERCENT.toFloat()
        sliderPointIntensity.stepSize = 1f
        sliderPointIntensity.value = selectedIntensity.toFloat()

        btnPointDelete.visibility =
            if (canDelete) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnPointTimeMinus.setOnClickListener {
            selectedMinutes =
                (selectedMinutes - POINT_STEP_MINUTES)
                    .coerceAtLeast(0)

            tvPointTime.text = minutesToTime(selectedMinutes)
        }

        btnPointTimePlus.setOnClickListener {
            selectedMinutes =
                (selectedMinutes + POINT_STEP_MINUTES)
                    .coerceAtMost(MINUTES_IN_DAY - POINT_STEP_MINUTES)

            tvPointTime.text = minutesToTime(selectedMinutes)
        }

        sliderPointIntensity.addOnChangeListener { _, value, _ ->
            selectedIntensity = value.roundToInt()
            tvPointIntensityValue.text = "$selectedIntensity%"
        }

        btnPointSave.setOnClickListener {
            dialog.dismiss()

            onSave(
                minutesToTime(selectedMinutes),
                selectedIntensity
            )
        }

        btnPointDelete.setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        btnPointCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showAcclimationSettingsSheet() = with(binding) {
        val dialog = BottomSheetDialog(requireContext())

        val sheetView =
            layoutInflater.inflate(
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
        var startIntensity =
            extractAcclimationStartIntensity(
                text = tvAcclimationValue.text.toString()
            ).coerceIn(20, 80)

        switchEnabled.isChecked = enabled
        tvDurationValue.text = "$selectedDays days"

        sliderStart.valueFrom = 20f
        sliderStart.valueTo = 80f
        sliderStart.stepSize = 5f
        sliderStart.value = startIntensity.toFloat()

        tvStartValue.text = "$startIntensity%"

        fun updateSummary() {
            tvSummary.text =
                if (enabled) {
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
            tvAcclimationValue.text =
                if (enabled) {
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

        val sheetView =
            layoutInflater.inflate(
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

        val tvSummary =
            sheetView.findViewById<TextView>(R.id.tvDayPickerSummary)

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
                    color(R.color.light_accent)
                )
                chip.setTextColor(
                    color(R.color.background_color)
                )
            } else {
                chip.setBackgroundResource(android.R.color.transparent)
                chip.setTextColor(
                    color(R.color.settings_text_secondary)
                )
            }
        }

        fun selectedDaysLabel(): String {
            return when {
                selectedDays.size == 7 -> {
                    "Every day selected"
                }

                selectedDays == weekDays() -> {
                    "Weekdays selected"
                }

                selectedDays == weekendDays() -> {
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
            selectedDays.addAll(weekDays())
            renderAll()
        }

        btnWeekend.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(weekendDays())
            renderAll()
        }

        btnEveryDay.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(allDays())
            renderAll()
        }

        btnSave.setOnClickListener {
            customRepeatDays.clear()
            customRepeatDays.addAll(selectedDays)

            selectedRepeatMode = RepeatMode.CUSTOM
            renderRepeatChips()

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

    private fun setRepeatMode(
        mode: RepeatMode,
        showToast: Boolean
    ) {
        selectedRepeatMode = mode

        renderRepeatChips()

        if (showToast) {
            showMessage("Repeat: ${mode.label}")
        }
    }

    private fun renderRepeatChips() = with(binding) {
        applyTextChipState(
            chip = chipRepeatEveryDay,
            selected = selectedRepeatMode == RepeatMode.EVERY
        )

        applyTextChipState(
            chip = chipRepeatWeekdays,
            selected = selectedRepeatMode == RepeatMode.WEEKDAYS
        )

        applyTextChipState(
            chip = chipRepeatWeekend,
            selected = selectedRepeatMode == RepeatMode.WEEKEND
        )

        applyTextChipState(
            chip = chipRepeatCustom,
            selected = selectedRepeatMode == RepeatMode.CUSTOM
        )
    }

    private fun setRampSmoothing(
        smoothing: RampSmoothing,
        showToast: Boolean
    ) {
        selectedRampSmoothing = smoothing

        renderRampSmoothingChips()

        if (showToast) {
            showMessage("Ramp smoothing: ${smoothing.label}")
        }
    }

    private fun renderRampSmoothingChips() = with(binding) {
        applyTextChipState(
            chip = chipRampLinear,
            selected = selectedRampSmoothing == RampSmoothing.LINEAR
        )

        applyTextChipState(
            chip = chipRampSoft,
            selected = selectedRampSmoothing == RampSmoothing.SOFT
        )

        applyTextChipState(
            chip = chipRampNatural,
            selected = selectedRampSmoothing == RampSmoothing.NATURAL
        )
    }

    private fun renderChannelValues() = with(binding) {
        updateChannelValue(
            valueView = tvProgramRedValue,
            label = "Red",
            value = sliderProgramRed.value
        )

        updateChannelValue(
            valueView = tvProgramGreenValue,
            label = "Green",
            value = sliderProgramGreen.value
        )

        updateChannelValue(
            valueView = tvProgramBlueValue,
            label = "Blue",
            value = sliderProgramBlue.value
        )

        updateChannelValue(
            valueView = tvProgramWhiteValue,
            label = "White",
            value = sliderProgramWhite.value
        )
    }

    private fun updateChannelValue(
        valueView: TextView,
        label: String,
        value: Float
    ) {
        valueView.text = "$label ${value.roundToInt()}%"
    }

    private fun applyTextChipState(
        chip: TextView,
        selected: Boolean
    ) {
        if (selected) {
            chip.setBackgroundColor(
                color(R.color.light_accent)
            )
            chip.setTextColor(
                color(R.color.background_color)
            )
        } else {
            chip.setBackgroundResource(android.R.color.transparent)
            chip.setTextColor(
                color(R.color.settings_text_secondary)
            )
        }
    }

    private fun MaterialCardView.applyProChannelStyle(
        channel: ProChannel
    ) {
        val selected = selectedProChannel == channel

        setCardBackgroundColor(
            color(
                if (selected) {
                    R.color.light_accent_soft
                } else {
                    R.color.light_surface
                }
            )
        )

        strokeColor =
            color(
                if (selected) {
                    channel.colorRes
                } else {
                    R.color.light_stroke
                }
            )

        val textView = getChildAt(0) as? TextView

        textView?.setTextColor(
            color(
                if (selected) {
                    channel.colorRes
                } else {
                    R.color.settings_text_secondary
                }
            )
        )
    }

    private fun currentCurvePoints(): MutableMap<CurvePointId, CurvePointState> {
        return if (isProMode) {
            proChannelCurves.getValue(selectedProChannel)
        } else {
            simpleCurvePoints
        }
    }

    private fun createSimpleCurvePoints(): MutableMap<CurvePointId, CurvePointState> {
        return mutableMapOf(
            CurvePointId.START to CurvePointState(
                id = CurvePointId.START,
                label = "Start",
                time = "08:00",
                intensity = 0
            ),
            CurvePointId.PEAK_START to CurvePointState(
                id = CurvePointId.PEAK_START,
                label = "Peak start",
                time = "12:00",
                intensity = 100
            ),
            CurvePointId.PEAK_END to CurvePointState(
                id = CurvePointId.PEAK_END,
                label = "Peak end",
                time = "16:00",
                intensity = 100
            ),
            CurvePointId.END to CurvePointState(
                id = CurvePointId.END,
                label = "End",
                time = "20:00",
                intensity = 0
            )
        )
    }

    private fun createChannelCurvePoints(
        peak: Int
    ): MutableMap<CurvePointId, CurvePointState> {
        val safePeak = peak.coerceIn(0, MAX_PERCENT)

        return mutableMapOf(
            CurvePointId.START to CurvePointState(
                id = CurvePointId.START,
                label = "Start",
                time = "08:00",
                intensity = 0
            ),
            CurvePointId.PEAK_START to CurvePointState(
                id = CurvePointId.PEAK_START,
                label = "Peak start",
                time = "12:00",
                intensity = safePeak
            ),
            CurvePointId.PEAK_END to CurvePointState(
                id = CurvePointId.PEAK_END,
                label = "Peak end",
                time = "16:00",
                intensity = safePeak
            ),
            CurvePointId.END to CurvePointState(
                id = CurvePointId.END,
                label = "End",
                time = "20:00",
                intensity = 0
            )
        )
    }

    private fun showPreviewMessage() {
        val modeLabel =
            if (isProMode) {
                "Pro ${selectedProChannel.label}"
            } else {
                "Simple"
            }

        showMessage("$modeLabel preview day simulation will be added")
    }

    private fun saveProgram() {
        showMessage("$programName save will be connected later")
    }

    private fun timeToMinutes(
        time: String
    ): Int {
        val parts = time.split(":")

        if (parts.size != 2) {
            return 0
        }

        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        return (hour * 60 + minute)
            .coerceIn(
                0,
                MINUTES_IN_DAY - 1
            )
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes =
            minutes.coerceIn(
                0,
                MINUTES_IN_DAY - 1
            )

        val hour = safeMinutes / 60
        val minute = safeMinutes % 60

        return "%02d:%02d".format(
            hour,
            minute
        )
    }

    private fun extractAcclimationDays(
        text: String
    ): Int {
        val firstNumber =
            text.split(" ")
                .firstOrNull()
                ?.toIntOrNull()

        return firstNumber ?: 7
    }

    private fun extractAcclimationStartIntensity(
        text: String
    ): Int {
        return text
            .substringAfter(
                delimiter = "Start",
                missingDelimiterValue = ""
            )
            .trim()
            .replace(
                oldValue = "%",
                newValue = ""
            )
            .toIntOrNull()
            ?: 40
    }

    private fun allDays(): Set<Int> {
        return setOf(
            DAY_MON,
            DAY_TUE,
            DAY_WED,
            DAY_THU,
            DAY_FRI,
            DAY_SAT,
            DAY_SUN
        )
    }

    private fun weekDays(): Set<Int> {
        return setOf(
            DAY_MON,
            DAY_TUE,
            DAY_WED,
            DAY_THU,
            DAY_FRI
        )
    }

    private fun weekendDays(): Set<Int> {
        return setOf(
            DAY_SAT,
            DAY_SUN
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

    private enum class CurvePointId {
        START,
        PEAK_START,
        PEAK_END,
        END
    }

    private data class CurvePointState(
        val id: CurvePointId,
        val label: String,
        val time: String,
        val intensity: Int,
        val canDelete: Boolean = false
    )

    private enum class ProChannel(
        val label: String,
        @ColorRes val colorRes: Int,
        val defaultPeak: Int
    ) {
        RED(
            label = "Red",
            colorRes = R.color.light_red,
            defaultPeak = 80
        ),
        GREEN(
            label = "Green",
            colorRes = R.color.light_green,
            defaultPeak = 84
        ),
        BLUE(
            label = "Blue",
            colorRes = R.color.light_blue,
            defaultPeak = 79
        ),
        WHITE(
            label = "White",
            colorRes = R.color.light_white,
            defaultPeak = 65
        )
    }

    private enum class RepeatMode(
        val label: String
    ) {
        EVERY("Every day"),
        WEEKDAYS("Weekdays"),
        WEEKEND("Weekend"),
        CUSTOM("Custom")
    }

    private enum class RampSmoothing(
        val label: String
    ) {
        LINEAR("Linear"),
        SOFT("Soft"),
        NATURAL("Natural")
    }

    companion object {
        private const val ARG_PROGRAM_NAME = "programName"

        private const val DEFAULT_PROGRAM_NAME = "Every Day Program"
        private const val POINT_STEP_MINUTES = 15
        private const val MINUTES_IN_DAY = 24 * 60
        private const val MAX_PERCENT = 100

        private const val DAY_MON = 1
        private const val DAY_TUE = 2
        private const val DAY_WED = 3
        private const val DAY_THU = 4
        private const val DAY_FRI = 5
        private const val DAY_SAT = 6
        private const val DAY_SUN = 7
    }
}
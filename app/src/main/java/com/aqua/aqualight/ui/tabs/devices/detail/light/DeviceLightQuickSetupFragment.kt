package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightQuickSetupBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

class DeviceLightQuickSetupFragment :
    Fragment(R.layout.fragment_device_light_quick_setup) {

    private var _binding: FragmentDeviceLightQuickSetupBinding? = null
    private val binding get() = _binding!!

    private val lightController: DeviceLightControllerFragment?
        get() = parentFragment as? DeviceLightControllerFragment

    private var sunriseStartTime: String = "09:00"
    private var sunsetEndTime: String = "19:15"

    private var rampMinutes: Int = 60
    private var peakIntensity: Int = 100
    private var balanceName: String = "Natural"
    private var channelSummary: String = "R80  G84  B79  W65"

    private val selectedDays = mutableSetOf(
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
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceLightQuickSetupBinding.bind(view)

        configureSliderRanges()
        renderPreviewState()
        setupSliders()
        setupClicks()
    }

    private fun configureSliderRanges() = with(binding) {
        sliderRampDuration.valueFrom = 15f
        sliderRampDuration.valueTo = 120f
        sliderRampDuration.stepSize = 15f

        sliderPeakIntensity.valueFrom = 10f
        sliderPeakIntensity.valueTo = 100f
        sliderPeakIntensity.stepSize = 1f
    }

    private fun renderPreviewState() = with(binding) {
        tvSunriseStartTime.text = sunriseStartTime
        tvSunsetEndTime.text = sunsetEndTime

        sliderRampDuration.value = rampMinutes.toFloat()
        sliderPeakIntensity.value = peakIntensity.toFloat()

        renderDayChips()
        updateRampText()
        updatePeakText()
        updatePreviewText()
    }

    private fun setupSliders() = with(binding) {
        sliderRampDuration.addOnChangeListener { _, value, _ ->
            rampMinutes = value.roundToInt()
            updateRampText()
            updatePreviewText()
        }

        sliderPeakIntensity.addOnChangeListener { _, value, _ ->
            peakIntensity = value.roundToInt()
            updatePeakText()
            updatePreviewText()
        }
    }

    private fun setupClicks() = with(binding) {
        btnSunriseStart.setOnClickListener {
            showTimePickerSheet(
                title = "Sunrise Start",
                initialTime = sunriseStartTime,
                onTimeSelected = { selectedTime ->
                    sunriseStartTime = selectedTime
                    tvSunriseStartTime.text = selectedTime
                    updatePreviewText()
                }
            )
        }

        btnSunsetEnd.setOnClickListener {
            showTimePickerSheet(
                title = "Sunset End",
                initialTime = sunsetEndTime,
                onTimeSelected = { selectedTime ->
                    sunsetEndTime = selectedTime
                    tvSunsetEndTime.text = selectedTime
                    updatePreviewText()
                }
            )
        }

        chipDayMon.setOnClickListener {
            toggleDay(DAY_MON)
        }

        chipDayTue.setOnClickListener {
            toggleDay(DAY_TUE)
        }

        chipDayWed.setOnClickListener {
            toggleDay(DAY_WED)
        }

        chipDayThu.setOnClickListener {
            toggleDay(DAY_THU)
        }

        chipDayFri.setOnClickListener {
            toggleDay(DAY_FRI)
        }

        chipDaySat.setOnClickListener {
            toggleDay(DAY_SAT)
        }

        chipDaySun.setOnClickListener {
            toggleDay(DAY_SUN)
        }

        btnRamp30.setOnClickListener {
            setRamp(
                minutes = 30
            )
        }

        btnRamp60.setOnClickListener {
            setRamp(
                minutes = 60
            )
        }

        btnRamp90.setOnClickListener {
            setRamp(
                minutes = 90
            )
        }

        chipBalanceNatural.setOnClickListener {
            setBalance(
                name = "Natural",
                summary = "R80  G84  B79  W65"
            )
        }

        chipBalancePlant.setOnClickListener {
            setBalance(
                name = "Plant",
                summary = "R85  G92  B76  W70"
            )
        }

        chipBalanceWarm.setOnClickListener {
            setBalance(
                name = "Warm",
                summary = "R90  G76  B55  W70"
            )
        }

        chipBalanceBlue.setOnClickListener {
            setBalance(
                name = "Blue",
                summary = "R55  G68  B95  W50"
            )
        }

        btnCreateQuickProgram.setOnClickListener {
            openGeneratedProgram()
        }
    }

    private fun showTimePickerSheet(
        title: String,
        initialTime: String,
        onTimeSelected: (String) -> Unit
    ) {
        val dialog =
            BottomSheetDialog(
                requireContext()
            )

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
            timeToMinutes(
                time = initialTime
            )

        fun updateTimeLabel() {
            tvSelectedTime.text =
                minutesToTime(
                    minutes = selectedMinutes
                )
        }

        tvTitle.text = title
        updateTimeLabel()

        btnMinusHour.setOnClickListener {
            selectedMinutes =
                wrapMinutes(
                    minutes = selectedMinutes - 60
                )

            updateTimeLabel()
        }

        btnMinusStep.setOnClickListener {
            selectedMinutes =
                wrapMinutes(
                    minutes = selectedMinutes - TIME_STEP_MINUTES
                )

            updateTimeLabel()
        }

        btnPlusStep.setOnClickListener {
            selectedMinutes =
                wrapMinutes(
                    minutes = selectedMinutes + TIME_STEP_MINUTES
                )

            updateTimeLabel()
        }

        btnPlusHour.setOnClickListener {
            selectedMinutes =
                wrapMinutes(
                    minutes = selectedMinutes + 60
                )

            updateTimeLabel()
        }

        btnSave.setOnClickListener {
            dialog.dismiss()

            onTimeSelected(
                minutesToTime(
                    minutes = selectedMinutes
                )
            )
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(
            sheetView
        )

        dialog.show()
    }

    private fun toggleDay(
        day: Int
    ) {
        if (selectedDays.contains(day)) {
            if (selectedDays.size == 1) {
                showMessage(
                    message = "At least one day must be selected"
                )
                return
            }

            selectedDays.remove(day)
        } else {
            selectedDays.add(day)
        }

        renderDayChips()
        updatePreviewText()
    }

    private fun renderDayChips() = with(binding) {
        renderDayChip(
            chip = chipDayMon,
            day = DAY_MON
        )

        renderDayChip(
            chip = chipDayTue,
            day = DAY_TUE
        )

        renderDayChip(
            chip = chipDayWed,
            day = DAY_WED
        )

        renderDayChip(
            chip = chipDayThu,
            day = DAY_THU
        )

        renderDayChip(
            chip = chipDayFri,
            day = DAY_FRI
        )

        renderDayChip(
            chip = chipDaySat,
            day = DAY_SAT
        )

        renderDayChip(
            chip = chipDaySun,
            day = DAY_SUN
        )
    }

    private fun renderDayChip(
        chip: TextView,
        day: Int
    ) {
        val isSelected =
            selectedDays.contains(day)

        if (isSelected) {
            chip.setBackgroundColor(
                requireContext().getColor(
                    R.color.light_accent
                )
            )

            chip.setTextColor(
                requireContext().getColor(
                    R.color.background_color
                )
            )
        } else {
            chip.setBackgroundResource(
                android.R.color.transparent
            )

            chip.setTextColor(
                requireContext().getColor(
                    R.color.settings_text_secondary
                )
            )
        }
    }

    private fun setRamp(
        minutes: Int
    ) = with(binding) {
        rampMinutes = minutes
        sliderRampDuration.value = minutes.toFloat()

        updateRampText()
        updatePreviewText()
    }

    private fun setBalance(
        name: String,
        summary: String
    ) = with(binding) {
        balanceName = name
        channelSummary = summary
        tvQuickChannelSummary.text = summary

        showMessage(
            message = "$name balance selected"
        )

        updatePreviewText()
    }

    private fun updateRampText() = with(binding) {
        tvRampDurationValue.text =
            "$rampMinutes min"
    }

    private fun updatePeakText() = with(binding) {
        tvPeakIntensityValue.text =
            "$peakIntensity%"
    }

    private fun updatePreviewText() = with(binding) {
        val peakStart =
            addMinutesToTime(
                time = sunriseStartTime,
                minutesToAdd = rampMinutes
            )

        val sunsetRampStart =
            subtractMinutesFromTime(
                time = sunsetEndTime,
                minutesToSubtract = rampMinutes
            )

        tvQuickPreviewSummary.text =
            "$sunriseStartTime sunrise → $peakStart peak → " +
                "$sunsetRampStart sunset ramp → $sunsetEndTime off · ${selectedDaysLabel()}"

        tvQuickChannelSummary.text =
            channelSummary
    }

    private fun openGeneratedProgram() {
        lightController?.openProgramEditor(
            programName = "Quick Setup Program"
        )
    }

    private fun selectedDaysLabel(): String {
        return when {
            selectedDays.size == 7 -> {
                "Every day"
            }

            selectedDays == setOf(
                DAY_MON,
                DAY_TUE,
                DAY_WED,
                DAY_THU,
                DAY_FRI
            ) -> {
                "Weekdays"
            }

            selectedDays == setOf(
                DAY_SAT,
                DAY_SUN
            ) -> {
                "Weekend"
            }

            else -> {
                "${selectedDays.size} days"
            }
        }
    }

    private fun addMinutesToTime(
        time: String,
        minutesToAdd: Int
    ): String {
        val total =
            timeToMinutes(
                time = time
            ) + minutesToAdd

        return minutesToTime(
            minutes = wrapMinutes(
                minutes = total
            )
        )
    }

    private fun subtractMinutesFromTime(
        time: String,
        minutesToSubtract: Int
    ): String {
        val total =
            timeToMinutes(
                time = time
            ) - minutesToSubtract

        return minutesToTime(
            minutes = wrapMinutes(
                minutes = total
            )
        )
    }

    private fun timeToMinutes(
        time: String
    ): Int {
        val parts =
            time.split(":")

        if (parts.size != 2) {
            return 0
        }

        val hour =
            parts[0].toIntOrNull() ?: 0

        val minute =
            parts[1].toIntOrNull() ?: 0

        return (hour * 60 + minute)
            .coerceIn(
                minimumValue = 0,
                maximumValue = MINUTES_IN_DAY - 1
            )
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes =
            wrapMinutes(
                minutes = minutes
            )

        val hour =
            safeMinutes / 60

        val minute =
            safeMinutes % 60

        return "%02d:%02d".format(
            hour,
            minute
        )
    }

    private fun wrapMinutes(
        minutes: Int
    ): Int {
        return (
            (minutes % MINUTES_IN_DAY) + MINUTES_IN_DAY
            ) % MINUTES_IN_DAY
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

        private const val TIME_STEP_MINUTES = 15
        private const val MINUTES_IN_DAY = 24 * 60

        private const val DAY_MON = 1
        private const val DAY_TUE = 2
        private const val DAY_WED = 3
        private const val DAY_THU = 4
        private const val DAY_FRI = 5
        private const val DAY_SAT = 6
        private const val DAY_SUN = 7

        fun newInstance(
            deviceId: Long
        ): DeviceLightQuickSetupFragment {
            return DeviceLightQuickSetupFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )
                }
            }
        }
    }
}
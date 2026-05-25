package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.BottomSheetTimerOutletSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TimerOutletSettingsBottomSheet(
    private val fragment: Fragment,
    private val initialState: TimerOutletEditorState,
    private val onSave: (
        state: TimerOutletEditorState,
        sheet: TimerOutletSettingsBottomSheet
    ) -> Unit
) {

    private lateinit var dialog: BottomSheetDialog
    private lateinit var binding: BottomSheetTimerOutletSettingsBinding

    private var selectedRegime: TimerDeviceRepository.OutletRegime =
        initialState.regime

    private var selectedStartTime: String =
        normalizeStartTime(
            value = initialState.startTime
        )

    private var selectedRunDurationMinutes: Int =
        initialState.runDurationMinutes.coerceAtLeast(
            MIN_RUN_DURATION_MINUTES
        )

    private var selectedOffDurationMinutes: Int =
        initialState.offDurationMinutes.coerceAtLeast(
            MIN_OFF_DURATION_MINUTES
        )

    private var selectedRepeatCount: Int =
        initialState.repeatCount.coerceIn(
            MIN_REPEAT_COUNT,
            MAX_REPEAT_COUNT
        )

    private var selectedDays: MutableList<Boolean> =
        initialState.weekDays
            .take(7)
            .toMutableList()
            .also { days ->
                while (days.size < 7) {
                    days.add(
                        true
                    )
                }

                if (days.none { enabled ->
                        enabled
                    }
                ) {
                    days.clear()

                    repeat(7) {
                        days.add(
                            true
                        )
                    }
                }
            }

    private var isSaving: Boolean = false

    fun show() {
        binding = BottomSheetTimerOutletSettingsBinding.inflate(
            fragment.layoutInflater
        )

        dialog = BottomSheetDialog(
            fragment.requireContext()
        )

        dialog.setContentView(
            binding.root
        )

        bindInitialState()
        bindActions()

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.background = ColorDrawable(
                Color.TRANSPARENT
            )

            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(
                    sheet
                )

                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
            }
        }

        dialog.show()
    }

    private fun bindInitialState() {
        binding.tvSheetTitle.text =
            "${initialState.outletName.ifBlank { "Outlet" }} Settings"

        binding.etOutletName.setText(
            initialState.outletName
        )

        binding.etStartTime.text =
            selectedStartTime

        binding.etRunDuration.text =
            formatDurationForUi(
                minutes = selectedRunDurationMinutes
            )

        binding.etOffDuration.text =
            formatDurationForUi(
                minutes = selectedOffDurationMinutes
            )

        binding.switchTimerEnabled.isChecked =
            initialState.timerEnabled

        renderRepeatCount()
        renderMode()
        renderDays()

        binding.tvSheetError.visibility = View.GONE
    }

    private fun bindActions() {
        binding.btnCancel.setOnClickListener {
            if (!isSaving) {
                dialog.dismiss()
            }
        }

        binding.btnSave.setOnClickListener {
            save()
        }

        binding.btnModeAuto.setOnClickListener {
            if (!isSaving) {
                selectedRegime = TimerDeviceRepository.OutletRegime.AUTO
                renderMode()
            }
        }

        binding.btnModeOn.setOnClickListener {
            if (!isSaving) {
                selectedRegime = TimerDeviceRepository.OutletRegime.ON
                renderMode()
            }
        }

        binding.btnModeOff.setOnClickListener {
            if (!isSaving) {
                selectedRegime = TimerDeviceRepository.OutletRegime.OFF
                renderMode()
            }
        }

        binding.inputStartTimeLayout.setOnClickListener {
            showStartTimePicker()
        }

        binding.etStartTime.setOnClickListener {
            showStartTimePicker()
        }

        binding.ivStartTimeEdit.setOnClickListener {
            showStartTimePicker()
        }

        binding.inputRunDurationLayout.setOnClickListener {
            showRunDurationPicker()
        }

        binding.etRunDuration.setOnClickListener {
            showRunDurationPicker()
        }

        binding.ivRunDurationEdit.setOnClickListener {
            showRunDurationPicker()
        }

        binding.inputOffDurationLayout.setOnClickListener {
            showOffDurationPicker()
        }

        binding.etOffDuration.setOnClickListener {
            showOffDurationPicker()
        }

        binding.ivOffDurationEdit.setOnClickListener {
            showOffDurationPicker()
        }

        binding.btnRepeatMinus.setOnClickListener {
            if (
                !isSaving &&
                selectedRepeatCount > MIN_REPEAT_COUNT
            ) {
                selectedRepeatCount--
                renderRepeatCount()
            }
        }

        binding.btnRepeatPlus.setOnClickListener {
            if (
                !isSaving &&
                selectedRepeatCount < MAX_REPEAT_COUNT
            ) {
                selectedRepeatCount++
                renderRepeatCount()
            }
        }

        dayButtons().forEachIndexed { index, button ->
            button.setOnClickListener {
                if (!isSaving) {
                    selectedDays[index] = !selectedDays[index]
                    renderDays()
                }
            }
        }
    }

    private fun showRunDurationPicker() {
        showDurationPicker(
            title = "Run duration",
            currentMinutes = selectedRunDurationMinutes,
            minMinutes = MIN_RUN_DURATION_MINUTES
        ) { minutes ->
            selectedRunDurationMinutes = minutes

            binding.etRunDuration.text =
                formatDurationForUi(
                    minutes = minutes
                )
        }
    }

    private fun showOffDurationPicker() {
        showDurationPicker(
            title = "Off interval",
            currentMinutes = selectedOffDurationMinutes,
            minMinutes = MIN_OFF_DURATION_MINUTES
        ) { minutes ->
            selectedOffDurationMinutes = minutes

            binding.etOffDuration.text =
                formatDurationForUi(
                    minutes = minutes
                )
        }
    }

    private fun save() {
        if (isSaving) {
            return
        }

        binding.tvSheetError.visibility = View.GONE

        val outletName = binding.etOutletName.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (outletName.isBlank()) {
            showError(
                message = "Outlet name cannot be empty."
            )
            return
        }

        if (!isValidStartTime(selectedStartTime)) {
            showError(
                message = "Start time must be like 20:30."
            )
            return
        }

        if (selectedRunDurationMinutes < MIN_RUN_DURATION_MINUTES) {
            showError(
                message = "Run duration must be greater than 0."
            )
            return
        }

        if (selectedOffDurationMinutes < MIN_OFF_DURATION_MINUTES) {
            showError(
                message = "Off interval cannot be negative."
            )
            return
        }

        if (selectedRepeatCount !in MIN_REPEAT_COUNT..MAX_REPEAT_COUNT) {
            showError(
                message = "Repeat count must be between $MIN_REPEAT_COUNT and $MAX_REPEAT_COUNT."
            )
            return
        }

        if (selectedDays.none { enabled ->
                enabled
            }
        ) {
            showError(
                message = "Select at least one active day."
            )
            return
        }

        setSaving(
            saving = true
        )

        onSave(
            initialState.copy(
                outletName = outletName,
                regime = selectedRegime,
                timerEnabled = binding.switchTimerEnabled.isChecked,
                startTime = selectedStartTime,
                runDurationMinutes = selectedRunDurationMinutes,
                offDurationMinutes = selectedOffDurationMinutes,
                repeatCount = selectedRepeatCount,
                weekDays = selectedDays.toList()
            ),
            this
        )
    }

    fun showSaveError(
        message: String
    ) {
        setSaving(
            saving = false
        )

        showError(
            message = message
        )
    }

    fun closeAfterSave() {
        dialog.dismiss()
    }

    fun setSaving(
        saving: Boolean
    ) {
        isSaving = saving

        dialog.setCancelable(
            !saving
        )

        dialog.setCanceledOnTouchOutside(
            !saving
        )

        binding.btnSave.text = if (saving) {
            "Saving..."
        } else {
            "Save"
        }

        binding.btnSave.isEnabled = !saving
        binding.btnCancel.isEnabled = !saving

        binding.etOutletName.isEnabled = !saving
        binding.inputOutletNameLayout.isEnabled = !saving

        binding.inputStartTimeLayout.isEnabled = !saving
        binding.etStartTime.isEnabled = !saving
        binding.ivStartTimeEdit.isEnabled = !saving

        binding.inputRunDurationLayout.isEnabled = !saving
        binding.etRunDuration.isEnabled = !saving
        binding.ivRunDurationEdit.isEnabled = !saving

        binding.inputOffDurationLayout.isEnabled = !saving
        binding.etOffDuration.isEnabled = !saving
        binding.ivOffDurationEdit.isEnabled = !saving

        binding.switchTimerEnabled.isEnabled = !saving

        binding.btnModeAuto.isEnabled = !saving
        binding.btnModeOn.isEnabled = !saving
        binding.btnModeOff.isEnabled = !saving

        dayButtons().forEach { button ->
            button.isEnabled = !saving
        }

        renderRepeatCount()
        renderSavingAlpha()
    }

    private fun renderSavingAlpha() {
        val alpha = if (isSaving) {
            0.55f
        } else {
            1f
        }

        binding.inputOutletNameLayout.alpha = alpha
        binding.modeContainer.alpha = alpha
        binding.timerEnabledRow.alpha = alpha
        binding.inputStartTimeLayout.alpha = alpha
        binding.repeatCounterCard.alpha = alpha
        binding.inputRunDurationLayout.alpha = alpha
        binding.inputOffDurationLayout.alpha = alpha
        binding.daysContainer.alpha = alpha
    }

    private fun showStartTimePicker() {
        if (isSaving) {
            return
        }

        val parts = selectedStartTime
            .split(":")
            .mapNotNull { part ->
                part.toIntOrNull()
            }

        val currentHour = parts.getOrNull(
            index = 0
        ) ?: 0

        val currentMinute = parts.getOrNull(
            index = 1
        ) ?: 0

        TimePickerDialog(
            fragment.requireContext(),
            { _, hourOfDay, minute ->
                selectedStartTime = "%02d:%02d".format(
                    hourOfDay,
                    minute
                )

                binding.etStartTime.text =
                    selectedStartTime
            },
            currentHour,
            currentMinute,
            true
        ).show()
    }

    private fun showDurationPicker(
        title: String,
        currentMinutes: Int,
        minMinutes: Int,
        onSelected: (minutes: Int) -> Unit
    ) {
        if (isSaving) {
            return
        }

        val context = fragment.requireContext()

        val currentHours = currentMinutes / MINUTES_PER_HOUR
        val currentMins = currentMinutes % MINUTES_PER_HOUR

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                18.dp(context),
                4.dp(context),
                18.dp(context),
                0
            )
        }

        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        labelRow.addView(
            createPickerLabel(
                context = context,
                text = "Hours"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        labelRow.addView(
            createPickerLabel(
                context = context,
                text = "Minutes"
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val pickerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val hourPicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 23
            value = currentHours.coerceIn(
                0,
                23
            )
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }

        val minutePicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 59
            value = currentMins.coerceIn(
                0,
                59
            )
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            setFormatter { value ->
                "%02d".format(
                    value
                )
            }
        }

        pickerRow.addView(
            hourPicker,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        pickerRow.addView(
            minutePicker,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(
            labelRow
        )

        root.addView(
            pickerRow
        )

        val pickerDialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create()

        pickerDialog.setOnShowListener {
            pickerDialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val selectedMinutes =
                    hourPicker.value * MINUTES_PER_HOUR +
                        minutePicker.value

                if (selectedMinutes < minMinutes) {
                    showError(
                        message = if (minMinutes == MIN_RUN_DURATION_MINUTES) {
                            "Duration must be greater than 0."
                        } else {
                            "Duration cannot be negative."
                        }
                    )

                    return@setOnClickListener
                }

                onSelected(
                    selectedMinutes
                )

                pickerDialog.dismiss()
            }
        }

        pickerDialog.show()
    }

    private fun createPickerLabel(
        context: Context,
        text: String
    ): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(
                Color.parseColor("#9FAABB")
            )
            textSize = 13f
        }
    }

    private fun renderRepeatCount() {
        binding.tvRepeatCount.text =
            selectedRepeatCount.toString()

        val canDecrease =
            selectedRepeatCount > MIN_REPEAT_COUNT

        val canIncrease =
            selectedRepeatCount < MAX_REPEAT_COUNT

        binding.btnRepeatMinus.alpha =
            if (
                canDecrease &&
                !isSaving
            ) {
                1f
            } else {
                0.38f
            }

        binding.btnRepeatPlus.alpha =
            if (
                canIncrease &&
                !isSaving
            ) {
                1f
            } else {
                0.38f
            }

        binding.btnRepeatMinus.isEnabled =
            canDecrease && !isSaving

        binding.btnRepeatPlus.isEnabled =
            canIncrease && !isSaving
    }

    private fun renderMode() {
        renderModeButton(
            button = binding.btnModeAuto,
            selected = selectedRegime == TimerDeviceRepository.OutletRegime.AUTO,
            selectedBackground = "#6E63E8",
            selectedStroke = "#8B7CFF",
            selectedText = "#FFFFFF",
            idleBackground = "#171D39",
            idleStroke = "#3D3477",
            idleText = "#A7A2D8"
        )

        renderModeButton(
            button = binding.btnModeOn,
            selected = selectedRegime == TimerDeviceRepository.OutletRegime.ON,
            selectedBackground = "#2EAE74",
            selectedStroke = "#52D99A",
            selectedText = "#FFFFFF",
            idleBackground = "#122A24",
            idleStroke = "#245D47",
            idleText = "#93DAB8"
        )

        renderModeButton(
            button = binding.btnModeOff,
            selected = selectedRegime == TimerDeviceRepository.OutletRegime.OFF,
            selectedBackground = "#3C465F",
            selectedStroke = "#AFC1D6",
            selectedText = "#FFFFFF",
            idleBackground = "#1D263F",
            idleStroke = "#2D385C",
            idleText = "#9FAABB"
        )
    }

    private fun renderModeButton(
        button: MaterialButton,
        selected: Boolean,
        selectedBackground: String,
        selectedStroke: String,
        selectedText: String,
        idleBackground: String,
        idleStroke: String,
        idleText: String
    ) {
        button.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(
                if (selected) {
                    selectedBackground
                } else {
                    idleBackground
                }
            )
        )

        button.strokeColor = ColorStateList.valueOf(
            Color.parseColor(
                if (selected) {
                    selectedStroke
                } else {
                    idleStroke
                }
            )
        )

        button.strokeWidth =
            if (selected) {
                2.dp(
                    context = button.context
                )
            } else {
                1.dp(
                    context = button.context
                )
            }

        button.setTextColor(
            Color.parseColor(
                if (selected) {
                    selectedText
                } else {
                    idleText
                }
            )
        )

        button.alpha = if (selected) {
            1f
        } else {
            0.72f
        }
    }

    private fun renderDays() {
        val labels = listOf(
            "S",
            "M",
            "T",
            "W",
            "T",
            "F",
            "S"
        )

        dayButtons().forEachIndexed { index, button ->
            val selected = selectedDays.getOrNull(
                index = index
            ) == true

            button.text = labels[index]

            button.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor(
                    if (selected) {
                        "#6E63E8"
                    } else {
                        "#131C34"
                    }
                )
            )

            button.setTextColor(
                Color.parseColor(
                    if (selected) {
                        "#FFFFFF"
                    } else {
                        "#9FAABB"
                    }
                )
            )

            button.strokeColor = ColorStateList.valueOf(
                Color.parseColor(
                    if (selected) {
                        "#8B7CFF"
                    } else {
                        "#2D385C"
                    }
                )
            )

            button.strokeWidth =
                if (selected) {
                    2.dp(
                        context = button.context
                    )
                } else {
                    1.dp(
                        context = button.context
                    )
                }

            button.alpha =
                if (selected) {
                    1f
                } else {
                    0.68f
                }
        }
    }

    private fun dayButtons(): List<MaterialButton> {
        return listOf(
            binding.btnDaySun,
            binding.btnDayMon,
            binding.btnDayTue,
            binding.btnDayWed,
            binding.btnDayThu,
            binding.btnDayFri,
            binding.btnDaySat
        )
    }

    private fun showError(
        message: String
    ) {
        binding.tvSheetError.text = message
        binding.tvSheetError.visibility = View.VISIBLE

        binding.sheetScroll.post {
            binding.sheetScroll.smoothScrollTo(
                0,
                binding.tvSheetError.bottom
            )
        }
    }

    private fun normalizeStartTime(
        value: String
    ): String {
        return if (isValidStartTime(value)) {
            value
        } else {
            "00:00"
        }
    }

    private fun isValidStartTime(
        value: String
    ): Boolean {
        val parts = value.split(
            ":"
        )

        if (parts.size != 2) {
            return false
        }

        val hour = parts[0].toIntOrNull()
        val minute = parts[1].toIntOrNull()

        return hour != null &&
            minute != null &&
            hour in 0..23 &&
            minute in 0..59
    }

    private fun formatDurationForUi(
        minutes: Int
    ): String {
        val safeMinutes = minutes.coerceAtLeast(
            0
        )

        val hours = safeMinutes / MINUTES_PER_HOUR
        val mins = safeMinutes % MINUTES_PER_HOUR

        return "%02d:%02d".format(
            hours,
            mins
        )
    }

    private fun Int.dp(
        context: Context
    ): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val MIN_REPEAT_COUNT = 1
        private const val MAX_REPEAT_COUNT = 99

        private const val MIN_RUN_DURATION_MINUTES = 1
        private const val MIN_OFF_DURATION_MINUTES = 0

        private const val MINUTES_PER_HOUR = 60
    }
}
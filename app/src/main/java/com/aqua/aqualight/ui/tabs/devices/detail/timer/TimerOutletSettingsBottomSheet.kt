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
            1
        )

    private var selectedOffDurationMinutes: Int =
        initialState.offDurationMinutes.coerceAtLeast(
            0
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

        binding.etStartTime.setText(
            selectedStartTime
        )

        binding.etRunDuration.setText(
            formatDurationForUi(
                minutes = selectedRunDurationMinutes
            )
        )

        binding.etOffDuration.setText(
            formatDurationForUi(
                minutes = selectedOffDurationMinutes
            )
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
            selectedRegime = TimerDeviceRepository.OutletRegime.AUTO
            renderMode()
        }

        binding.btnModeOn.setOnClickListener {
            selectedRegime = TimerDeviceRepository.OutletRegime.ON
            renderMode()
        }

        binding.btnModeOff.setOnClickListener {
            selectedRegime = TimerDeviceRepository.OutletRegime.OFF
            renderMode()
        }

        binding.etStartTime.setOnClickListener {
            showStartTimePicker()
        }

        binding.inputStartTimeLayout.setEndIconOnClickListener {
            showStartTimePicker()
        }

        binding.inputStartTimeLayout.setOnClickListener {
            showStartTimePicker()
        }

        binding.etRunDuration.setOnClickListener {
            showDurationPicker(
                title = "Run duration",
                currentMinutes = selectedRunDurationMinutes,
                minMinutes = 1
            ) { minutes ->
                selectedRunDurationMinutes = minutes
                binding.etRunDuration.setText(
                    formatDurationForUi(
                        minutes = minutes
                    )
                )
            }
        }

        binding.inputRunDurationLayout.setEndIconOnClickListener {
            showDurationPicker(
                title = "Run duration",
                currentMinutes = selectedRunDurationMinutes,
                minMinutes = 1
            ) { minutes ->
                selectedRunDurationMinutes = minutes
                binding.etRunDuration.setText(
                    formatDurationForUi(
                        minutes = minutes
                    )
                )
            }
        }

        binding.etOffDuration.setOnClickListener {
            showDurationPicker(
                title = "Off interval",
                currentMinutes = selectedOffDurationMinutes,
                minMinutes = 0
            ) { minutes ->
                selectedOffDurationMinutes = minutes
                binding.etOffDuration.setText(
                    formatDurationForUi(
                        minutes = minutes
                    )
                )
            }
        }

        binding.inputOffDurationLayout.setEndIconOnClickListener {
            showDurationPicker(
                title = "Off interval",
                currentMinutes = selectedOffDurationMinutes,
                minMinutes = 0
            ) { minutes ->
                selectedOffDurationMinutes = minutes
                binding.etOffDuration.setText(
                    formatDurationForUi(
                        minutes = minutes
                    )
                )
            }
        }

        binding.btnRepeatMinus.setOnClickListener {
            if (selectedRepeatCount > MIN_REPEAT_COUNT) {
                selectedRepeatCount--
                renderRepeatCount()
            }
        }

        binding.btnRepeatPlus.setOnClickListener {
            if (selectedRepeatCount < MAX_REPEAT_COUNT) {
                selectedRepeatCount++
                renderRepeatCount()
            }
        }

        dayButtons().forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedDays[index] = !selectedDays[index]
                renderDays()
            }
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

        if (selectedRunDurationMinutes <= 0) {
            showError(
                message = "Run duration must be greater than 0."
            )
            return
        }

        if (selectedRepeatCount !in MIN_REPEAT_COUNT..MAX_REPEAT_COUNT) {
            showError(
                message = "Repeat count must be between $MIN_REPEAT_COUNT and $MAX_REPEAT_COUNT."
            )
            return
        }

        if (selectedDays.none { enabled -> enabled }) {
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
        binding.etStartTime.isEnabled = !saving
        binding.etRunDuration.isEnabled = !saving
        binding.etOffDuration.isEnabled = !saving
        binding.switchTimerEnabled.isEnabled = !saving

        binding.btnRepeatMinus.isEnabled = !saving
        binding.btnRepeatPlus.isEnabled = !saving

        dayButtons().forEach { button ->
            button.isEnabled = !saving
        }

        binding.btnModeAuto.isEnabled = !saving
        binding.btnModeOn.isEnabled = !saving
        binding.btnModeOff.isEnabled = !saving
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

                binding.etStartTime.setText(
                    selectedStartTime
                )
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

        val currentHours = currentMinutes / 60
        val currentMins = currentMinutes % 60

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

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                val selectedMinutes =
                    hourPicker.value * 60 +
                        minutePicker.value

                if (selectedMinutes < minMinutes) {
                    showError(
                        message = if (minMinutes == 1) {
                            "Duration must be greater than 0."
                        } else {
                            "Duration cannot be negative."
                        }
                    )

                    return@setPositiveButton
                }

                onSelected(
                    selectedMinutes
                )
            }
            .show()
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

        binding.btnRepeatMinus.alpha =
            if (selectedRepeatCount <= MIN_REPEAT_COUNT) {
                0.45f
            } else {
                1f
            }

        binding.btnRepeatPlus.alpha =
            if (selectedRepeatCount >= MAX_REPEAT_COUNT) {
                0.45f
            } else {
                1f
            }
    }

    private fun renderMode() {
        renderModeButton(
            button = binding.btnModeAuto,
            selected = selectedRegime == TimerDeviceRepository.OutletRegime.AUTO
        )

        renderModeButton(
            button = binding.btnModeOn,
            selected = selectedRegime == TimerDeviceRepository.OutletRegime.ON
        )

        renderModeButton(
            button = binding.btnModeOff,
            selected = selectedRegime == TimerDeviceRepository.OutletRegime.OFF
        )
    }

    private fun renderModeButton(
        button: MaterialButton,
        selected: Boolean
    ) {
        button.alpha = if (selected) {
            1f
        } else {
            0.45f
        }
    }

    private fun renderDays() {
        dayButtons().forEachIndexed { index, button ->
            val selected = selectedDays.getOrNull(index) == true

            button.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor(
                    if (selected) {
                        "#29264A"
                    } else {
                        "#131C34"
                    }
                )
            )

            button.setTextColor(
                Color.parseColor(
                    if (selected) {
                        "#CFC8FF"
                    } else {
                        "#9FAABB"
                    }
                )
            )

            button.strokeColor = ColorStateList.valueOf(
                Color.parseColor(
                    if (selected) {
                        "#5F55C8"
                    } else {
                        "#2D385C"
                    }
                )
            )

            button.strokeWidth = 1.dp(
                context = button.context
            )
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

        val hours = safeMinutes / 60
        val mins = safeMinutes % 60

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
    }
}
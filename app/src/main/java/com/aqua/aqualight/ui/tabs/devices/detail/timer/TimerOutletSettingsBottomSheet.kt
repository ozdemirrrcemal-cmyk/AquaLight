package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.BottomSheetTimerOutletSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

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
            initialState.startTime
        )

        binding.etRunDuration.setText(
            initialState.runDurationMinutes.toString()
        )

        binding.etOffDuration.setText(
            initialState.offDurationMinutes.toString()
        )

        binding.etRepeatCount.setText(
            initialState.repeatCount.toString()
        )

        binding.switchTimerEnabled.isChecked =
            initialState.timerEnabled

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

        val startTime = binding.etStartTime.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (!isValidStartTime(startTime)) {
            showError(
                message = "Start time must be like 20:30."
            )
            return
        }

        val runMinutes = binding.etRunDuration.text
            ?.toString()
            ?.trim()
            ?.toIntOrNull()

        if (runMinutes == null || runMinutes <= 0) {
            showError(
                message = "Run duration must be greater than 0."
            )
            return
        }

        val offMinutes = binding.etOffDuration.text
            ?.toString()
            ?.trim()
            ?.toIntOrNull() ?: 0

        if (offMinutes < 0) {
            showError(
                message = "Off interval cannot be negative."
            )
            return
        }

        val repeatCount = binding.etRepeatCount.text
            ?.toString()
            ?.trim()
            ?.toIntOrNull()

        if (repeatCount == null || repeatCount <= 0) {
            showError(
                message = "Repeat count must be greater than 0."
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
                startTime = startTime,
                runDurationMinutes = runMinutes,
                offDurationMinutes = offMinutes,
                repeatCount = repeatCount,
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
        binding.etRepeatCount.isEnabled = !saving
        binding.switchTimerEnabled.isEnabled = !saving

        dayButtons().forEach { button ->
            button.isEnabled = !saving
        }

        binding.btnModeAuto.isEnabled = !saving
        binding.btnModeOn.isEnabled = !saving
        binding.btnModeOff.isEnabled = !saving
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
}
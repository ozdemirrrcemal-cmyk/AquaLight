package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightDayPickerBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightRepeatDay
import com.google.android.material.bottomsheet.BottomSheetDialog

object LightDayPickerBottomSheet {

    fun show(
        fragment: Fragment,
        initialDays: Set<LightRepeatDay>,
        onSave: (Set<LightRepeatDay>) -> Unit
    ) {
        val context = fragment.requireContext()
        val dialog = BottomSheetDialog(context)

        val binding = BottomSheetLightDayPickerBinding.inflate(
            LayoutInflater.from(context)
        )

        val selectedDays =
            if (initialDays.isEmpty()) {
                LightRepeatDay.everyDay().toMutableSet()
            } else {
                initialDays.toMutableSet()
            }

        fun updateChip(
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
                context.getColor(
                    if (selected) {
                        R.color.background_color
                    } else {
                        R.color.settings_text_secondary
                    }
                )
            )
        }

        fun summaryText(): String {
            return when {
                selectedDays == LightRepeatDay.everyDay() -> {
                    context.getString(R.string.light_day_picker_summary_every_day)
                }

                selectedDays == LightRepeatDay.weekdays() -> {
                    context.getString(R.string.light_day_picker_summary_weekdays)
                }

                selectedDays == LightRepeatDay.weekend() -> {
                    context.getString(R.string.light_day_picker_summary_weekend)
                }

                else -> {
                    context.getString(
                        R.string.light_day_picker_summary_days_selected,
                        selectedDays.size
                    )
                }
            }
        }

        fun render() {
            updateChip(
                binding.chipPickerMon,
                selectedDays.contains(LightRepeatDay.MONDAY)
            )

            updateChip(
                binding.chipPickerTue,
                selectedDays.contains(LightRepeatDay.TUESDAY)
            )

            updateChip(
                binding.chipPickerWed,
                selectedDays.contains(LightRepeatDay.WEDNESDAY)
            )

            updateChip(
                binding.chipPickerThu,
                selectedDays.contains(LightRepeatDay.THURSDAY)
            )

            updateChip(
                binding.chipPickerFri,
                selectedDays.contains(LightRepeatDay.FRIDAY)
            )

            updateChip(
                binding.chipPickerSat,
                selectedDays.contains(LightRepeatDay.SATURDAY)
            )

            updateChip(
                binding.chipPickerSun,
                selectedDays.contains(LightRepeatDay.SUNDAY)
            )

            binding.tvDayPickerSummary.text = summaryText()
        }

        fun toggleDay(
            day: LightRepeatDay
        ) {
            if (selectedDays.contains(day)) {
                if (selectedDays.size == 1) {
                    binding.tvDayPickerSummary.text = context.getString(
                        R.string.light_day_picker_error_one_day_required
                    )
                    return
                }

                selectedDays.remove(day)
            } else {
                selectedDays.add(day)
            }

            render()
        }

        binding.chipPickerMon.setOnClickListener {
            toggleDay(LightRepeatDay.MONDAY)
        }

        binding.chipPickerTue.setOnClickListener {
            toggleDay(LightRepeatDay.TUESDAY)
        }

        binding.chipPickerWed.setOnClickListener {
            toggleDay(LightRepeatDay.WEDNESDAY)
        }

        binding.chipPickerThu.setOnClickListener {
            toggleDay(LightRepeatDay.THURSDAY)
        }

        binding.chipPickerFri.setOnClickListener {
            toggleDay(LightRepeatDay.FRIDAY)
        }

        binding.chipPickerSat.setOnClickListener {
            toggleDay(LightRepeatDay.SATURDAY)
        }

        binding.chipPickerSun.setOnClickListener {
            toggleDay(LightRepeatDay.SUNDAY)
        }

        binding.btnDayPickerWeekdays.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(LightRepeatDay.weekdays())
            render()
        }

        binding.btnDayPickerWeekend.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(LightRepeatDay.weekend())
            render()
        }

        binding.btnDayPickerEveryDay.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(LightRepeatDay.everyDay())
            render()
        }

        binding.btnDayPickerSave.setOnClickListener {
            dialog.dismiss()
            onSave(selectedDays.toSet())
        }

        binding.btnDayPickerCancel.setOnClickListener {
            dialog.dismiss()
        }

        render()

        dialog.setContentView(binding.root)
        dialog.show()
    }
}
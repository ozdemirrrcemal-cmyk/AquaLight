package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightCustomDaysBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightCustomDaysSheet private constructor(
    private val context: Context
) {

    fun show(
        selectedDays: Set<Int>,
        onApply: (selectedDays: Set<Int>) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetLightCustomDaysBinding.inflate(
            LayoutInflater.from(context)
        )

        dialog.setContentView(binding.root)

        val currentSelection = selectedDays.toMutableSet()

        val dayViews = listOf(
            1 to binding.dayMonday,
            2 to binding.dayTuesday,
            3 to binding.dayWednesday,
            4 to binding.dayThursday,
            5 to binding.dayFriday,
            6 to binding.daySaturday,
            7 to binding.daySunday
        )

        fun renderDays() {
            dayViews.forEach { (day, view) ->
                renderDayChip(
                    view = view,
                    selected = currentSelection.contains(day)
                )
            }
        }

        dayViews.forEach { (day, view) ->
            view.setOnClickListener {
                if (currentSelection.contains(day)) {
                    currentSelection.remove(day)
                } else {
                    currentSelection.add(day)
                }

                renderDays()
            }
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnApply.setOnClickListener {
            onApply(currentSelection.toSet())
            dialog.dismiss()
        }

        renderDays()
        dialog.show()
    }

    private fun renderDayChip(
        view: TextView,
        selected: Boolean
    ) {
        val background = if (selected) {
            R.drawable.bg_light_filter_selected
        } else {
            android.R.color.transparent
        }

        val textColor = if (selected) {
            context.getColor(R.color.light_button_on_primary)
        } else {
            context.getColor(R.color.light_text_secondary)
        }

        view.setBackgroundResource(background)
        view.setTextColor(textColor)
    }

    companion object {
        fun create(context: Context): LightCustomDaysSheet {
            return LightCustomDaysSheet(context)
        }
    }
}
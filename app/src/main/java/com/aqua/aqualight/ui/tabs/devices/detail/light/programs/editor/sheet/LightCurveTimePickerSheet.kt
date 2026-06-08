package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import com.aqua.aqualight.databinding.BottomSheetLightCurveTimePickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale

class LightCurveTimePickerSheet private constructor(
    private val context: Context
) {

    fun show(
        title: String,
        initialHour: Int,
        initialMinute: Int,
        onApply: (hour: Int, minute: Int) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetLightCurveTimePickerBinding.inflate(
            LayoutInflater.from(context)
        )

        dialog.setContentView(binding.root)

        binding.tvSheetTitle.text = title

        setupHourPicker(binding.hourPicker, initialHour)
        setupMinutePicker(binding.minutePicker, initialMinute)

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnApply.setOnClickListener {
            onApply(
                binding.hourPicker.value,
                binding.minutePicker.value
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupHourPicker(
        picker: NumberPicker,
        initialHour: Int
    ) {
        picker.minValue = 0
        picker.maxValue = 23
        picker.value = initialHour.coerceIn(0, 23)
        picker.wrapSelectorWheel = true

        picker.displayedValues = (0..23)
            .map { String.format(Locale.US, "%02d", it) }
            .toTypedArray()
    }

    private fun setupMinutePicker(
        picker: NumberPicker,
        initialMinute: Int
    ) {
        picker.minValue = 0
        picker.maxValue = 59
        picker.value = initialMinute.coerceIn(0, 59)
        picker.wrapSelectorWheel = true

        picker.displayedValues = (0..59)
            .map { String.format(Locale.US, "%02d", it) }
            .toTypedArray()
    }

    companion object {
        fun create(context: Context): LightCurveTimePickerSheet {
            return LightCurveTimePickerSheet(context)
        }
    }
}
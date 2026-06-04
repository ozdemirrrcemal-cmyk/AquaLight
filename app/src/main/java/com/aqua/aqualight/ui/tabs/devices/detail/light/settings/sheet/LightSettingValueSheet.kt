package com.aqua.aqualight.ui.tabs.devices.detail.light.settings.sheet

import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import android.widget.TextView
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightSettingValueSheet private constructor(
    private val context: Context
) {

    fun show(
        title: String,
        subtitle: String,
        values: List<Int>,
        suffix: String,
        initialValue: Int,
        onSelected: (Int) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)

        val view = LayoutInflater.from(context).inflate(
            R.layout.bottom_sheet_light_setting_value,
            null,
            false
        )

        val titleText = view.findViewById<TextView>(R.id.tvSheetTitle)
        val subtitleText = view.findViewById<TextView>(R.id.tvSheetSubtitle)
        val valuePicker = view.findViewById<NumberPicker>(R.id.valuePicker)
        val cancelButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val applyButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApply)

        val labels = values.map { value ->
            "$value$suffix"
        }.toTypedArray()

        val initialIndex = values.indexOf(initialValue).takeIf { it >= 0 } ?: 0

        titleText.text = title
        subtitleText.text = subtitle

        valuePicker.minValue = 0
        valuePicker.maxValue = labels.lastIndex
        valuePicker.displayedValues = labels
        valuePicker.value = initialIndex
        valuePicker.wrapSelectorWheel = false

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        applyButton.setOnClickListener {
            val selectedValue = values[valuePicker.value]
            dialog.dismiss()
            onSelected(selectedValue)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    companion object {
        fun create(
            context: Context
        ): LightSettingValueSheet {
            return LightSettingValueSheet(context)
        }
    }
}
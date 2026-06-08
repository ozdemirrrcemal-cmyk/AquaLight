package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.sheet

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightPresetOptionsSheet private constructor(
    private val context: Context
) {

    fun show(
        presetName: String,
        subtitle: String,
        isCustom: Boolean,
        onApply: () -> Unit,
        onDeleteRequested: () -> Unit
    ) {
        val dialog = BottomSheetDialog(context)

        val view = LayoutInflater.from(context).inflate(
            R.layout.bottom_sheet_light_preset_options,
            null,
            false
        )

        val titleText =
            view.findViewById<TextView>(R.id.tvPresetSheetTitle)

        val subtitleText =
            view.findViewById<TextView>(R.id.tvPresetSheetSubtitle)

        val applyRow =
            view.findViewById<LinearLayout>(R.id.rowApplyPreset)

        val deleteRow =
            view.findViewById<LinearLayout>(R.id.rowDeletePreset)

        titleText.text = presetName
        subtitleText.text = subtitle

        deleteRow.visibility = if (isCustom) {
            View.VISIBLE
        } else {
            View.GONE
        }

        applyRow.setOnClickListener {
            dialog.dismiss()
            onApply()
        }

        deleteRow.setOnClickListener {
            dialog.dismiss()
            onDeleteRequested()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    companion object {
        fun create(
            context: Context
        ): LightPresetOptionsSheet {
            return LightPresetOptionsSheet(context)
        }
    }
}
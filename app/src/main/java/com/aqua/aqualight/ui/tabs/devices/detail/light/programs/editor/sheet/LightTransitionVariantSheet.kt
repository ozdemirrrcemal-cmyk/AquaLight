package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightTransitionVariantBinding
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightTransitionVariantSheet private constructor(
    private val context: Context
) {

    fun show(
        initialMode: LightCurveTransitionMode,
        onApply: (LightCurveTransitionMode) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetLightTransitionVariantBinding.inflate(
            LayoutInflater.from(context)
        )

        dialog.setContentView(binding.root)

        var selectedMode = initialMode

        fun renderSelection() {
            renderModeRow(
                row = binding.modeLinear,
                checkText = binding.checkLinear,
                selected = selectedMode == LightCurveTransitionMode.LINEAR
            )

            renderModeRow(
                row = binding.modeSmooth,
                checkText = binding.checkSmooth,
                selected = selectedMode == LightCurveTransitionMode.SMOOTH
            )

            renderModeRow(
                row = binding.modeNatural,
                checkText = binding.checkNatural,
                selected = selectedMode == LightCurveTransitionMode.NATURAL
            )
        }

        binding.modeLinear.setOnClickListener {
            selectedMode = LightCurveTransitionMode.LINEAR
            renderSelection()
        }

        binding.modeSmooth.setOnClickListener {
            selectedMode = LightCurveTransitionMode.SMOOTH
            renderSelection()
        }

        binding.modeNatural.setOnClickListener {
            selectedMode = LightCurveTransitionMode.NATURAL
            renderSelection()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnApply.setOnClickListener {
            onApply(selectedMode)
            dialog.dismiss()
        }

        renderSelection()
        dialog.show()
    }

    private fun renderModeRow(
        row: android.view.View,
        checkText: TextView,
        selected: Boolean
    ) {
        row.setBackgroundResource(
            if (selected) {
                R.drawable.bg_light_filter_selected
            } else {
                R.drawable.bg_light_program_time_panel
            }
        )

        checkText.text = if (selected) {
            context.getString(R.string.common_selected)
        } else {
            ""
        }

        checkText.setTextColor(
            if (selected) {
                context.getColor(R.color.light_button_on_primary)
            } else {
                context.getColor(R.color.light_text_secondary)
            }
        )
    }

    companion object {
        fun create(context: Context): LightTransitionVariantSheet {
            return LightTransitionVariantSheet(context)
        }
    }
}
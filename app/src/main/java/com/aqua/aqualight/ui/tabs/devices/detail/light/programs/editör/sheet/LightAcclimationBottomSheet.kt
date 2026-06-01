package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightAcclimationSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

object LightAcclimationBottomSheet {

    fun show(
        fragment: Fragment,
        initialState: LightAcclimationSheetState,
        onSave: (LightAcclimationSheetState) -> Unit
    ) {
        val context = fragment.requireContext()
        val dialog = BottomSheetDialog(context)

        val binding = BottomSheetLightAcclimationSettingsBinding.inflate(
            LayoutInflater.from(context)
        )

        var enabled = initialState.enabled
        var selectedDays = initialState.durationDays
        var startIntensity = initialState.startIntensityPercent.coerceIn(
            LightAcclimationSheetState.MIN_START_INTENSITY_PERCENT,
            LightAcclimationSheetState.MAX_START_INTENSITY_PERCENT
        )

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

        fun render() {
            binding.switchAcclimationEnabled.isChecked = enabled

            binding.tvAcclimationDurationValue.text = context.getString(
                R.string.light_acclimation_duration_value,
                selectedDays
            )

            binding.tvAcclimationStartValue.text = context.getString(
                R.string.common_percent_value,
                startIntensity
            )

            binding.tvAcclimationSummary.text =
                if (enabled) {
                    context.getString(
                        R.string.light_acclimation_summary_enabled,
                        startIntensity,
                        selectedDays
                    )
                } else {
                    context.getString(
                        R.string.light_acclimation_summary_disabled
                    )
                }

            updateChip(
                binding.chipAcclimation3Days,
                selectedDays == 3
            )

            updateChip(
                binding.chipAcclimation7Days,
                selectedDays == 7
            )

            updateChip(
                binding.chipAcclimation14Days,
                selectedDays == 14
            )
        }

        binding.sliderAcclimationStart.valueFrom =
            LightAcclimationSheetState.MIN_START_INTENSITY_PERCENT.toFloat()

        binding.sliderAcclimationStart.valueTo =
            LightAcclimationSheetState.MAX_START_INTENSITY_PERCENT.toFloat()

        binding.sliderAcclimationStart.stepSize = 5f
        binding.sliderAcclimationStart.value = startIntensity.toFloat()

        binding.switchAcclimationEnabled.setOnCheckedChangeListener { _, isChecked ->
            enabled = isChecked
            render()
        }

        binding.chipAcclimation3Days.setOnClickListener {
            selectedDays = 3
            render()
        }

        binding.chipAcclimation7Days.setOnClickListener {
            selectedDays = 7
            render()
        }

        binding.chipAcclimation14Days.setOnClickListener {
            selectedDays = 14
            render()
        }

        binding.sliderAcclimationStart.addOnChangeListener { _, value, _ ->
            startIntensity = value.roundToInt().coerceIn(
                LightAcclimationSheetState.MIN_START_INTENSITY_PERCENT,
                LightAcclimationSheetState.MAX_START_INTENSITY_PERCENT
            )

            render()
        }

        binding.btnAcclimationSave.setOnClickListener {
            dialog.dismiss()

            onSave(
                LightAcclimationSheetState(
                    enabled = enabled,
                    durationDays = selectedDays,
                    startIntensityPercent = startIntensity
                )
            )
        }

        binding.btnAcclimationCancel.setOnClickListener {
            dialog.dismiss()
        }

        render()

        dialog.setContentView(binding.root)
        dialog.show()
    }
}
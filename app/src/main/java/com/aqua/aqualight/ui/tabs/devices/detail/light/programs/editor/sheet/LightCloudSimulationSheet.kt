package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightCloudSimulationBinding
import com.aqua.aqualight.data.devices.light.programs.model.CloudFrequency
import com.aqua.aqualight.data.devices.light.programs.model.CloudSimulationSettings
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightCloudSimulationSheet private constructor(
    private val context: Context
) {

    fun show(
        initialSettings: CloudSimulationSettings,
        onApply: (CloudSimulationSettings) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetLightCloudSimulationBinding.inflate(
            LayoutInflater.from(context)
        )

        dialog.setContentView(binding.root)

        var enabled = initialSettings.enabled
        var coverage = initialSettings.coveragePercent.coerceIn(5, 70)
        var frequency = initialSettings.frequency

        fun renderEnabledState() {
            binding.cloudOptionsContainer.alpha = if (enabled) 1f else 0.45f
            setChildrenEnabled(binding.cloudOptionsContainer, enabled)
        }

        fun renderCoverage() {
            binding.tvCloudCoverage.text =
                context.getString(
                    R.string.light_cloud_simulation_coverage_format,
                    coverage
                )

            binding.sliderCloudCoverage.value = coverage.toFloat()
        }

        fun renderFrequency() {
            renderFrequencyChip(
                view = binding.frequencyRare,
                selected = frequency == CloudFrequency.RARE
            )
            renderFrequencyChip(
                view = binding.frequencyNormal,
                selected = frequency == CloudFrequency.NORMAL
            )
            renderFrequencyChip(
                view = binding.frequencyFrequent,
                selected = frequency == CloudFrequency.FREQUENT
            )
        }

        binding.switchCloudEnabled.isChecked = enabled

        renderEnabledState()
        renderCoverage()
        renderFrequency()

        binding.switchCloudEnabled.setOnCheckedChangeListener { _, isChecked ->
            enabled = isChecked
            renderEnabledState()
        }

        binding.sliderCloudCoverage.addOnChangeListener { _, value, _ ->
            coverage = value.toInt()

            binding.tvCloudCoverage.text =
                context.getString(
                    R.string.light_cloud_simulation_coverage_format,
                    coverage
                )
        }

        binding.frequencyRare.setOnClickListener {
            frequency = CloudFrequency.RARE
            renderFrequency()
        }

        binding.frequencyNormal.setOnClickListener {
            frequency = CloudFrequency.NORMAL
            renderFrequency()
        }

        binding.frequencyFrequent.setOnClickListener {
            frequency = CloudFrequency.FREQUENT
            renderFrequency()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnApply.setOnClickListener {
            onApply(
                CloudSimulationSettings(
                    enabled = enabled,
                    coveragePercent = coverage,
                    frequency = frequency
                )
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun renderFrequencyChip(
        view: TextView,
        selected: Boolean
    ) {
        view.setBackgroundResource(
            if (selected) {
                R.drawable.bg_light_filter_selected
            } else {
                android.R.color.transparent
            }
        )

        view.setTextColor(
            if (selected) {
                context.getColor(R.color.light_button_on_primary)
            } else {
                context.getColor(R.color.light_text_secondary)
            }
        )
    }

    private fun setChildrenEnabled(
        view: View,
        enabled: Boolean
    ) {
        view.isEnabled = enabled

        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                setChildrenEnabled(view.getChildAt(index), enabled)
            }
        }
    }

    companion object {
        fun create(context: Context): LightCloudSimulationSheet {
            return LightCloudSimulationSheet(context)
        }
    }
}
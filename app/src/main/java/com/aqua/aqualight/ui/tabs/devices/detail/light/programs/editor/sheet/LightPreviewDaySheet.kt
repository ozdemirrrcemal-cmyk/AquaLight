package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightPreviewDayBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.PreviewSpeed
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightPreviewDaySheet private constructor(
    private val context: Context
) {

    fun show(
        initialSpeed: PreviewSpeed = PreviewSpeed.ONE_MINUTE,
        onStartPreview: (PreviewSpeed) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetLightPreviewDayBinding.inflate(
            LayoutInflater.from(context)
        )

        dialog.setContentView(binding.root)

        var selectedSpeed = initialSpeed

        fun renderSpeed() {
            renderSpeedChip(
                view = binding.speedOneMinute,
                selected = selectedSpeed == PreviewSpeed.ONE_MINUTE
            )

            renderSpeedChip(
                view = binding.speedThreeMinutes,
                selected = selectedSpeed == PreviewSpeed.THREE_MINUTES
            )

            renderSpeedChip(
                view = binding.speedFiveMinutes,
                selected = selectedSpeed == PreviewSpeed.FIVE_MINUTES
            )
        }

        binding.speedOneMinute.setOnClickListener {
            selectedSpeed = PreviewSpeed.ONE_MINUTE
            renderSpeed()
        }

        binding.speedThreeMinutes.setOnClickListener {
            selectedSpeed = PreviewSpeed.THREE_MINUTES
            renderSpeed()
        }

        binding.speedFiveMinutes.setOnClickListener {
            selectedSpeed = PreviewSpeed.FIVE_MINUTES
            renderSpeed()
        }

        binding.btnStartPreview.setOnClickListener {
            binding.previewProgressBar.progress = 0
            binding.tvPreviewProgress.text = "0%"
            onStartPreview(selectedSpeed)
        }

        renderSpeed()
        dialog.show()
    }

    private fun renderSpeedChip(
        view: TextView,
        selected: Boolean
    ) {
        view.setBackgroundResource(
            if (selected) R.drawable.bg_light_filter_selected else android.R.color.transparent
        )

        view.setTextColor(
            if (selected) {
                context.getColor(R.color.light_button_on_primary)
            } else {
                context.getColor(R.color.light_text_secondary)
            }
        )
    }

    companion object {
        fun create(context: Context): LightPreviewDaySheet {
            return LightPreviewDaySheet(context)
        }
    }
}
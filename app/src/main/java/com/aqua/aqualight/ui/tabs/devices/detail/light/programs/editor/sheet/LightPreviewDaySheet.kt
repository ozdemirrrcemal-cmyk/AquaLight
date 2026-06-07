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

    private var dialog: BottomSheetDialog? = null
    private var binding: BottomSheetLightPreviewDayBinding? = null

    private var selectedSpeed: PreviewSpeed = PreviewSpeed.ONE_MINUTE
    private var currentIsPreviewRunning: Boolean = false

    private var onStopPreview: (() -> Unit)? = null

    fun show(
        initialSpeed: PreviewSpeed = PreviewSpeed.ONE_MINUTE,
        initialProgressPercent: Int = 0,
        isPreviewRunning: Boolean = false,
        onStartPreview: (PreviewSpeed) -> Unit,
        onStopPreview: () -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetLightPreviewDayBinding.inflate(
            LayoutInflater.from(context)
        )

        this.dialog = dialog
        this.binding = binding
        this.selectedSpeed = initialSpeed
        this.currentIsPreviewRunning = isPreviewRunning
        this.onStopPreview = onStopPreview

        dialog.setContentView(binding.root)

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
            if (currentIsPreviewRunning) {
                return@setOnClickListener
            }

            selectedSpeed = PreviewSpeed.ONE_MINUTE
            renderSpeed()
        }

        binding.speedThreeMinutes.setOnClickListener {
            if (currentIsPreviewRunning) {
                return@setOnClickListener
            }

            selectedSpeed = PreviewSpeed.THREE_MINUTES
            renderSpeed()
        }

        binding.speedFiveMinutes.setOnClickListener {
            if (currentIsPreviewRunning) {
                return@setOnClickListener
            }

            selectedSpeed = PreviewSpeed.FIVE_MINUTES
            renderSpeed()
        }

        binding.btnStartPreview.setOnClickListener {
            if (currentIsPreviewRunning) {
                this.onStopPreview?.invoke()
                return@setOnClickListener
            }

            renderPreviewState(
                isPreviewRunning = true,
                progressPercent = 0
            )

            onStartPreview(selectedSpeed)
        }

        dialog.setOnDismissListener {
            this.binding = null
            this.dialog = null
            this.onStopPreview = null
            onDismiss()
        }

        renderSpeed()

        renderPreviewState(
            isPreviewRunning = isPreviewRunning,
            progressPercent = initialProgressPercent
        )

        dialog.show()
    }

    fun renderPreviewState(
        isPreviewRunning: Boolean,
        progressPercent: Int
    ) {
        val binding = binding ?: return

        currentIsPreviewRunning = isPreviewRunning

        val safeProgress = progressPercent.coerceIn(0, 100)

        binding.previewProgressBar.progress = safeProgress
        binding.tvPreviewProgress.text = "$safeProgress%"

        binding.btnStartPreview.text = when {
            isPreviewRunning -> {
                "Stop Preview"
            }

            safeProgress >= 100 -> {
                "Start Again"
            }

            else -> {
                "Start Preview"
            }
        }

        binding.previewSpeedSelector.alpha = if (isPreviewRunning) {
            0.55f
        } else {
            1f
        }

        binding.speedOneMinute.isEnabled = !isPreviewRunning
        binding.speedThreeMinutes.isEnabled = !isPreviewRunning
        binding.speedFiveMinutes.isEnabled = !isPreviewRunning
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    private fun renderSpeedChip(
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

    companion object {
        fun create(
            context: Context
        ): LightPreviewDaySheet {
            return LightPreviewDaySheet(context)
        }
    }
}
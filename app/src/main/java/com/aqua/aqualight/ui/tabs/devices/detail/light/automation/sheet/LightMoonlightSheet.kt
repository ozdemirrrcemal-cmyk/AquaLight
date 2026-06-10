package com.aqua.aqualight.ui.tabs.devices.detail.light.automation.sheet

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightMoonlightBinding
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightChannel
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightSettings
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightMoonlightSheet private constructor(
    private val context: Context
) {

    fun show(
        initialSettings: MoonlightSettings,
        onApply: (MoonlightSettings) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetLightMoonlightBinding.inflate(
            LayoutInflater.from(context)
        )

        dialog.setContentView(binding.root)

        var enabled = initialSettings.enabled
        var followProgramEnd = initialSettings.followProgramEnd
        var startTime = initialSettings.startTime
        var endTime = initialSettings.endTime
        var channel = initialSettings.channel
        var intensity = initialSettings.intensityPercent.coerceIn(1, 15)

        fun renderEnabledState() {
            binding.moonlightOptionsContainer.alpha = if (enabled) {
                1f
            } else {
                0.45f
            }

            binding.moonlightOptionsContainer.isEnabled = enabled
            setChildrenEnabled(binding.moonlightOptionsContainer, enabled)
        }

        fun renderFollowProgramEnd() {
            binding.rowMoonlightStart.visibility =
                if (followProgramEnd) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

            binding.tvMoonlightStart.text =
                if (followProgramEnd) {
                    context.getString(R.string.light_moonlight_program_end)
                } else {
                    startTime.label
                }
        }

        fun renderChannel() {
            renderChannelChip(
                view = binding.channelBlue,
                selected = channel == MoonlightChannel.BLUE
            )
            renderChannelChip(
                view = binding.channelWhite,
                selected = channel == MoonlightChannel.WHITE
            )
            renderChannelChip(
                view = binding.channelBlueWhite,
                selected = channel == MoonlightChannel.BLUE_WHITE
            )
        }

        fun renderIntensity() {
            binding.tvMoonlightIntensity.text =
                context.getString(
                    R.string.light_moonlight_intensity_format,
                    intensity
                )

            binding.sliderMoonlightIntensity.value = intensity.toFloat()
        }

        binding.switchMoonlightEnabled.isChecked = enabled
        binding.switchFollowProgramEnd.isChecked = followProgramEnd
        binding.tvMoonlightEnd.text = endTime.label

        renderEnabledState()
        renderFollowProgramEnd()
        renderChannel()
        renderIntensity()

        binding.switchMoonlightEnabled.setOnCheckedChangeListener { _, isChecked ->
            enabled = isChecked
            renderEnabledState()
        }

        binding.switchFollowProgramEnd.setOnCheckedChangeListener { _, isChecked ->
            followProgramEnd = isChecked
            renderFollowProgramEnd()
        }

        binding.rowMoonlightStart.setOnClickListener {
            LightCurveTimePickerSheet
                .create(context)
                .show(
                    title = context.getString(R.string.light_moonlight_start_picker_title),
                    initialHour = startTime.hour,
                    initialMinute = startTime.minute
                ) { hour, minute ->
                    startTime = LightCurvePoint.of(hour, minute)
                    renderFollowProgramEnd()
                }
        }

        binding.rowMoonlightEnd.setOnClickListener {
            LightCurveTimePickerSheet
                .create(context)
                .show(
                    title = context.getString(R.string.light_moonlight_end_picker_title),
                    initialHour = endTime.hour,
                    initialMinute = endTime.minute
                ) { hour, minute ->
                    endTime = LightCurvePoint.of(hour, minute)
                    binding.tvMoonlightEnd.text = endTime.label
                }
        }

        binding.channelBlue.setOnClickListener {
            channel = MoonlightChannel.BLUE
            renderChannel()
        }

        binding.channelWhite.setOnClickListener {
            channel = MoonlightChannel.WHITE
            renderChannel()
        }

        binding.channelBlueWhite.setOnClickListener {
            channel = MoonlightChannel.BLUE_WHITE
            renderChannel()
        }

        binding.sliderMoonlightIntensity.addOnChangeListener { _, value, _ ->
            intensity = value.toInt()

            binding.tvMoonlightIntensity.text =
                context.getString(
                    R.string.light_moonlight_intensity_format,
                    intensity
                )
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnApply.setOnClickListener {
            onApply(
                MoonlightSettings(
                    enabled = enabled,
                    followProgramEnd = followProgramEnd,
                    startTime = startTime,
                    endTime = endTime,
                    channel = channel,
                    intensityPercent = intensity
                )
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun renderChannelChip(
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
        fun create(context: Context): LightMoonlightSheet {
            return LightMoonlightSheet(context)
        }
    }
}
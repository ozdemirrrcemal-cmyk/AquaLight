package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightManualBinding
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class DeviceLightManualFragment : Fragment(R.layout.fragment_device_light_manual) {

    private var _binding: FragmentDeviceLightManualBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightManualBinding.bind(view)

        configureSliderRanges()
        renderPreviewState()
        setupSliders()
        setupClicks()
    }

    private fun configureSliderRanges() = with(binding) {
        listOf(
            sliderMasterBrightness,
            sliderRed,
            sliderGreen,
            sliderBlue,
            sliderWhite
        ).forEach { slider ->
            slider.valueFrom = 0f
            slider.valueTo = 100f
            slider.stepSize = 1f
        }
    }

    private fun renderPreviewState() = with(binding) {
        tvManualConnectionStatus.text = "Online · Manual ready"
        tvManualPowerState.text = "LIVE"

        sliderMasterBrightness.value = 78f
        sliderRed.value = 80f
        sliderGreen.value = 84f
        sliderBlue.value = 79f
        sliderWhite.value = 65f

        updateMasterValue()
        updateChannelValues()
        updatePowerState()
        updatePreviewText()
    }

    private fun setupSliders() = with(binding) {
        bindMasterSlider(
            slider = sliderMasterBrightness
        )

        bindChannelSlider(
            slider = sliderRed,
            valueView = tvRedValue,
            summaryView = tvManualRedSummaryValue
        )

        bindChannelSlider(
            slider = sliderGreen,
            valueView = tvGreenValue,
            summaryView = tvManualGreenSummaryValue
        )

        bindChannelSlider(
            slider = sliderBlue,
            valueView = tvBlueValue,
            summaryView = tvManualBlueSummaryValue
        )

        bindChannelSlider(
            slider = sliderWhite,
            valueView = tvWhiteValue,
            summaryView = tvManualWhiteSummaryValue
        )
    }

    private fun bindMasterSlider(
        slider: Slider
    ) {
        slider.addOnChangeListener { _, _, _ ->
            updateMasterValue()
            updatePowerState()
            updatePreviewText()
        }
    }

    private fun bindChannelSlider(
        slider: Slider,
        valueView: TextView,
        summaryView: TextView
    ) {
        slider.addOnChangeListener { _, value, _ ->
            val label = formatPercent(value)

            valueView.text = label
            summaryView.text = label

            updatePreviewText()
        }
    }

    private fun setupClicks() = with(binding) {
        btnManualSync.setOnClickListener {
            showMessage("Syncing manual light data")
        }

        btnManualMore.setOnClickListener {
            showMessage("Manual options will be added")
        }

        btnAll100.setOnClickListener {
            applyAllChannels(100f)
        }

        btnAll50.setOnClickListener {
            applyAllChannels(50f)
        }

        btnAllOff.setOnClickListener {
            applyAllChannels(0f)
        }

        btnResetChannels.setOnClickListener {
            sliderMasterBrightness.value = 78f
            sliderRed.value = 80f
            sliderGreen.value = 84f
            sliderBlue.value = 79f
            sliderWhite.value = 65f

            updateMasterValue()
            updateChannelValues()
            updatePowerState()
            updatePreviewText()
        }

        btnApplyTemporary.setOnClickListener {
            showMessage("Temporary manual output command will be sent")
        }

        btnSaveAsPreset.setOnClickListener {
            showMessage("Save as preset will be added")
        }

        btnApplyToProgram.setOnClickListener {
            showMessage("Apply to program will be added")
        }
    }

    private fun applyAllChannels(
        value: Float
    ) = with(binding) {
        sliderRed.value = value
        sliderGreen.value = value
        sliderBlue.value = value
        sliderWhite.value = value

        updateChannelValues()
        updatePreviewText()
    }

    private fun updateMasterValue() = with(binding) {
        tvManualOutputValue.text = formatPercent(
            value = sliderMasterBrightness.value
        )
    }

    private fun updateChannelValues() = with(binding) {
        val red = formatPercent(sliderRed.value)
        val green = formatPercent(sliderGreen.value)
        val blue = formatPercent(sliderBlue.value)
        val white = formatPercent(sliderWhite.value)

        tvRedValue.text = red
        tvGreenValue.text = green
        tvBlueValue.text = blue
        tvWhiteValue.text = white

        tvManualRedSummaryValue.text = red
        tvManualGreenSummaryValue.text = green
        tvManualBlueSummaryValue.text = blue
        tvManualWhiteSummaryValue.text = white
    }

    private fun updatePowerState() = with(binding) {
        val master = sliderMasterBrightness.value.roundToInt()

        tvManualPowerState.text = if (master <= 0) {
            "OFF"
        } else {
            "LIVE"
        }
    }

    private fun updatePreviewText() = with(binding) {
        val master = sliderMasterBrightness.value
        val red = sliderRed.value
        val green = sliderGreen.value
        val blue = sliderBlue.value
        val white = sliderWhite.value

        val channelAverage = (red + green + blue + white) / 4f
        val effectiveAverage = channelAverage * (master / 100f)

        val description = when {
            master <= 0f || effectiveAverage <= 5f -> {
                "Estimated appearance: Lights off"
            }

            blue > red + 20f -> {
                "Estimated appearance: Cool blue display"
            }

            red > blue + 20f -> {
                "Estimated appearance: Warm evening tone"
            }

            white >= 70f && effectiveAverage >= 70f -> {
                "Estimated appearance: Bright daylight"
            }

            effectiveAverage >= 45f -> {
                "Estimated appearance: Neutral daylight"
            }

            else -> {
                "Estimated appearance: Soft low light"
            }
        }

        tvPreviewAppearance.text = description
    }

    private fun formatPercent(
        value: Float
    ): String {
        return "${value.roundToInt().coerceIn(0, 100)}%"
    }

    private fun showMessage(
        message: String
    ) {
        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"

        fun newInstance(
            deviceId: Long
        ): DeviceLightManualFragment {
            return DeviceLightManualFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )
                }
            }
        }
    }
}
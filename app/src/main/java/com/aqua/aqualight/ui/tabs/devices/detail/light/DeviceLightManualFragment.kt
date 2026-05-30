package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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
        renderDummyState()
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

    private fun renderDummyState() = with(binding) {
        tvManualSubtitle.text = "Live output control · Device ID: $deviceId"
        tvManualPowerState.text = "ON"

        sliderMasterBrightness.value = 78f
        sliderRed.value = 80f
        sliderGreen.value = 84f
        sliderBlue.value = 79f
        sliderWhite.value = 65f

        updateValue(tvMasterBrightnessValue, sliderMasterBrightness.value)
        updateValue(tvRedValue, sliderRed.value)
        updateValue(tvGreenValue, sliderGreen.value)
        updateValue(tvBlueValue, sliderBlue.value)
        updateValue(tvWhiteValue, sliderWhite.value)

        updatePowerState()
        updatePreviewText()
    }

    private fun setupSliders() = with(binding) {
        bindMasterSlider(
            slider = sliderMasterBrightness,
            valueView = tvMasterBrightnessValue
        )

        bindChannelSlider(
            slider = sliderRed,
            valueView = tvRedValue
        )

        bindChannelSlider(
            slider = sliderGreen,
            valueView = tvGreenValue
        )

        bindChannelSlider(
            slider = sliderBlue,
            valueView = tvBlueValue
        )

        bindChannelSlider(
            slider = sliderWhite,
            valueView = tvWhiteValue
        )
    }

    private fun bindMasterSlider(
        slider: Slider,
        valueView: TextView
    ) {
        slider.addOnChangeListener { _, value, _ ->
            updateValue(
                valueView = valueView,
                value = value
            )
            updatePowerState()
            updatePreviewText()
        }
    }

    private fun bindChannelSlider(
        slider: Slider,
        valueView: TextView
    ) {
        slider.addOnChangeListener { _, value, _ ->
            updateValue(
                valueView = valueView,
                value = value
            )
            updatePreviewText()
        }
    }

    private fun updateValue(
        valueView: TextView,
        value: Float
    ) {
        valueView.text = "${value.roundToInt()}%"
    }

    private fun setupClicks() = with(binding) {
        btnManualBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnMasterOff.setOnClickListener {
            sliderMasterBrightness.value = 0f
            updatePowerState()
            updatePreviewText()
        }

        btnMaster25.setOnClickListener {
            sliderMasterBrightness.value = 25f
            updatePowerState()
            updatePreviewText()
        }

        btnMaster50.setOnClickListener {
            sliderMasterBrightness.value = 50f
            updatePowerState()
            updatePreviewText()
        }

        btnMaster100.setOnClickListener {
            sliderMasterBrightness.value = 100f
            updatePowerState()
            updatePreviewText()
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
            sliderRed.value = 80f
            sliderGreen.value = 84f
            sliderBlue.value = 79f
            sliderWhite.value = 65f
            updatePreviewText()
        }

        btnApplyTemporary.setOnClickListener {
            showMessage("Temporary manual output will be sent later")
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
        updatePreviewText()
    }

    private fun updatePowerState() = with(binding) {
        val master = sliderMasterBrightness.value.roundToInt()

        tvManualPowerState.text = if (master <= 0) {
            "OFF"
        } else {
            "ON"
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
                    putLong(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }
}
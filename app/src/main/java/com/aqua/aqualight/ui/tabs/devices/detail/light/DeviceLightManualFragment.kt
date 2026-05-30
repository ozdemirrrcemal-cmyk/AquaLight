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

        configureSliders()
        renderPreviewState()
        setupSliders()
        setupClicks()
    }

    private fun configureSliders() = with(binding) {
        listOf(
            sliderMasterBrightness,
            sliderRed,
            sliderGreen,
            sliderBlue,
            sliderWhite
        ).forEach { slider ->
            slider.valueFrom = 0f
            slider.valueTo = MAX_PERCENT.toFloat()
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

        btnApplyToProgram.isEnabled = false
        btnApplyToProgram.alpha = DISABLED_ALPHA
    }

    private fun setupSliders() = with(binding) {
        bindMasterSlider(
            slider = sliderMasterBrightness
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
        valueView: TextView
    ) {
        slider.addOnChangeListener { _, value, _ ->
            valueView.text = formatPercent(
                value = value.roundToInt()
            )

            updateChannelValues()
            updatePreviewText()
        }
    }

    private fun setupClicks() = with(binding) {
        btnAll100.setOnClickListener {
            applyAllChannels(
                value = 100
            )
        }

        btnAll50.setOnClickListener {
            applyAllChannels(
                value = 50
            )
        }

        btnAllOff.setOnClickListener {
            applyAllChannels(
                value = 0
            )
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
            if (!btnApplyToProgram.isEnabled) {
                return@setOnClickListener
            }

            showMessage("Apply to program will be added")
        }
    }

    private fun applyAllChannels(
        value: Int
    ) = with(binding) {
        val safeValue = value
            .coerceIn(
                minimumValue = 0,
                maximumValue = MAX_PERCENT
            )
            .toFloat()

        sliderRed.value = safeValue
        sliderGreen.value = safeValue
        sliderBlue.value = safeValue
        sliderWhite.value = safeValue

        updateChannelValues()
        updatePreviewText()
    }

    private fun updateMasterValue() = with(binding) {
        tvManualOutputValue.text = formatPercent(
            value = sliderMasterBrightness.value.roundToInt()
        )
    }

    private fun updateChannelValues() = with(binding) {
        val red = sliderRed.value.roundToInt()
        val green = sliderGreen.value.roundToInt()
        val blue = sliderBlue.value.roundToInt()
        val white = sliderWhite.value.roundToInt()

        tvRedValue.text = formatPercent(red)
        tvGreenValue.text = formatPercent(green)
        tvBlueValue.text = formatPercent(blue)
        tvWhiteValue.text = formatPercent(white)

        tvManualRedSummaryValue.text = "R${formatPercent(red)}"
        tvManualGreenSummaryValue.text = "G${formatPercent(green)}"
        tvManualBlueSummaryValue.text = "B${formatPercent(blue)}"
        tvManualWhiteSummaryValue.text = "W${formatPercent(white)}"
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
        value: Int
    ): String {
        return "${value.coerceIn(0, MAX_PERCENT)}%"
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
        private const val MAX_PERCENT = 100
        private const val DISABLED_ALPHA = 0.45f

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
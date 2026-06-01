package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightManualBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_ID
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightArgs.ARG_DEVICE_TITLE
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class DeviceLightManualFragment : Fragment(R.layout.fragment_device_light_manual) {

    private var _binding: FragmentDeviceLightManualBinding? = null
    private val binding get() = _binding!!

    private var isProgrammaticSliderChange = false

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceTitle: String
        get() = requireArguments()
            .getString(ARG_DEVICE_TITLE)
            .orEmpty()
            .ifBlank {
                getString(R.string.light_default_device_title)
            }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightManualBinding.bind(view)

        setupHeader()
        configureSliders()
        setupSliderListeners()
        setupClicks()
    }

    private fun setupHeader() = with(binding.deviceHeader) {
        tvTitle.text = getString(R.string.light_manual_title)

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        headerActionsContainer.visibility = View.VISIBLE

        btnActionOne.visibility = View.VISIBLE
        btnActionOne.setImageResource(R.drawable.ic_light_sync_24)
        btnActionOne.contentDescription = getString(R.string.light_cd_sync)
        btnActionOne.setOnClickListener {
            // TODO: Connect manual refresh when ESP32 read layer is enabled.
        }

        btnActionTwo.visibility = View.GONE
        btnActionThree.visibility = View.GONE
    }

    private fun configureSliders() = with(binding) {
        listOf(
            sliderMasterBrightness,
            sliderRed,
            sliderGreen,
            sliderBlue,
            sliderWhite
        ).forEach { slider ->
            slider.valueFrom = MIN_PERCENT.toFloat()
            slider.valueTo = MAX_PERCENT.toFloat()
            slider.stepSize = SLIDER_STEP_SIZE
            slider.setLabelFormatter { value ->
                getString(
                    R.string.common_percent_value,
                    value.roundToInt()
                )
            }
        }
    }

    private fun setupSliderListeners() = with(binding) {
        bindSlider(sliderMasterBrightness)
        bindSlider(sliderRed)
        bindSlider(sliderGreen)
        bindSlider(sliderBlue)
        bindSlider(sliderWhite)
    }

    private fun bindSlider(
        slider: Slider
    ) {
        slider.addOnChangeListener { _, _, _ ->
            if (isProgrammaticSliderChange) {
                return@addOnChangeListener
            }

            renderCurrentSliderLabels()
        }
    }

    private fun setupClicks() = with(binding) {
        btnAll100.setOnClickListener {
            applyAllChannels(MAX_PERCENT)
        }

        btnAll50.setOnClickListener {
            applyAllChannels(HALF_PERCENT)
        }

        btnAllOff.setOnClickListener {
            applyAllChannels(MIN_PERCENT)
        }

        btnResetChannels.setOnClickListener {
            clearManualSelection()
        }

        btnApplyTemporary.setOnClickListener {
            // TODO: Send current manual values as temporary output when ESP32 command layer is enabled.
        }

        btnSaveAsPreset.setOnClickListener {
            openPresets()
        }

        btnApplyToProgram.setOnClickListener {
            // TODO: Enable when applying manual values into a saved program is implemented.
        }
    }

    private fun applyAllChannels(
        value: Int
    ) = with(binding) {
        val safeValue = value.coerceIn(
            minimumValue = MIN_PERCENT,
            maximumValue = MAX_PERCENT
        ).toFloat()

        isProgrammaticSliderChange = true

        sliderRed.value = safeValue
        sliderGreen.value = safeValue
        sliderBlue.value = safeValue
        sliderWhite.value = safeValue

        isProgrammaticSliderChange = false

        renderCurrentSliderLabels()
    }

    private fun clearManualSelection() = with(binding) {
        isProgrammaticSliderChange = true

        sliderMasterBrightness.value = MIN_PERCENT.toFloat()
        sliderRed.value = MIN_PERCENT.toFloat()
        sliderGreen.value = MIN_PERCENT.toFloat()
        sliderBlue.value = MIN_PERCENT.toFloat()
        sliderWhite.value = MIN_PERCENT.toFloat()

        isProgrammaticSliderChange = false

        tvManualPowerState.text = ""
        tvManualOutputValue.text = ""
        tvRedValue.text = ""
        tvGreenValue.text = ""
        tvBlueValue.text = ""
        tvWhiteValue.text = ""
        tvPreviewAppearance.text = ""

        // TODO: Reset preview view when LightWrgbOutputPreviewView API is finalized.
    }

    private fun renderCurrentSliderLabels() = with(binding) {
        val master = sliderMasterBrightness.value.roundToInt()
        val red = sliderRed.value.roundToInt()
        val green = sliderGreen.value.roundToInt()
        val blue = sliderBlue.value.roundToInt()
        val white = sliderWhite.value.roundToInt()

        tvManualOutputValue.text = formatPercent(master)
        tvRedValue.text = formatPercent(red)
        tvGreenValue.text = formatPercent(green)
        tvBlueValue.text = formatPercent(blue)
        tvWhiteValue.text = formatPercent(white)

        tvManualPowerState.text =
            if (master > MIN_PERCENT || red > MIN_PERCENT || green > MIN_PERCENT || blue > MIN_PERCENT || white > MIN_PERCENT) {
                getString(R.string.light_manual_state_live)
            } else {
                getString(R.string.light_manual_state_off)
            }

        // TODO: Update LightWrgbOutputPreviewView with current local slider values when its API is finalized.
        // TODO: Fill tvPreviewAppearance from real calculation or ESP32 response later.
    }

    private fun formatPercent(
        value: Int
    ): String {
        return getString(
            R.string.common_percent_value,
            value.coerceIn(
                minimumValue = MIN_PERCENT,
                maximumValue = MAX_PERCENT
            )
        )
    }

    private fun openPresets() {
        findNavController().navigate(
            R.id.deviceLightPresetsFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_DEVICE_TITLE to deviceTitle
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val MIN_PERCENT = 0
        private const val HALF_PERCENT = 50
        private const val MAX_PERCENT = 100
        private const val SLIDER_STEP_SIZE = 1f
    }
}